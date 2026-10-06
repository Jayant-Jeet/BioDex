import argparse
import gc
import hashlib
import json
import math
import os
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import open_clip
import torch
import torch.nn.functional as functional
from huggingface_hub import snapshot_download


MODEL_ID = "imageomics/bioclip-2.5-vith14"
MODEL_REVISION = "6e3d04e3d6522012c88181085c5ae666e14c45cd"
MODEL_FILENAME = "bioclip_2_5_vith14_image_fp16.onnx"
TEACHER_EMBEDDINGS_SHA256 = (
    "75626c967a00556f09bd6534d15c9c97c71ce37b0f3ae591187ba06d53377ae2"
)
ASSET_DIRECTORY = (
    Path(__file__).resolve().parents[1]
    / "app"
    / "src"
    / "main"
    / "assets"
    / "bioclip"
)
MODEL_FILES = [
    "open_clip_config.json",
    "open_clip_model.safetensors",
]


class NormalizedImageEncoder(torch.nn.Module):
    def __init__(self, visual: torch.nn.Module) -> None:
        super().__init__()
        self.visual = visual

    def forward(self, images: torch.Tensor) -> torch.Tensor:
        features = self.visual(images.to(dtype=torch.float16))
        return functional.normalize(features.float(), dim=-1)


class PortableMultiheadAttention(torch.nn.Module):
    def __init__(self, attention: torch.nn.MultiheadAttention) -> None:
        super().__init__()
        if attention.in_proj_weight is None:
            raise ValueError("BioCLIP export expects fused QKV attention.")
        self.attention = attention
        self.batch_first = attention.batch_first

    def forward(
        self,
        query: torch.Tensor,
        key: torch.Tensor,
        value: torch.Tensor,
        need_weights: bool = False,
        attn_mask: torch.Tensor | None = None,
        key_padding_mask: torch.Tensor | None = None,
        average_attn_weights: bool = True,
        is_causal: bool = False,
    ) -> tuple[torch.Tensor, None]:
        if key_padding_mask is not None or is_causal:
            raise ValueError("Unexpected mask configuration in BioCLIP vision attention.")
        if attn_mask is not None:
            raise ValueError("Unexpected attention mask in BioCLIP vision attention.")
        if query is not key or query is not value:
            raise ValueError("BioCLIP vision attention must use self-attention.")

        if self.batch_first:
            batch_size, sequence_length, embedding_size = query.shape
        else:
            sequence_length, batch_size, embedding_size = query.shape
        head_count = self.attention.num_heads
        head_size = embedding_size // head_count
        qkv = functional.linear(
            query,
            self.attention.in_proj_weight,
            self.attention.in_proj_bias,
        )
        if self.batch_first:
            qkv = qkv.reshape(batch_size, sequence_length, 3, head_count, head_size)
            q, k, v = qkv.permute(2, 0, 3, 1, 4).unbind(dim=0)
        else:
            qkv = qkv.reshape(sequence_length, batch_size, 3, head_count, head_size)
            q, k, v = qkv.permute(2, 1, 3, 0, 4).unbind(dim=0)
        weights = torch.softmax(
            torch.matmul(q, k.transpose(-2, -1)) * (head_size ** -0.5),
            dim=-1,
        )
        features = torch.matmul(weights, v)
        features = features.transpose(1, 2).reshape(
            batch_size if self.batch_first else sequence_length,
            sequence_length if self.batch_first else batch_size,
            embedding_size,
        )
        if not self.batch_first:
            features = features.transpose(0, 1)
        return functional.linear(
            features,
            self.attention.out_proj.weight,
            self.attention.out_proj.bias,
        ), None


class PortableGELU(torch.nn.Module):
    def forward(self, values: torch.Tensor) -> torch.Tensor:
        values_fp32 = values.float()
        result = 0.5 * values_fp32 * (
            1.0 + torch.erf(values_fp32 / math.sqrt(2.0))
        )
        return result.to(dtype=values.dtype)


def replace_gelu_modules(module: torch.nn.Module) -> None:
    for name, child in list(module.named_children()):
        if isinstance(child, torch.nn.GELU):
            setattr(module, name, PortableGELU())
        else:
            replace_gelu_modules(child)


