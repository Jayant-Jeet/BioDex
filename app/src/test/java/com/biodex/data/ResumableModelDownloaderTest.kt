package com.biodex.data

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumableModelDownloaderTest {
    private val temporaryDirectories = mutableListOf<java.io.File>()

    @After
    fun cleanUp() {
        temporaryDirectories.forEach { it.deleteRecursively() }
    }

    @Test
    fun resumesFromSavedRangeAndVerifiesChecksum() {
        val payload = "plantdata!".toByteArray(StandardCharsets.UTF_8)
        val directory = temporaryDirectory()
        val part = FileIn(directory, "model.part")
        part.writeBytes(payload.copyOfRange(0, 4))
        writeMetadata(part, etag = "\"revision-1\"")
        val requests = CopyOnWriteArrayList<String>()
        val server = FakeHttpServer(
            requests,
            listOf { request ->
                assertTrue(request.contains("Range: bytes=4-", ignoreCase = true))
                assertTrue(request.contains("If-Range: \"revision-1\"", ignoreCase = true))
                Response(
                    statusCode = 206,
                    headers = mapOf(
                        "Content-Range" to "bytes 4-${payload.lastIndex}/${payload.size}",
                        "ETag" to "\"revision-1\"",
                    ),
                    body = payload.copyOfRange(4, payload.size),
                )
            },
        )

        try {
            val destination = FileIn(directory, "model.onnx")
            downloader(part, destination, payload).download(server.url, {})
            server.await()

            assertArrayEquals(payload, destination.readBytes())
            assertFalse(part.exists())
            assertTrue(requests.single().contains("Range: bytes=4-", ignoreCase = true))
        } finally {
            server.close()
        }
    }

    @Test
    fun changedValidatorRestartsWithFreshResponseInsteadOfAppending() {
        val oldPayload = "olddata---".toByteArray(StandardCharsets.UTF_8)
        val newPayload = "newdata---".toByteArray(StandardCharsets.UTF_8)
        val directory = temporaryDirectory()
        val part = FileIn(directory, "model.part")
        part.writeBytes(oldPayload.copyOfRange(0, 4))
        writeMetadata(part, etag = "\"old\"")
        val requests = CopyOnWriteArrayList<String>()
        val logs = CopyOnWriteArrayList<String>()
        val server = FakeHttpServer(
            requests,
            listOf { request ->
                assertTrue(request.contains("Range: bytes=4-", ignoreCase = true))
                Response(
                    statusCode = 200,
                    headers = mapOf("ETag" to "\"new\""),
                    body = newPayload,
                )
            },
        )

        try {
            val destination = FileIn(directory, "model.onnx")
            downloader(part, destination, newPayload, logs::add).download(server.url, {})
            server.await()

            assertArrayEquals(newPayload, destination.readBytes())
            assertTrue(logs.any { it.contains("restarting from byte zero") })
            assertEquals(1, requests.size)
        } finally {
            server.close()
        }
    }

    @Test
    fun retriesTransientServerFailureWithJitterAndThenCompletes() {
        val payload = "good-model".toByteArray(StandardCharsets.UTF_8)
        val directory = temporaryDirectory()
        val part = FileIn(directory, "model.part")
        val requests = CopyOnWriteArrayList<String>()
        val server = FakeHttpServer(
            requests,
            listOf(
                { Response(statusCode = 503, body = byteArrayOf()) },
                { Response(statusCode = 200, body = payload) },
            ),
        )

        try {
            val destination = FileIn(directory, "model.onnx")
            downloader(part, destination, payload).download(server.url, {})
            server.await()

            assertArrayEquals(payload, destination.readBytes())
            assertEquals(2, requests.size)
        } finally {
            server.close()
        }
    }

    @Test
    fun checksumMismatchDeletesCorruptPartialFile() {
        val payload = "bad-model".toByteArray(StandardCharsets.UTF_8)
        val directory = temporaryDirectory()
        val part = FileIn(directory, "model.part")
        val requests = CopyOnWriteArrayList<String>()
        val server = FakeHttpServer(
            requests,
            listOf { Response(statusCode = 200, body = payload) },
        )

        try {
            val destination = FileIn(directory, "model.onnx")
            val exception = org.junit.Assert.assertThrows(java.io.IOException::class.java) {
                downloader(part, destination, payload, expectedHash = "0".repeat(64)).download(server.url, {})
            }
            server.await()

            assertTrue(exception.message.orEmpty().contains("SHA-256"))
            assertFalse(part.exists())
            assertFalse(destination.exists())
        } finally {
            server.close()
        }
    }

    @Test
    fun permanentHttpFailureIsNotRetried() {
        val payload = "unused".toByteArray(StandardCharsets.UTF_8)
        val directory = temporaryDirectory()
        val part = FileIn(directory, "model.part")
        val requests = CopyOnWriteArrayList<String>()
        val server = FakeHttpServer(
            requests,
            listOf { Response(statusCode = 404, body = payload) },
        )

        try {
            val exception = org.junit.Assert.assertThrows(ModelHttpException::class.java) {
                downloader(part, FileIn(directory, "model.onnx"), payload).download(server.url, {})
            }
            server.await()

            assertEquals(404, exception.statusCode)
            assertEquals(1, requests.size)
        } finally {
            server.close()
        }
    }

    @Test
    fun sha256ReturnsTheExpectedHexDigest() {
        val file = FileIn(temporaryDirectory(), "digest.txt")
        file.writeText("abc", StandardCharsets.UTF_8)

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ResumableModelDownloader.sha256(file),
        )
    }

    private fun downloader(
        part: java.io.File,
        destination: java.io.File,
        payload: ByteArray,
        log: (String) -> Unit = {},
        expectedHash: String = sha256(payload),
    ) = ResumableModelDownloader(
        partFile = part,
        destinationFile = destination,
        expectedBytes = payload.size.toLong(),
        expectedSha256 = expectedHash,
        log = log,
    )

    private fun writeMetadata(part: java.io.File, etag: String) {
        val metadata = Properties().apply { setProperty("etag", etag) }
        java.io.FileOutputStream(java.io.File(part.parentFile, "${part.name}.properties")).use {
            metadata.store(it, null)
        }
    }

    private fun temporaryDirectory(): java.io.File =
        Files.createTempDirectory("biodex-model-test").toFile().also(temporaryDirectories::add)

    private fun FileIn(directory: java.io.File, name: String) = java.io.File(directory, name)

    private fun sha256(payload: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }

    private data class Response(
        val statusCode: Int,
        val headers: Map<String, String> = emptyMap(),
        val body: ByteArray,
    )

    private class FakeHttpServer(
        private val requests: MutableList<String>,
        private val responses: List<(String) -> Response>,
    ) : AutoCloseable {
        private val server = ServerSocket(0, responses.size, java.net.InetAddress.getByName("127.0.0.1"))
        private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        private val future = executor.submit {
            responses.forEach { respond ->
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val request = readRequest(socket)
                    requests += request
                    writeResponse(socket, respond(request))
                }
            }
        }
        val url: String = "http://127.0.0.1:${server.localPort}/model"

        fun await() {
            future.get(5, TimeUnit.SECONDS)
        }

        override fun close() {
            server.close()
            executor.shutdownNow()
        }

        private fun readRequest(socket: Socket): String {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val request = StringBuilder()
            while (true) {
                val line = reader.readLine() ?: break
                request.append(line).append('\n')
                if (line.isEmpty()) break
            }
            return request.toString()
        }

        private fun writeResponse(socket: Socket, response: Response) {
            val output = ByteArrayOutputStream().apply {
                val statusText = if (response.statusCode == 503) "Service Unavailable" else "OK"
                write("HTTP/1.1 ${response.statusCode} $statusText\r\n".toByteArray(StandardCharsets.UTF_8))
                write("Content-Length: ${response.body.size}\r\n".toByteArray(StandardCharsets.UTF_8))
                write("Connection: close\r\n".toByteArray(StandardCharsets.UTF_8))
                response.headers.forEach { (name, value) ->
                    write("$name: $value\r\n".toByteArray(StandardCharsets.UTF_8))
                }
                write("\r\n".toByteArray(StandardCharsets.UTF_8))
                write(response.body)
            }
            socket.getOutputStream().use { it.write(output.toByteArray()) }
        }
    }
}
