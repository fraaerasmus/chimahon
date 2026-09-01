package com.canopus.chimareader.opds

import com.canopus.chimareader.kosync.KosyncDocumentId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.ServerSocket
import java.util.Base64
import kotlin.concurrent.thread

/**
 * The acceptance property for cross-device sync: a book fetched from a catalog has to reach disk
 * with the server's exact bytes, because KOReader derives the document id from them.
 */
class OpdsDownloadTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var socket: ServerSocket
    private lateinit var listener: Thread

    @Volatile
    private var lastAuthHeader: String? = null

    // Large enough that the transfer crosses many read buffers and three partial-MD5 sample windows.
    private val payload = ByteArray(300 * 1024) { ((it * 31 + 7) and 0xff).toByte() }

    @BeforeEach
    fun start() {
        socket = ServerSocket(0)
        listener = thread(isDaemon = true) {
            while (!socket.isClosed) {
                val connection = try {
                    socket.accept()
                } catch (e: Exception) {
                    return@thread
                }
                connection.use { open ->
                    val input = open.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val requestLine = input.readLine() ?: return@use
                    val path = requestLine.split(" ").getOrElse(1) { "" }
                    var auth: String? = null
                    while (true) {
                        val header = input.readLine()
                        if (header.isNullOrEmpty()) break
                        if (header.startsWith("Authorization:", ignoreCase = true)) {
                            auth = header.substringAfter(':').trim()
                        }
                    }
                    lastAuthHeader = auth
                    val output = open.getOutputStream()
                    when {
                        auth == null -> output.write(head(401, 0))
                        path.startsWith("/get/epub/18") -> {
                            output.write(head(200, payload.size, "attachment; filename=\"Same Dream.epub\""))
                            output.write(payload)
                        }
                        else -> output.write(head(404, 0))
                    }
                    output.flush()
                }
            }
        }
    }

    @AfterEach
    fun stop() {
        socket.close()
    }

    private fun head(status: Int, length: Int, disposition: String? = null): ByteArray = buildString {
        append("HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\n")
        append("Content-Length: $length\r\n")
        disposition?.let { append("Content-Disposition: $it\r\n") }
        append("Connection: close\r\n\r\n")
    }.toByteArray(Charsets.ISO_8859_1)

    private val base get() = "http://127.0.0.1:${socket.localPort}"

    private val catalog = OpdsCatalog(id = "c", name = "calibre", url = "", username = "reader", password = "hunter2")

    @Test
    fun `download keeps the server's exact bytes`() = runBlocking {
        var lastSeen = 0L
        val result = OpdsClient().download(
            catalog = catalog,
            url = "$base/get/epub/18/calibre",
            directory = tempDir,
            fallbackName = "fallback.epub",
            onProgress = { done, _ -> lastSeen = done },
        )

        assertArrayEquals(payload, result.file.readBytes())
        assertEquals(payload.size.toLong(), lastSeen)
        assertEquals("Same Dream.epub", result.fileName)

        // The id KOReader would compute has to survive the transfer unchanged.
        val reference = File(tempDir, "reference.epub").apply { writeBytes(payload) }
        assertEquals(KosyncDocumentId.partialMd5(reference), KosyncDocumentId.partialMd5(result.file))
    }

    @Test
    fun `download sends basic auth`() = runBlocking {
        OpdsClient().download(catalog, "$base/get/epub/18/calibre", tempDir, "fallback.epub")
        val expected = "Basic " + Base64.getEncoder().encodeToString("reader:hunter2".toByteArray())
        assertEquals(expected, lastAuthHeader)
    }

    @Test
    fun `download surfaces a server error instead of writing a file`() {
        val error = assertThrows(OpdsException::class.java) {
            runBlocking { OpdsClient().download(catalog, "$base/get/epub/missing", tempDir, "fallback.epub") }
        }
        assertEquals(404, error.statusCode)
        val leftovers = tempDir.list().orEmpty().filter { it.startsWith("opds-") }
        assertEquals(emptyList<String>(), leftovers)
    }
}