def verify_gelu_conversion() -> None:
    inputs = torch.linspace(-8, 8, 4097, dtype=torch.float16)
    expected = torch.nn.GELU()(inputs)
    actual = PortableGELU()(inputs)
    if not torch.allclose(expected, actual, rtol=0.002, atol=0.002):
        raise RuntimeError("Portable GELU does not match PyTorch GELU.")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(8 * 1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def download_model(cache_directory: Path) -> Path:
    cache_directory.parent.mkdir(parents=True, exist_ok=True)
    return Path(
        snapshot_download(
            repo_id=MODEL_ID,
            revision=MODEL_REVISION,
            local_dir=cache_directory,
            allow_patterns=MODEL_FILES,
            max_workers=2,
        )
    )


def export_image_encoder(
    visual: torch.nn.Module,
    output_path: Path,
    verify: bool,
) -> None:
    encoder = NormalizedImageEncoder(visual.eval().half()).eval()
    del visual
    gc.collect()

    torch.backends.mha.set_fastpath_enabled(False)
    verify_gelu_conversion()
    replace_gelu_modules(encoder.visual)
    attention = encoder.visual.transformer.resblocks[0].attn
    attention_input_shape = (
        (1, 17, attention.embed_dim)
        if attention.batch_first
        else (17, 1, attention.embed_dim)
    )
    attention_input = torch.randn(attention_input_shape, dtype=torch.float16)
    with torch.inference_mode():
        reference = attention(
            attention_input,
            attention_input,
            attention_input,
            need_weights=False,
        )[0]
        portable = PortableMultiheadAttention(attention)(
            attention_input,
            attention_input,
            attention_input,
            need_weights=False,
        )[0]
    if not torch.allclose(reference, portable, rtol=0.01, atol=0.01):
        raise RuntimeError("Portable vision attention does not match PyTorch attention.")
    for block in encoder.visual.transformer.resblocks:
        block.attn = PortableMultiheadAttention(block.attn)

    temporary_path = output_path.with_name(output_path.stem + ".pending.onnx")
    sample = torch.rand((1, 3, 224, 224), dtype=torch.float32)
    with torch.inference_mode():
        torch.onnx.export(
            encoder,
            (sample,),
            str(temporary_path),
            input_names=["image"],
            output_names=["embedding"],
            opset_version=17,
            do_constant_folding=True,
            dynamo=False,
        )
        expected = encoder(sample).cpu().numpy()
        sample_input = sample.numpy()

    del encoder
    del sample
    gc.collect()

    onnx.checker.check_model(str(temporary_path))
    if verify:
        options = ort.SessionOptions()
        options.intra_op_num_threads = max(1, min(4, os.cpu_count() or 1))
        session = ort.InferenceSession(
            str(temporary_path),
            sess_options=options,
            providers=["CPUExecutionProvider"],
        )
        actual = session.run(["embedding"], {"image": sample_input})[0]
        if actual.shape != (1, 1024):
            raise RuntimeError(f"Unexpected ONNX output shape: {actual.shape}")
        if not np.allclose(actual, expected, rtol=0.02, atol=0.002):
            max_error = float(np.max(np.abs(actual - expected)))
            raise RuntimeError(
                f"ONNX output differs from OpenCLIP (maximum error {max_error})."
            )

    os.replace(temporary_path, output_path)


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Export Imageomics BioCLIP 2.5 ViT-H/14 as a local FP16 ONNX "
            "image encoder and regenerate matching plant text embeddings."
        )
    )
    parser.add_argument(
        "--cache-directory",
        type=Path,
        default=Path.home() / ".cache" / "biodex" / "bioclip-2.5-vith14",
    )
    parser.add_argument("--assets-directory", type=Path, default=ASSET_DIRECTORY)
    parser.add_argument(
        "--skip-onnx-verification",
        action="store_true",
        help="Skip loading the exported model into ONNX Runtime on the PC.",
    )
    args = parser.parse_args()

    assets = args.assets_directory.resolve()
    assets.mkdir(parents=True, exist_ok=True)
    labels_path = assets / "taxa_labels.json"
    if not labels_path.is_file():
        raise FileNotFoundError(f"BioDex taxonomy not found: {labels_path}")
    table_path = assets / "taxa_table.npy"
    if not table_path.is_file():
        raise FileNotFoundError(f"Teacher text embeddings not found: {table_path}")
    table_hash = sha256(table_path)
    if table_hash != TEACHER_EMBEDDINGS_SHA256:
        raise ValueError(
            "The text embedding table is not the expected BioCLIP 2.5 teacher "
            f"table (SHA-256 {table_hash})."
        )

    model_directory = download_model(args.cache_directory.resolve())
    print(f"Using pinned checkpoint from {model_directory}.", flush=True)
    model, _, _ = open_clip.create_model_and_transforms(
        f"local-dir:{model_directory}",
        device="cpu",
        precision="fp32",
    )

    labels = json.loads(labels_path.read_text(encoding="utf-8"))
    species_count = len(labels)
    model_path = assets / MODEL_FILENAME
    visual = model.visual
    del model
    gc.collect()
    print("Exporting and validating the FP16 ONNX image encoder.", flush=True)
    export_image_encoder(
        visual,
        model_path,
        verify=not args.skip_onnx_verification,
    )

    metadata = {
        "modelId": MODEL_ID,
        "revision": MODEL_REVISION,
        "license": "MIT",
        "imageEncoder": MODEL_FILENAME,
        "imageEncoderBytes": model_path.stat().st_size,
        "imageEncoderSha256": sha256(model_path),
        "textEmbeddings": "taxa_table.npy",
        "textEmbeddingsSha256": table_hash,
        "textEmbeddingsSource": (
            "BioCLIP 2.5 teacher-space table from "
            "crazedcodernate/bioclip-2.5-mobile-fastvit"
        ),
        "speciesCount": species_count,
        "embeddingDimensions": 1024,
        "precision": "FP16 image encoder; FP32 normalized output",
    }
    (assets / "model-info.json").write_text(
        json.dumps(metadata, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    main()
