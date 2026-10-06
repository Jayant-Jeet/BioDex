# BioCLIP 2.5 ViT-H/14 attribution

BioDex uses the official **BioCLIP 2.5 Huge (ViT-H/14)** weights from the Imageomics Institute, distributed under the MIT license:

- Source model: https://huggingface.co/imageomics/bioclip-2.5-vith14
- Source revision: `6e3d04e3d6522012c88181085c5ae666e14c45cd`
- Source checkpoint: `open_clip_model.safetensors` (3,944,517,804 bytes)
- Android model: `bioclip_2_5_vith14_image_fp16.onnx` (1,264,483,431 bytes; SHA-256 `b97083f340e214810080a16f53ee89c2b2fdac556629babebbc84e141b42c194`), exported from the official image encoder using `scripts/export-bioclip-25-onnx.py`
- Model configuration and tokenizer are downloaded from the same pinned revision during export.
- `taxa_labels.json` retains the 4,271-plant taxonomy from Nate Hamilton's [BioCLIP 2.5 Mobile repository](https://huggingface.co/crazedcodernate/bioclip-2.5-mobile-fastvit), MIT licensed. The student image encoder and its embedding table are not used.
- `taxa_table.npy` is the published normalized teacher-space text embedding table from the same source, verified against SHA-256 `75626c967a00556f09bd6534d15c9c97c71ce37b0f3ae591187ba06d53377ae2`.
- `model-info.json` records the generated model and embedding hashes and sizes.

The Android ONNX artifact contains the FP16 image encoder and FP32 normalized image embedding output. It runs against the precomputed official-model text embeddings. The classifier is still limited to the listed plants; animal and fungi labels are not included in this app's taxonomy.

The model card reports zero-shot benchmark results and notes higher inference resource requirements than BioCLIP 2. It does not guarantee correct identification for a particular phone photo, geography, or species. Similarity is not calibrated confidence. Never use the app to make food, toxicity, medicinal, or other safety decisions.

The model is MIT licensed. Training images are not bundled and remain subject to their respective licenses. Refer to the source model card for training data, benchmark methodology, limitations, and citation details.


## species_notes.json
Common names and short descriptions generated with Google Gemma 4 (gemma4:e4b, run locally through Ollama) by tools/generate_notes.py. AI-generated; may contain errors.

- DM Sans and DM Serif Display fonts: SIL Open Font License 1.1.
