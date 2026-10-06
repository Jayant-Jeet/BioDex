package com.biodex.data

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.biodex.util.PhotoBitmap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

data class Identification(
    val species: Species,
    val similarity: Float,
    val quality: Int,
)

/** Thrown when the best label is too weak to trust, e.g. for animals, objects or unlisted taxa. */
class NoConfidentMatchException(val similarity: Float) : Exception(
    "No confident plant match (best cosine similarity $similarity).",
)

class BioClipClassifier(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val environment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var embeddings: FloatArray? = null

    fun identify(photoPath: String, species: List<Species>): Identification {
        require(species.size == SPECIES_COUNT) {
            "BioCLIP requires $SPECIES_COUNT taxonomy labels; found ${species.size}."
        }
        val modelSession = getSession()
        val table = getEmbeddings()
        val image = PhotoBitmap.decode(photoPath)
        try {
            val quality = estimatePhotoQuality(image)
            val inputBuffer = createInput(image)
            val tensor = OnnxTensor.createTensor(
                environment,
                inputBuffer,
                longArrayOf(1L, CHANNELS.toLong(), IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong()),
            )
            try {
                modelSession.run(mapOf(INPUT_NAME to tensor)).use { result ->
                    val outputs = result[0].value as? Array<*>
                        ?: throw IOException("BioCLIP returned an unexpected output tensor.")
                    val imageEmbedding = outputs.firstOrNull() as? FloatArray
                        ?: throw IOException("BioCLIP returned an empty output tensor.")
                    if (imageEmbedding.size != EMBEDDING_DIMENSIONS) {
                        throw IOException(
                            "BioCLIP returned ${imageEmbedding.size} embedding values; " +
                                "expected $EMBEDDING_DIMENSIONS.",
                        )
                    }
                    val bestIndex = findBestMatch(imageEmbedding, table)
                    if (bestIndex !in species.indices) {
                        throw IOException("BioCLIP returned an invalid species index: $bestIndex.")
                    }
                    val similarity = cosineSimilarity(imageEmbedding, table, bestIndex)
                    if (similarity < MIN_MATCH_SIMILARITY) {
                        throw NoConfidentMatchException(similarity)
                    }
                    return Identification(
                        species = species[bestIndex],
                        similarity = similarity,
                        quality = quality,
                    )
                }
            } finally {
                tensor.close()
            }
        } finally {
            image.recycle()
        }
    }

    @Synchronized
    override fun close() {
        session?.close()
        session = null
        embeddings = null
    }

    @Synchronized
    private fun getSession(): OrtSession {
        session?.let { return it }
        val modelFile = getModelFile()
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(NUM_INTRA_OP_THREADS)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            return environment.createSession(modelFile.absolutePath, options).also { session = it }
        } finally {
            options.close()
        }
    }

    @Synchronized
    private fun getEmbeddings(): FloatArray {
        embeddings?.let { return it }
        return readNumpyTable().also { embeddings = it }
    }

    private fun getModelFile(): File {
        val modelDirectory = File(appContext.filesDir, "bioclip")
        if (!modelDirectory.exists()) modelDirectory.mkdirs()
        val modelFile = File(modelDirectory, MODEL_FILE_NAME)

        if (modelFile.exists() && modelFile.isFile) {
            if (modelFile.length() == ModelManager.EXPECTED_MODEL_SIZE_BYTES) {
                return modelFile
            } else {
                modelFile.delete()
            }
        }

        try {
            appContext.assets.openFd("bioclip/$MODEL_FILE_NAME").use { asset ->
                val expectedSize = asset.length
                if (expectedSize == ModelManager.EXPECTED_MODEL_SIZE_BYTES) {
                    asset.createInputStream().use { input ->
                        BufferedOutputStream(FileOutputStream(modelFile)).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (modelFile.length() == ModelManager.EXPECTED_MODEL_SIZE_BYTES) {
                        return modelFile
                    }
                }
            }
        } catch (_: Exception) {}

        throw IllegalStateException(
            "BioCLIP model file is missing or invalid. Please download the field guide model on the splash screen."
        )
    }

    private fun readNumpyTable(): FloatArray {
        appContext.assets.open("bioclip/taxa_table.npy").use { asset ->
            DataInputStream(BufferedInputStream(asset)).use { input ->
                val magic = ByteArray(NPY_MAGIC.size)
                input.readFully(magic)
                if (!magic.contentEquals(NPY_MAGIC)) {
                    throw IOException("BioCLIP species table is not a NumPy array.")
                }

                val majorVersion = input.readUnsignedByte()
                input.readUnsignedByte()
                val headerLength = when (majorVersion) {
                    1 -> readUnsignedShortLittleEndian(input)
                    2, 3 -> readUnsignedIntLittleEndian(input)
                    else -> throw IOException("Unsupported NumPy file version: $majorVersion.")
                }
                if (headerLength !in 1..MAX_NPY_HEADER_BYTES) {
                    throw IOException("BioCLIP species table has an invalid NumPy header.")
                }
                val headerBytes = ByteArray(headerLength)
                input.readFully(headerBytes)
                val header = String(headerBytes, Charsets.US_ASCII)
                if (!header.contains(FLOAT32_DTYPE) || !header.contains(FLOAT32_SHAPE)) {
                    throw IOException("BioCLIP species table must be a row-major 4271 x 1024 float32 array.")
                }

                val values = FloatArray(SPECIES_COUNT * EMBEDDING_DIMENSIONS)
                val chunk = ByteArray(NPY_READ_CHUNK_BYTES)
                var valuesRead = 0
                while (valuesRead < values.size) {
                    val bytesToRead = minOf(chunk.size, (values.size - valuesRead) * Float.SIZE_BYTES)
                    input.readFully(chunk, 0, bytesToRead)
                    ByteBuffer.wrap(chunk, 0, bytesToRead)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asFloatBuffer()
                        .get(values, valuesRead, bytesToRead / Float.SIZE_BYTES)
                    valuesRead += bytesToRead / Float.SIZE_BYTES
                }
                if (input.read() != -1) {
                    throw IOException("BioCLIP species table contains unexpected trailing data.")
                }
                return values
            }
        }
    }

    private fun findBestMatch(imageEmbedding: FloatArray, table: FloatArray): Int {
        var bestIndex = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (speciesIndex in 0 until SPECIES_COUNT) {
            val offset = speciesIndex * EMBEDDING_DIMENSIONS
            var score = 0f
            for (dimension in 0 until EMBEDDING_DIMENSIONS) {
                score += imageEmbedding[dimension] * table[offset + dimension]
            }
            if (score > bestScore) {
                bestScore = score
                bestIndex = speciesIndex
            }
        }
        return bestIndex
    }

    private fun cosineSimilarity(
        imageEmbedding: FloatArray,
        table: FloatArray,
        speciesIndex: Int,
    ): Float {
        val offset = speciesIndex * EMBEDDING_DIMENSIONS
        var score = 0f
        for (dimension in 0 until EMBEDDING_DIMENSIONS) {
            score += imageEmbedding[dimension] * table[offset + dimension]
        }
        return score.coerceIn(-1f, 1f)
    }

    private fun createInput(image: Bitmap): FloatBuffer {
        val pixels = resizeCenterCropBicubic(image)
        val buffer = ByteBuffer.allocateDirect(pixels.size * CHANNELS * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        val channelSize = pixels.size
        for (index in pixels.indices) {
            val pixel = pixels[index]
            buffer.put(index, normalize(BitmapChannels.red(pixel), IMAGE_MEAN_RED, IMAGE_STD_RED))
            buffer.put(
                channelSize + index,
                normalize(BitmapChannels.green(pixel), IMAGE_MEAN_GREEN, IMAGE_STD_GREEN),
            )
            buffer.put(
                2 * channelSize + index,
                normalize(BitmapChannels.blue(pixel), IMAGE_MEAN_BLUE, IMAGE_STD_BLUE),
            )
        }
        buffer.position(0)
        return buffer
    }

    private fun resizeCenterCropBicubic(image: Bitmap): IntArray {
        val scale = maxOf(
            IMAGE_SIZE.toFloat() / image.width,
            IMAGE_SIZE.toFloat() / image.height,
        )
        val resizedWidth = (image.width * scale).roundToInt()
        val resizedHeight = (image.height * scale).roundToInt()
        val cropLeft = (resizedWidth - IMAGE_SIZE) / 2
        val cropTop = (resizedHeight - IMAGE_SIZE) / 2
        val sourcePixels = IntArray(image.width * image.height)
        image.getPixels(sourcePixels, 0, image.width, 0, 0, image.width, image.height)
        val resized = IntArray(IMAGE_SIZE * IMAGE_SIZE)

        for (y in 0 until IMAGE_SIZE) {
            val sourceY = (cropTop + y + 0.5f) * image.height / resizedHeight - 0.5f
            val baseY = floor(sourceY).toInt()
            for (x in 0 until IMAGE_SIZE) {
                val sourceX = (cropLeft + x + 0.5f) * image.width / resizedWidth - 0.5f
                val baseX = floor(sourceX).toInt()
                var red = 0f
                var green = 0f
                var blue = 0f

                for (sampleY in baseY - 1..baseY + 2) {
                    val weightY = cubicWeight(sampleY - sourceY)
                    val pixelY = sampleY.coerceIn(0, image.height - 1)
                    for (sampleX in baseX - 1..baseX + 2) {
                        val weight = weightY * cubicWeight(sampleX - sourceX)
                        val pixelX = sampleX.coerceIn(0, image.width - 1)
                        val pixel = sourcePixels[pixelY * image.width + pixelX]
                        red += BitmapChannels.red(pixel) * weight
                        green += BitmapChannels.green(pixel) * weight
                        blue += BitmapChannels.blue(pixel) * weight
                    }
                }

                resized[y * IMAGE_SIZE + x] =
                    (red.roundToInt().coerceIn(0, 255) shl 16) or
                    (green.roundToInt().coerceIn(0, 255) shl 8) or
                    blue.roundToInt().coerceIn(0, 255)
            }
        }
        return resized
    }

    private fun cubicWeight(distance: Float): Float {
        val value = abs(distance)
        return when {
            value < 1f -> 1.5f * value * value * value - 2.5f * value * value + 1f
            value < 2f ->
                -0.5f * value * value * value +
                    2.5f * value * value -
                    4f * value +
                    2f
            else -> 0f
        }
    }

    private fun normalize(value: Float, mean: Float, standardDeviation: Float): Float =
        (value / PIXEL_SCALE - mean) / standardDeviation

    private fun estimatePhotoQuality(image: Bitmap): Int {
        val sample = Bitmap.createScaledBitmap(image, QUALITY_SAMPLE_SIZE, QUALITY_SAMPLE_SIZE, true)
        try {
            val pixels = IntArray(QUALITY_SAMPLE_SIZE * QUALITY_SAMPLE_SIZE)
            sample.getPixels(pixels, 0, QUALITY_SAMPLE_SIZE, 0, 0, QUALITY_SAMPLE_SIZE, QUALITY_SAMPLE_SIZE)
            var luminanceSum = 0f
            var horizontalContrast = 0f
            var verticalContrast = 0f
            for (y in 0 until QUALITY_SAMPLE_SIZE) {
                for (x in 0 until QUALITY_SAMPLE_SIZE) {
                    val index = y * QUALITY_SAMPLE_SIZE + x
                    val luminance = luminance(pixels[index])
                    luminanceSum += luminance
                    if (x + 1 < QUALITY_SAMPLE_SIZE) {
                        horizontalContrast += abs(luminance - luminance(pixels[index + 1]))
                    }
                    if (y + 1 < QUALITY_SAMPLE_SIZE) {
                        verticalContrast += abs(
                            luminance - luminance(pixels[index + QUALITY_SAMPLE_SIZE]),
                        )
                    }
                }
            }
            val sampleCount = pixels.size.toFloat()
            val meanLuminance = luminanceSum / sampleCount
            val exposureScore = (100f - abs(meanLuminance - 128f) * (100f / 128f)).coerceIn(0f, 100f)
            val edgeCount = QUALITY_SAMPLE_SIZE * (QUALITY_SAMPLE_SIZE - 1)
            val detailScore = (
                (horizontalContrast + verticalContrast) / edgeCount / 32f * 100f
                ).coerceIn(0f, 100f)
            return (exposureScore * 0.65f + detailScore * 0.35f)
                .roundToInt()
                .coerceIn(10, 100)
        } finally {
            if (sample !== image) sample.recycle()
        }
    }

    private fun luminance(pixel: Int): Float =
        0.2126f * BitmapChannels.red(pixel) +
            0.7152f * BitmapChannels.green(pixel) +
            0.0722f * BitmapChannels.blue(pixel)

    private fun readUnsignedShortLittleEndian(input: DataInputStream): Int {
        val low = input.readUnsignedByte()
        val high = input.readUnsignedByte()
        return low or (high shl 8)
    }

    private fun readUnsignedIntLittleEndian(input: DataInputStream): Int {
        val low = readUnsignedShortLittleEndian(input)
        val high = readUnsignedShortLittleEndian(input)
        return low or (high shl 16)
    }

    private object BitmapChannels {
        fun red(pixel: Int) = ((pixel shr 16) and 0xff).toFloat()
        fun green(pixel: Int) = ((pixel shr 8) and 0xff).toFloat()
        fun blue(pixel: Int) = (pixel and 0xff).toFloat()
    }

    private companion object {
        const val MODEL_FILE_NAME = "bioclip_2_5_vith14_image_fp16.onnx"
        const val INPUT_NAME = "image"
        const val CHANNELS = 3
        const val IMAGE_SIZE = 224
        const val EMBEDDING_DIMENSIONS = 1024
        const val SPECIES_COUNT = 4271
        const val NUM_INTRA_OP_THREADS = 4
        const val PIXEL_SCALE = 255f
        const val IMAGE_MEAN_RED = 0.48145466f
        const val IMAGE_MEAN_GREEN = 0.4578275f
        const val IMAGE_MEAN_BLUE = 0.40821073f
        const val IMAGE_STD_RED = 0.26862954f
        const val IMAGE_STD_GREEN = 0.26130258f
        const val IMAGE_STD_BLUE = 0.27577711f
        // Calibrated with the official encoder: real plants scored 0.59-0.62, animals/objects 0.30-0.39.
        const val MIN_MATCH_SIMILARITY = 0.45f
        const val QUALITY_SAMPLE_SIZE = 64
        const val NPY_READ_CHUNK_BYTES = 64 * 1024
        const val MAX_NPY_HEADER_BYTES = 64 * 1024
        const val FLOAT32_DTYPE = "'descr': '<f4'"
        const val FLOAT32_SHAPE = "'shape': (4271, 1024)"
        val NPY_MAGIC = byteArrayOf(0x93.toByte(), 0x4e, 0x55, 0x4d, 0x50, 0x59)
    }
}
