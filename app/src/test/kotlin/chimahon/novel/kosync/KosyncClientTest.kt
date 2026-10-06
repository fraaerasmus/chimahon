package chimahon.novel.kosync

import chimahon.custom.core.ServerException
import chimahon.custom.core.TinyHttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.time.Duration.Companion.milliseconds

class KosyncClientTest {
    private fun credentials(serverUrl: String) = KosyncCredentials(
        serverUrl = serverUrl,
        username = "reader",
        userKey = "0123456789abcdef0123456789abcdef",
    )

    @Test
    fun `a pull gives up on a server that never answers`() {
        // The connection is accepted by the OS backlog and then nothing is ever written back.
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { silentServer ->
            val client = KosyncClient(pullConnectTimeout = 300.milliseconds, pullReadTimeout = 300.milliseconds)

            val startedAt = System.nanoTime()
            assertThrows(IOException::class.java) {
                runBlocking { client.getProgress(credentials("http://127.0.0.1:${silentServer.localPort}"), "document") }
            }
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

            // Far below the 15 s a push is allowed to wait.
            assertTrue(elapsedMillis < 5_000, "pull took $elapsedMillis ms")
        }
    }

    @Test
    fun `a pull reads the position and sends the KOReader headers`() = runBlocking {
        TinyHttpServer {
            TinyHttpServer.Response(
                200,
                """{"document":"abc","progress":"/body/DocFragment[3]/body/p[2]/text().0",""" +
                    """"percentage":0.25,"device":"Kobo","device_id":"KOBO1","timestamp":1700000000}""",
            )
        }.use { server ->
            // A typed address without a scheme and with a trailing slash still resolves.
            val address = server.url.removePrefix("http://") + "/"
            val progress = KosyncClient().getProgress(credentials(address), "abc")

            assertEquals("/body/DocFragment[3]/body/p[2]/text().0", progress?.progress)
            assertEquals(0.25, progress?.percentage)
            assertEquals("KOBO1", progress?.deviceId)
            assertEquals(1_700_000_000L, progress?.timestamp)

            val request = server.requests.single()
            assertEquals("GET /syncs/progress/abc", "${request.method} ${request.path}")
            assertEquals("application/vnd.koreader.v1+json", request.header("Accept"))
            assertEquals("reader", request.header("x-auth-user"))
            assertEquals("0123456789abcdef0123456789abcdef", request.header("x-auth-key"))
        }
    }

    @Test
    fun `a document the server has never seen is not an error`() = runBlocking {
        TinyHttpServer { TinyHttpServer.Response(404) }.use { server ->
            assertNull(KosyncClient().getProgress(credentials(server.url), "abc"))
        }
        // Some servers answer 200 with an empty object instead.
        TinyHttpServer { TinyHttpServer.Response(200, "{}") }.use { server ->
            assertNull(KosyncClient().getProgress(credentials(server.url), "abc")?.percentage)
        }
    }

    @Test
    fun `a push sends a page number as a number and an xpointer as a string`() = runBlocking {
        TinyHttpServer { TinyHttpServer.Response(200, """{"document":"abc","timestamp":1700000123}""") }.use { server ->
            val client = KosyncClient()
            val timestamp = client.putProgress(credentials(server.url), "abc", "12", 0.5, "Phone", "DEV", numericProgress = true)
            client.putProgress(credentials(server.url), "abc", "/body/DocFragment[1]/body", 0.5, "Phone", "DEV")

            assertEquals(1_700_000_123L, timestamp)
            val (paged, reflowable) = server.requests.map { it.body.toString(Charsets.UTF_8) }
            assertEquals(
                """{"document":"abc","progress":12,"percentage":0.5,"device":"Phone","device_id":"DEV"}""",
                paged,
            )
            assertTrue(reflowable.contains(""""progress":"/body/DocFragment[1]/body""""), reflowable)
            assertEquals("PUT /syncs/progress", server.requests.first().let { "${it.method} ${it.path}" })
            // Bare, as KOReader sends it.
            assertEquals("application/json", server.requests.first().header("Content-Type"))
        }
    }

    @Test
    fun `server refusals are worded for the user`() {
        fun messageFor(status: Int): String? = TinyHttpServer { TinyHttpServer.Response(status) }.use { server ->
            assertThrows(ServerException::class.java) {
                runBlocking { KosyncClient().authorize(credentials(server.url)) }
            }.message
        }
        assertEquals("Incorrect username or password.", messageFor(401))
        assertEquals("Username is already taken.", messageFor(402))
        assertEquals("Unknown user.", messageFor(403))
        assertEquals("Server returned HTTP 500.", messageFor(500))
        assertEquals(
            "The server address is not a valid URL.",
            assertThrows(ServerException::class.java) {
                runBlocking { KosyncClient().authorize(credentials("http://")) }
            }.message,
        )
    }
}
