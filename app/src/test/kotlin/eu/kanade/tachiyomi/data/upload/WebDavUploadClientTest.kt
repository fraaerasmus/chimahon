package eu.kanade.tachiyomi.data.upload

import chimahon.custom.core.ServerException
import chimahon.custom.core.TinyHttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.Collections

/** Against a tiny WebDAV-ish server: MKCOL, HEAD, PUT and GET. */
class WebDavUploadClientTest {
    private val files = Collections.synchronizedMap(mutableMapOf<String, ByteArray>())

    private val server = TinyHttpServer { request ->
        val path = request.path
        when {
            path.contains("/forbidden") -> TinyHttpServer.Response(403)
            request.method == "MKCOL" -> TinyHttpServer.Response(if (path.endsWith("/exists")) 405 else 201)
            request.method == "PUT" -> {
                files[path] = request.body
                TinyHttpServer.Response(201)
            }
            request.method == "HEAD" || request.method == "GET" ->
                files[path]?.let { TinyHttpServer.Response(200, it) } ?: TinyHttpServer.Response(404)
            else -> TinyHttpServer.Response(500)
        }
    }

    @AfterEach
    fun stop() = server.close()

    private val payload = ByteArray(200 * 1024) { ((it * 13 + 5) and 0xff).toByte() }

    private fun client() = WebDavUploadClient(
        settings = {
            WebDavUploadClient.Settings(
                baseUrl = "${server.url}/dav/",
                username = "reader",
                password = "hunter2",
                uploadFolder = "/manga/",
            )
        },
    )

    @Test
    fun `builds encoded urls under the upload folder`() {
        assertEquals(
            "${server.url}/dav/manga/Berserk/Berserk,%20Ch.%20012.cbz",
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

        assertEquals(listOf("MKCOL /dav/manga", "MKCOL /dav/manga/Berserk"), server.requests.take(2).map { "${it.method} ${it.path}" })
        assertEquals(
            "Basic " + Base64.getEncoder().encodeToString("reader:hunter2".toByteArray()),
            server.requests.last().header("Authorization"),
        )
    }

    @Test
    fun `an existing folder is not an error`() = runBlocking {
        client().ensureFolder("exists")
    }

    @Test
    fun `unexpected statuses surface as errors`() {
        val client = client()
        val url = "${server.url}/dav/manga/forbidden/x.cbz"
        val head = assertThrows(ServerException::class.java) {
            runBlocking { client.remoteSize(url) }
        }
        assertEquals(true, head.message.orEmpty().contains("HTTP 403"), head.message)
        assertThrows(ServerException::class.java) {
            runBlocking { client.put(url, 0) { ByteArrayInputStream(ByteArray(0)) } }
        }
        assertThrows(ServerException::class.java) {
            runBlocking { client.getText(url) }
        }
        assertThrows(ServerException::class.java) {
            runBlocking { client.ensureFolder("forbidden") }
        }
    }
}
