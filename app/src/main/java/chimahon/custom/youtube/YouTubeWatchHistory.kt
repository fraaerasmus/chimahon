package chimahon.custom.youtube

import android.webkit.CookieManager
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.database.models.Episode
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.youtube.YouTubeSource
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Reports YouTube playback to the signed-in account's watch history the way the official
 * players do: a playback ping at start, then watch-time pings carrying the position.
 *
 * The stats URLs come from a WEB-client InnerTube player response fetched with the browser
 * session's cookies, so YouTube attributes the pings to that session. Parameters follow
 * yt-dlp's `--mark-watched` and ytui's history sync. Everything here is best effort: the
 * endpoints are undocumented, so failures are logged and otherwise ignored.
 */
object YouTubeWatchHistory {

    private const val ORIGIN = "https://www.youtube.com"
    private const val PLAYER_ENDPOINT = "$ORIGIN/youtubei/v1/player?prettyPrint=false"
    private const val WEB_CLIENT_VERSION = "2.20260101.00.00"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    private const val CPN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    class Session internal constructor(
        private val client: OkHttpClient,
        private val playbackUrl: String,
        private val watchtimeUrl: String,
        private val cpn: String,
    ) {
        private var lastReported = 0f

        suspend fun reportStart() = ping(statsUrl(playbackUrl, cpn, position = 0f))

        suspend fun reportWatchTime(position: Float, final: Boolean = false) {
            ping(statsUrl(watchtimeUrl, cpn, position, segmentStart = lastReported, final = final))
            lastReported = position
        }

        private suspend fun ping(url: String) {
            try {
                client.newCall(signedRequest(url).get().build()).await().close()
                logcat(LogPriority.DEBUG) { "YouTube history ping ok: ${url.substringBefore('?').substringAfterLast('/')}" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "YouTube history ping failed" }
            }
        }
    }

    /** Null when not signed in, or when YouTube returns no tracking URLs for the video. */
    suspend fun open(videoId: String, client: OkHttpClient = Injekt.get<NetworkHelper>().client): Session? {
        if (sapisid() == null) return null
        return try {
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"$WEB_CLIENT_VERSION"}},"videoId":"$videoId"}"""
            val request = signedRequest(PLAYER_ENDPOINT)
                .header("X-Youtube-Client-Name", "1")
                .header("X-Youtube-Client-Version", WEB_CLIENT_VERSION)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val json = client.newCall(request).await().use { it.body.string() }
            trackingUrls(json)?.let { (playback, watchtime) -> Session(client, playback, watchtime, newCpn()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "YouTube history: player request failed" }
            null
        }
    }

    private fun signedRequest(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
        sapisid()?.let { builder.header("Authorization", "SAPISIDHASH ${sapisidHash(it, ORIGIN)}") }
        return builder
    }

    private fun sapisid(): String? = runCatching {
        CookieManager.getInstance().getCookie(ORIGIN)
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("SAPISID=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    // Pure helpers below are what the unit tests cover.

    internal fun statsUrl(
        baseUrl: String,
        cpn: String,
        position: Float,
        segmentStart: Float? = null,
        final: Boolean = false,
    ): String {
        val builder = baseUrl.toHttpUrlOrNull()?.newBuilder() ?: return baseUrl
        builder.setQueryParameter("ver", "2")
        builder.setQueryParameter("cpn", cpn)
        builder.setQueryParameter("cmt", position.seconds())
        builder.setQueryParameter("el", "detailpage") // otherwise counted as a Short
        if (segmentStart != null) {
            builder.setQueryParameter("st", segmentStart.seconds())
            builder.setQueryParameter("et", position.seconds())
            if (final) builder.setQueryParameter("final", "1")
        }
        return builder.build().toString()
    }

    /** Playback and watch-time base URLs from a player response, or null if either is missing. */
    internal fun trackingUrls(playerResponseJson: String): Pair<String, String>? = runCatching {
        val tracking = Json.parseToJsonElement(playerResponseJson).jsonObject["playbackTracking"]?.jsonObject ?: return null
        fun url(key: String) = tracking[key]?.jsonObject?.get("baseUrl")?.jsonPrimitive?.content?.takeIf { it.startsWith("http") }
        val playback = url("videostatsPlaybackUrl") ?: return null
        val watchtime = url("videostatsWatchtimeUrl") ?: return null
        playback to watchtime
    }.getOrNull()

    internal fun sapisidHash(sapisid: String, origin: String, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$nowSeconds $sapisid $origin".toByteArray())
        return "${nowSeconds}_${digest.toHexString()}"
    }

    internal fun newCpn(): String = buildString { repeat(16) { append(CPN_ALPHABET[Random.nextInt(64)]) } }

    private fun Float.seconds() = "%.1f".format(java.util.Locale.ROOT, this)
}

/**
 * Drives [YouTubeWatchHistory] from the player's state: one session per YouTube episode,
 * a start ping, a watch-time ping every [REPORT_INTERVAL_MS] while playing, and a final
 * ping when the episode changes or [stop] is called. The optional [enabled] is read when
 * an episode starts, so toggling the setting applies to the next video.
 */
class YouTubeWatchHistorySync(
    episode: StateFlow<Episode?>,
    source: StateFlow<AnimeSource?>,
    private val position: StateFlow<Float>,
    private val paused: StateFlow<Boolean>,
    private val open: suspend (videoId: String) -> YouTubeWatchHistory.Session? = { YouTubeWatchHistory.open(it) },
    private val enabled: () -> Boolean,
) {
    // Own scope so the final ping outlives the view model that owns this object. It runs one
    // coroutine at a time, because the watcher, the reporting job and stop() all touch
    // [session] and [reporting].
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var session: YouTubeWatchHistory.Session? = null
    private var reporting: Job? = null

    private val watcher = scope.launch {
        combine(episode, source) { ep, src -> ep?.url?.takeIf { src?.id == YouTubeSource.ID } }
            .distinctUntilChanged()
            .collect { episodeUrl ->
                finish()
                val videoId = episodeUrl?.removePrefix(YouTubeSource.WATCH_PREFIX) ?: return@collect
                if (!enabled()) return@collect
                reporting = scope.launch {
                    val opened = open(videoId) ?: return@launch
                    session = opened
                    paused.first { !it }
                    opened.reportStart()
                    while (isActive) {
                        delay(REPORT_INTERVAL_MS)
                        if (!paused.value) opened.reportWatchTime(position.value)
                    }
                }
            }
    }

    fun stop() {
        watcher.cancel()
        val lastPosition = position.value
        scope.launch { finish(lastPosition) }
    }

    private fun finish(lastPosition: Float = position.value) {
        reporting?.cancel()
        reporting = null
        val opened = session ?: return
        session = null
        scope.launch { opened.reportWatchTime(lastPosition, final = true) }
    }

    private companion object {
        const val REPORT_INTERVAL_MS = 10_000L
    }
}
