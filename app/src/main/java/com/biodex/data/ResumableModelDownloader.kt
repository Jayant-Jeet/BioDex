package com.biodex.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.ThreadLocalRandom

data class ModelTransferProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val etaMillis: Long?,
)

class ModelHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message) {
    val isTransient: Boolean
        get() = statusCode == 408 || statusCode == 429 || statusCode in 500..599
}

class ModelIntegrityException(message: String) : IOException(message)
class ModelDownloadPausedException(message: String) : IOException(message)

class ResumableModelDownloader(
    private val partFile: File,
    private val destinationFile: File,
    private val expectedBytes: Long,
    private val expectedSha256: String,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val log: (String) -> Unit = {},
) {
    private val metadataFile = File(partFile.parentFile, "${partFile.name}.properties")

    @Throws(IOException::class)
    fun download(
        modelUrl: String,
        onProgress: (ModelTransferProgress) -> Unit,
        isCancelled: () -> Boolean = { false },
        onVerifying: () -> Unit = {},
        pauseForConsent: () -> Boolean = { false },
    ): File {
        var attempt = 0
        while (true) {
            try {
                return downloadOnce(modelUrl, onProgress, isCancelled, onVerifying, pauseForConsent)
            } catch (exception: IOException) {
                val retryable = when (exception) {
                    is ModelHttpException -> exception.isTransient
                    is ModelIntegrityException, is ModelDownloadPausedException -> false
                    else -> true
                }
                if (!retryable || attempt >= MAX_TRANSIENT_RETRIES) throw exception
                val jitterWindow = INITIAL_JITTER_MILLIS * (1L shl attempt)
                val jitterMillis = ThreadLocalRandom.current().nextLong(jitterWindow + 1L)
                log("Transient transfer failure; retry ${attempt + 1}/$MAX_TRANSIENT_RETRIES in ${jitterMillis}ms: ${exception.message}")
                try {
                    Thread.sleep(jitterMillis)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Model download retry was interrupted.", interrupted)
                }
                attempt++
            }
        }
    }

    @Throws(IOException::class)
    private fun downloadOnce(
        modelUrl: String,
        onProgress: (ModelTransferProgress) -> Unit,
        isCancelled: () -> Boolean,
        onVerifying: () -> Unit,
        pauseForConsent: () -> Boolean,
    ): File {
        check(expectedBytes > 0) { "Expected model size must be positive." }
        partFile.parentFile?.let { directory ->
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Could not create the model download directory.")
            }
        }

        var connection: HttpURLConnection? = null
        try {
            while (true) {
                checkNotCancelled(isCancelled)
                checkMobileConsent(pauseForConsent)
                var offset = partFile.takeIf { it.isFile }?.length() ?: 0L
                val previous = readMetadata()
                if (offset > expectedBytes) {
                    log("Discarding partial model larger than the expected size.")
                    resetPartial()
                    offset = 0L
                } else if (offset > 0L && previous.validator == null) {
                    log("No ETag or Last-Modified validator is available; restarting the partial download safely.")
                    resetPartial()
                    offset = 0L
                }

                connection = connectionFactory(URL(modelUrl)).apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept-Encoding", "identity")
                    if (offset > 0L) {
                        setRequestProperty("Range", "bytes=$offset-")
                        previous.etag?.let { setRequestProperty("If-Range", it) }
                            ?: previous.lastModified?.let { setRequestProperty("If-Range", it) }
                    }
                }

                val responseCode = connection.responseCode
                if (responseCode == HTTP_RANGE_NOT_SATISFIABLE) {
                    connection.disconnect()
                    connection = null
                    if (offset == expectedBytes && partFile.length() == expectedBytes) {
                        onVerifying()
                        verifyAndInstall()
                        return destinationFile
                    }
                    if (offset == 0L) {
                        throw ModelHttpException(responseCode, "Server rejected the model byte range.")
                    }
                    log("Server rejected the saved byte range; restarting the model download.")
                    resetPartial()
                    continue
                }

                if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    val rangeStart = CONTENT_RANGE.find(connection.getHeaderField("Content-Range").orEmpty())
                        ?.groupValues?.getOrNull(1)?.toLongOrNull()
                    val responseEtag = connection.getHeaderField("ETag")?.takeUnless {
                        it.startsWith("W/", ignoreCase = true)
                    }
                    val responseLastModified = connection.getHeaderField("Last-Modified")
                    val validatorChanged =
                        previous.etag != null && responseEtag != null && previous.etag != responseEtag ||
                            previous.etag != null && responseEtag == null ||
                            previous.etag == null && previous.lastModified != null &&
                            (responseLastModified == null || previous.lastModified != responseLastModified)

                    if (offset == 0L || rangeStart != offset || validatorChanged) {
                        if (offset == 0L) {
                            throw ModelHttpException(responseCode, "Server returned an invalid model byte range.")
                        }
                        log("The server returned an invalid or changed byte range; restarting from byte zero.")
                        connection.disconnect()
                        connection = null
                        resetPartial()
                        continue
                    }
                    writeMetadata(connection, connection.getHeaderField("Content-Range")?.let(::rangeTotal))
                    break
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    if (offset > 0L) {
                        log("Server does not support a compatible Range response or its validator changed; restarting from byte zero.")
                        partFile.delete()
                    }
                    offset = 0L
                    val responseSize = connection.contentLengthLong.takeIf { it > 0L }
                    if (responseSize != null && responseSize != expectedBytes) {
                        throw IOException("Model server reported $responseSize bytes; expected $expectedBytes.")
                    }
                    writeMetadata(connection, responseSize)
                    break
                }

                val message = runCatching { connection.responseMessage }.getOrNull()
                throw ModelHttpException(
                    responseCode,
                    "Model server returned HTTP $responseCode${message?.let { ": $it" }.orEmpty()}.",
                )
            }

            val activeConnection = connection
            val responseCode = activeConnection.responseCode
            val append = responseCode == HttpURLConnection.HTTP_PARTIAL && partFile.length() > 0L
            val total = readMetadata().totalBytes ?: expectedBytes
            val startedAt = System.nanoTime()
            var lastSampleAt = startedAt
            var lastSampleBytes = partFile.length()
            var lastReportAt = 0L
            var downloaded = partFile.length()
            activeConnection.inputStream.use { input ->
                FileOutputStream(partFile, append).buffered(BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        checkNotCancelled(isCancelled)
                        checkMobileConsent(pauseForConsent)
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (downloaded > expectedBytes) {
                            throw IOException("The downloaded model exceeds the expected size.")
                        }
                        val now = System.nanoTime()
                        if (now - lastReportAt >= REPORT_INTERVAL_NANOS || downloaded == total) {
                            val elapsed = (now - lastSampleAt).coerceAtLeast(1L)
                            val speed = ((downloaded - lastSampleBytes) * NANOS_PER_SECOND / elapsed)
                                .coerceAtLeast(0L)
                            val remaining = (total - downloaded).coerceAtLeast(0L)
                            onProgress(
                                ModelTransferProgress(
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    bytesPerSecond = speed,
                                    etaMillis = if (speed > 0L) remaining * 1000L / speed else null,
                                ),
                            )
                            lastReportAt = now
                            lastSampleAt = now
                            lastSampleBytes = downloaded
                        }
                    }
                    output.flush()
                }
            }
            if (partFile.length() != expectedBytes) {
                throw IOException("Downloaded ${partFile.length()} of $expectedBytes model bytes; the partial file is retained for resume.")
            }
            onVerifying()
            verifyAndInstall()
            return destinationFile
        } finally {
            connection?.disconnect()
        }
    }

    private fun verifyAndInstall() {
        if (partFile.length() != expectedBytes) {
            throw IOException("Downloaded model size does not match the expected size.")
        }
        val actualSha256 = sha256(partFile)
        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
            resetPartial()
            throw ModelIntegrityException("Downloaded model failed SHA-256 verification; the partial file was discarded.")
        }
        destinationFile.parentFile?.let { directory ->
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Could not create the model destination directory.")
            }
        }
        try {
            Files.move(
                partFile.toPath(),
                destinationFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(partFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        metadataFile.delete()
    }

    private fun writeMetadata(connection: HttpURLConnection, totalBytes: Long?) {
        val properties = Properties().apply {
            connection.getHeaderField("ETag")
                ?.takeUnless { it.startsWith("W/", ignoreCase = true) }
                ?.let { setProperty("etag", it) }
            connection.getHeaderField("Last-Modified")?.let { setProperty("lastModified", it) }
            totalBytes?.takeIf { it > 0L }?.let { setProperty("totalBytes", it.toString()) }
        }
        val temporaryFile = File(metadataFile.parentFile, "${metadataFile.name}.tmp")
        FileOutputStream(temporaryFile).use { properties.store(it, null) }
        try {
            Files.move(
                temporaryFile.toPath(),
                metadataFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporaryFile.toPath(), metadataFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (exception: IOException) {
            temporaryFile.delete()
            throw IOException("Could not persist model resume metadata.", exception)
        }
    }

    private fun readMetadata(): ResumeMetadata {
        if (!metadataFile.isFile) return ResumeMetadata()
        val properties = runCatching {
            Properties().apply { metadataFile.inputStream().use(::load) }
        }.getOrElse {
            log("Could not read saved model validators; restarting safely.")
            resetPartial()
            return ResumeMetadata()
        }
        return ResumeMetadata(
            etag = properties.getProperty("etag"),
            lastModified = properties.getProperty("lastModified"),
            totalBytes = properties.getProperty("totalBytes")?.toLongOrNull(),
        )
    }

    private fun resetPartial() {
        if (partFile.exists() && !partFile.delete()) {
            throw IOException("Could not discard an invalid partial model.")
        }
        metadataFile.delete()
        File(metadataFile.parentFile, "${metadataFile.name}.tmp").delete()
    }

    private fun checkNotCancelled(isCancelled: () -> Boolean) {
        if (Thread.currentThread().isInterrupted || isCancelled()) {
            throw java.util.concurrent.CancellationException("Model download was stopped.")
        }
    }

    private fun checkMobileConsent(pauseForConsent: () -> Boolean) {
        if (pauseForConsent()) {
            throw ModelDownloadPausedException("Mobile data confirmation is required to continue the model download.")
        }
    }

    private data class ResumeMetadata(
        val etag: String? = null,
        val lastModified: String? = null,
        val totalBytes: Long? = null,
    ) {
        val validator: String? get() = etag ?: lastModified
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val REPORT_INTERVAL_NANOS = 500_000_000L
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val MAX_TRANSIENT_RETRIES = 2
        private const val INITIAL_JITTER_MILLIS = 250L
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private val CONTENT_RANGE = Regex("^bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)$", RegexOption.IGNORE_CASE)

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(BUFFER_SIZE).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun rangeTotal(contentRange: String): Long? =
            CONTENT_RANGE.find(contentRange)?.groupValues?.getOrNull(3)
                ?.takeIf { it != "*" }?.toLongOrNull()
    }
}
