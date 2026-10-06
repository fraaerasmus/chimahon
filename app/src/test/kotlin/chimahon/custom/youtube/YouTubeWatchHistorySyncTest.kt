package chimahon.custom.youtube

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.database.models.Episode
import eu.kanade.tachiyomi.data.database.models.EpisodeImpl
import eu.kanade.tachiyomi.ui.youtube.YouTubeSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class YouTubeWatchHistorySyncTest {

    private val pings = CopyOnWriteArrayList<String>()

    // Answers every ping itself, so nothing leaves the machine.
    private val recordingClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            pings += chain.request().url.toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(204)
                .message("No Content")
                .body("".toResponseBody())
                .build()
        }
        .build()

    private val youtube = mockk<AnimeSource> { every { id } returns YouTubeSource.ID }
    private val episode = MutableStateFlow<Episode?>(
        EpisodeImpl().apply { url = YouTubeSource.WATCH_PREFIX + "abc" },
    )
    private val source = MutableStateFlow<AnimeSource?>(youtube)
    private val position = MutableStateFlow(0f)
    private val paused = MutableStateFlow(false)

    private fun session(client: OkHttpClient) = YouTubeWatchHistory.Session(
        client = client,
        playbackUrl = "https://example.invalid/playback",
        watchtimeUrl = "https://example.invalid/watchtime",
        cpn = "cpn123",
    )

    @Test
    fun stoppingSendsOneFinalPingWithTheLastPosition() {
        val sync = YouTubeWatchHistorySync(episode, source, position, paused, open = { session(recordingClient) }) { true }
        awaitUntil { pings.any { "/playback" in it } }

        position.value = 42.5f
        sync.stop()
        awaitUntil { pings.any { "final=1" in it } }
        Thread.sleep(200)

        val finalPings = pings.filter { "final=1" in it }
        assertEquals(1, finalPings.size)
        assertTrue("et=42.5" in finalPings.single())
    }

    @Test
    fun stoppingWhileTheSessionIsStillOpeningCancelsItAndSendsNothing() {
        val neverOpens = CompletableDeferred<YouTubeWatchHistory.Session?>()
        val openCalled = CountDownLatch(1)
        val openCancelled = CountDownLatch(1)
        val sync = YouTubeWatchHistorySync(
            episode,
            source,
            position,
            paused,
            open = {
                openCalled.countDown()
                try {
                    neverOpens.await()
                } catch (e: CancellationException) {
                    openCancelled.countDown()
                    throw e
                }
            },
        ) { true }
        assertTrue(openCalled.await(5, TimeUnit.SECONDS))

        sync.stop()

        assertTrue(openCancelled.await(5, TimeUnit.SECONDS))
        assertTrue(pings.isEmpty(), "pings sent after stop: $pings")
    }

    @Test
    fun aCancelledPingCancelsItsCallerInsteadOfReturning() {
        val requestStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val hangingClient = OkHttpClient.Builder()
            .addInterceptor {
                requestStarted.countDown()
                release.await(5, TimeUnit.SECONDS)
                throw IOException("released")
            }
            .build()
        val returnedNormally = AtomicBoolean(false)

        runBlocking {
            val caller = launch(Dispatchers.Default) {
                session(hangingClient).reportStart()
                returnedNormally.set(true)
            }
            assertTrue(requestStarted.await(5, TimeUnit.SECONDS))
            caller.cancelAndJoin()
        }
        release.countDown()

        assertFalse(returnedNormally.get())
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met within 5 s; pings=$pings" }
            Thread.sleep(20)
        }
    }
}
