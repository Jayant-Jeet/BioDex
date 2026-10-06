package com.biodex.data

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object ModelManager {
    const val MODEL_FILE_NAME = "bioclip_2_5_vith14_image_fp16.onnx"
    // Raw download URL (must point directly to binary file, not GitHub HTML preview page)
    const val DEFAULT_MODEL_URL = "https://github.com/Jayant-Jeet/BioDex/raw/main/releases/download/$MODEL_FILE_NAME"
    const val EXPECTED_MODEL_SIZE_BYTES = 1_264_483_431L // Exact ONNX model size in bytes

    fun getModelFile(context: Context): File {
        val modelDir = File(context.applicationContext.filesDir, "bioclip")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        return File(modelDir, MODEL_FILE_NAME)
    }

    fun isModelDownloaded(context: Context): Boolean {
        // First check if full model is bundled in APK assets (e.g. local debug build)
        try {
            context.assets.openFd("bioclip/$MODEL_FILE_NAME").use { asset ->
                if (asset.length == EXPECTED_MODEL_SIZE_BYTES) return true
            }
        } catch (_: Exception) {}

        // Check if full model exists in local downloaded storage
        val modelFile = getModelFile(context)
        if (modelFile.exists() && modelFile.isFile) {
            if (modelFile.length() == EXPECTED_MODEL_SIZE_BYTES) {
                return true
            } else {
                // Delete invalid or truncated file
                modelFile.delete()
            }
        }
        return false
    }

    @Throws(IOException::class)
    fun downloadModel(
        context: Context,
        modelUrl: String = DEFAULT_MODEL_URL,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        val modelFile = getModelFile(context)
        val tempFile = File(modelFile.parentFile, "$MODEL_FILE_NAME.tmp")

        var currentUrl = modelUrl
        var connection: HttpURLConnection? = null
        var redirectCount = 0

        try {
            while (redirectCount < 10) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("HTTP redirect missing Location header.")
                    currentUrl = location
                    connection.disconnect()
                    redirectCount++
                    continue
                }

                if (status !in 200..299) {
                    throw IOException("Server returned HTTP response code $status: ${connection.responseMessage}")
                }
                break
            }

            val conn = connection ?: throw IOException("Could not establish connection to model URL.")
            val contentLength = conn.contentLengthLong.let {
                if (it > 0) it else EXPECTED_MODEL_SIZE_BYTES
            }

            BufferedInputStream(conn.inputStream, 64 * 1024).use { input ->
                BufferedOutputStream(FileOutputStream(tempFile), 64 * 1024).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloadedBytes = 0L
                    var bytesRead: Int
                    var lastReportedTime = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastReportedTime >= 100 || downloadedBytes == contentLength) {
                            lastReportedTime = currentTime
                            onProgress(downloadedBytes, contentLength)
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.length() != EXPECTED_MODEL_SIZE_BYTES && tempFile.length() != contentLength) {
                throw IOException("Downloaded file size (${tempFile.length()} bytes) does not match expected size ($EXPECTED_MODEL_SIZE_BYTES bytes).")
            }

            if (modelFile.exists()) {
                modelFile.delete()
            }

            if (!tempFile.renameTo(modelFile)) {
                throw IOException("Could not save the downloaded model file to destination.")
            }
        } finally {
            connection?.disconnect()
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
