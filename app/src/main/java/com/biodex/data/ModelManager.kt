package com.biodex.data

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay

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
    suspend fun downloadModel(
        context: Context,
        modelUrl: String = DEFAULT_MODEL_URL,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        val appContext = context.applicationContext
        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

        if (downloadManager != null) {
            try {
                downloadWithDownloadManager(appContext, downloadManager, modelUrl, onProgress)
                return
            } catch (_: Exception) {
                // Fallback to HttpURLConnection if DownloadManager fails
            }
        }

        downloadWithHttpURLConnection(appContext, modelUrl, onProgress)
    }

    private suspend fun downloadWithDownloadManager(
        context: Context,
        downloadManager: DownloadManager,
        modelUrl: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        val finalModelFile = getModelFile(context)
        val tempFileName = "$MODEL_FILE_NAME.tmp"

        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        val downloadedTempFile = File(destDir, tempFileName)
        if (downloadedTempFile.exists()) {
            downloadedTempFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(modelUrl))
            .setTitle("BioCLIP Field Guide Model")
            .setDescription("Downloading AI model for offline identification")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(context, null, tempFileName)

        val downloadId = downloadManager.enqueue(request)

        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor: Cursor? = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalSizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                val downloadedBytes = if (bytesDownloadedIndex >= 0) cursor.getLong(bytesDownloadedIndex) else 0L
                val totalBytes = if (totalSizeIndex >= 0) {
                    val size = cursor.getLong(totalSizeIndex)
                    if (size > 0) size else EXPECTED_MODEL_SIZE_BYTES
                } else EXPECTED_MODEL_SIZE_BYTES

                if (downloadedBytes > 0) {
                    onProgress(downloadedBytes, totalBytes)
                }

                if (statusIndex >= 0) {
                    when (cursor.getInt(statusIndex)) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            cursor.close()
                            break
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                            cursor.close()
                            throw IOException("System DownloadManager failed with error code $reason.")
                        }
                    }
                }
                cursor.close()
            }
            delay(300)
        }

        if (downloadedTempFile.exists()) {
            if (finalModelFile.exists()) {
                finalModelFile.delete()
            }
            BufferedInputStream(downloadedTempFile.inputStream()).use { input ->
                BufferedOutputStream(FileOutputStream(finalModelFile)).use { output ->
                    input.copyTo(output)
                }
            }
            downloadedTempFile.delete()
        }

        if (finalModelFile.length() != EXPECTED_MODEL_SIZE_BYTES) {
            finalModelFile.delete()
            throw IOException("Downloaded model file size (${finalModelFile.length()} bytes) does not match expected size ($EXPECTED_MODEL_SIZE_BYTES bytes).")
        }
    }

    private fun downloadWithHttpURLConnection(
        context: Context,
        modelUrl: String,
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
