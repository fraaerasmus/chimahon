package eu.kanade.tachiyomi.data.upload

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.Base64
import java.util.Collections
import kotlin.concurrent.thread

/** A tiny WebDAV-ish server: MKCOL, HEAD, PUT and GET over HTTP/1.1 with one request per connection. */
class WebDavUploadClientTest {
    private lateinit var socket: ServerSocket
    private lateinit var listener: Thread
    private val files = Collections.synchronizedMap(mutableMapOf<String, ByteArray>())
    private val requests = Collections.synchronizedList(mutableListOf<String>())

    @Volatile
    private var lastAuth: String? = null

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
                    val input = open.getInputStream().buffered()
                    val requestLine = readLine(input) ?: return@use
                    val (method, rawPath) = requestLine.split(" ").let { it[0] to it[1] }
                    val path = URLDecoder.decode(rawPath, "UTF-8")
                    var contentLength = 0
                    var auth: String? = null
                    while (true) {
                        val header = readLine(input)
                        if (header.isNullOrEmpty()) break
                        if (header.startsWith("Content-Length:", true)) contentLength = header.substringAfter(':').trim().toInt()
                        if (header.startsWith("Authorization:", true)) auth = header.substringAfter(':').trim()
                    }
                    lastAuth = auth
                    requests += "$method $path"
                    val body = readFully(input, contentLength)
                    val output = open.getOutputStream()
                    when {
                        path.contains("/forbidden") -> output.write(head(403, 0))
                        else -> respond(method, path, body, output)
                    }
                    output.flush()
                }
            }
        }
    }

    private fun respond(method: String, path: String, body: ByteArray, output: java.io.OutputStream) {
        when (method) {
            "MKCOL" -> output.write(head(if (path.endsWith("/exists")) 405 else 201, 0))
            "HEAD" -> files[path]?.let { output.write(head(200, it.size)) } ?: output.write(head(404, 0))
            "PUT" -> {
                files[path] = body
                output.write(head(201, 0))
            }
            "GET" -> files[path]?.let {
                output.write(head(200, it.size))
                output.write(it)
            } ?: output.write(head(404, 0))
            else -> output.write(head(500, 0))
        }
    }

    @AfterEach
    fun stop() {
        socket.close()
    }

    private val payload = ByteArray(200 * 1024) { ((it * 13 + 5) and 0xff).toByte() }

    private fun client() = WebDavUploadClient(
        settings = {
            WebDavUploadClient.Settings(
                baseUrl = "http://127.0.0.1:${socket.localPort}/dav/",
                username = "reader",
                password = "hunter2",
                uploadFolder = "/manga/",
            )
        },
    )

    @Test
    fun `builds encoded urls under the upload folder`() {
        assertEquals(
            "http://127.0.0.1:${socket.localPort}/dav/manga/Berserk/Berserk,%20Ch.%20012.cbz",
            client().fileUrl("Berserk", "Berserk, Ch. 012.cbz"),
        )
        assertEquals(false, WebDavUploadClient.Settings("", "u", "p", "manga").isConfigured)
        assertEquals(true, WebDavUploadClient.Settings("http://h", "u", "p", "").isConfigured)
    }

    @Test
    fun `uploads the exact bytes, then sees the same size, and reads the sidecar`() = runBlocking {
        val client = client()
        val url = client.fileUrl("Berserk", "Berserk, Ch. 012.cbz")

        client.ensureFolder("Berserk")
        assertNull(client.remoteSize(url))
        client.put(url, payload.size.toLong()) { ByteArrayInputStream(payload) }
        assertArrayEquals(payload, files["/dav/manga/Berserk/Berserk, Ch. 012.cbz"])
        assertEquals(payload.size.toLong(), client.remoteSize(url))

        val sidecar = client.fileUrl("Berserk", "Berserk, Ch. 012.mokuro")
        assertNull(client.getText(sidecar))
        files["/dav/manga/Berserk/Berserk, Ch. 012.mokuro"] = "{\"pages\":[]}".toByteArray()
        assertEquals("{\"pages\":[]}", client.getText(sidecar))

        assertEquals(listOf("MKCOL /dav/manga", "MKCOL /dav/manga/Berserk"), requests.take(2))
        assertEquals("Basic " + Base64.getEncoder().encodeToString("reader:hunter2".toByteArray()), lastAuth)
    }

    @Test
    fun `an existing folder is not an error`() = runBlocking {
        client().ensureFolder("exists")
    }

    @Test
    fun `unexpected statuses surface as errors`() {
        val client = client()
        val url = "http://127.0.0.1:${socket.localPort}/dav/manga/forbidden/x.cbz"
        val head = assertThrows(WebDavUploadClient.WebDavUploadException::class.java) {
            runBlocking { client.remoteSize(url) }
        }
        assertEquals(true, head.message.orEmpty().contains("HTTP 403"), head.message)
        assertThrows(WebDavUploadClient.WebDavUploadException::class.java) {
            runBlocking { client.put(url, 0) { ByteArrayInputStream(ByteArray(0)) } }
        }
        assertThrows(WebDavUploadClient.WebDavUploadException::class.java) {
            runBlocking { client.getText(url) }
        }
        assertThrows(WebDavUploadClient.WebDavUploadException::class.java) {
            runBlocking { client.ensureFolder("forbidden") }
        }
    }

    private fun readLine(input: InputStream): String? {
        val bytes = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (bytes.isEmpty()) null else bytes.toString()
            if (byte == '\n'.code) return bytes.toString().trimEnd('\r')
            bytes.append(byte.toChar())
        }
    }

    private fun readFully(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) break
            read += n
        }
        return buffer
    }

    private fun head(status: Int, length: Int): ByteArray =
        "HTTP/1.1 $status ${if (status < 400) "OK" else "Error"}\r\nContent-Length: $length\r\nConnection: close\r\n\r\n"
            .toByteArray(Charsets.ISO_8859_1)
}
