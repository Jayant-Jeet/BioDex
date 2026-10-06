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
    // Default model download URL (hosted on GitHub Releases or CDN)
    const val DEFAULT_MODEL_URL = "https://github.com/Jayant-Jeet/BioDex/blob/main/releases/download/$MODEL_FILE_NAME"
    const val EXPECTED_MODEL_SIZE_BYTES = 1_264_488_704L // ~1.2 GB

    fun getModelFile(context: Context): File {
        val modelDir = File(context.applicationContext.filesDir, "bioclip")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        return File(modelDir, MODEL_FILE_NAME)
    }

    fun isModelDownloaded(context: Context): Boolean {
        try {
            context.assets.openFd("bioclip/$MODEL_FILE_NAME").use { return true }
        } catch (_: Exception) {}

        val modelFile = getModelFile(context)
        return modelFile.exists() && modelFile.isFile && modelFile.length() > 0
    }

    @Throws(IOException::class)
    fun downloadModel(
        context: Context,
        modelUrl: String = DEFAULT_MODEL_URL,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        val modelFile = getModelFile(context)
        val tempFile = File(modelFile.parentFile, "$MODEL_FILE_NAME.tmp")

        var connection: HttpURLConnection? = null
        try {
            val url = URL(modelUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Server returned HTTP response code $responseCode: ${connection.responseMessage}")
            }

            val contentLength = connection.contentLengthLong.let {
                if (it > 0) it else EXPECTED_MODEL_SIZE_BYTES
            }

            BufferedInputStream(connection.inputStream, 8192).use { input ->
                BufferedOutputStream(FileOutputStream(tempFile), 8192).use { output ->
                    val buffer = ByteArray(32 * 1024)
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

            if (tempFile.length() <= 0) {
                throw IOException("Downloaded model file is empty.")
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
