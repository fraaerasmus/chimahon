package chimahon.novel.kosync

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

class KosyncClientTest {
    @Test
    fun `a pull gives up on a server that never answers`() {
        // The connection is accepted by the OS backlog and then nothing is ever written back.
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { silentServer ->
            val client = KosyncClient(pullConnectTimeoutMillis = 300, pullReadTimeoutMillis = 300)
            val credentials = KosyncCredentials(
                serverUrl = "http://127.0.0.1:${silentServer.localPort}",
                username = "reader",
                userKey = "0123456789abcdef0123456789abcdef",
            )

            val startedAt = System.nanoTime()
            assertThrows(SocketTimeoutException::class.java) {
                runBlocking { client.getProgress(credentials, "document") }
            }
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

            // Far below the 15 s a push is allowed to wait.
            assertTrue(elapsedMillis < 5_000, "pull took $elapsedMillis ms")
        }
    }
}
