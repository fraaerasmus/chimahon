package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import logcat.LogPriority
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Reports playback of a Jellyfin stream to its server (`/Sessions/Playing*`), so the server lists
 * it as an active session like any other Jellyfin client. Everything needed comes from the video
 * the Jellyfin extension produced: the stream url names the server, item and play session, and the
 * `Authorization` header carries the device id and token the session is keyed on.
 *
 * Reports are fire-and-forget and never affect playback. They run on their own scope so the final
 * "stopped" still goes out after the player is destroyed, and one at a time so they arrive in order.
 */
class JellyfinPlaybackReporter(
    private val target: Target,
    private val positionSeconds: () -> Float,
) {

    data class Target(
        val baseUrl: String,
        val authorization: String,
        val itemId: String,
        val mediaSourceId: String,
        val playSessionId: String?,
        val playMethod: String,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    @Volatile
    private var ticker: Job? = null

    @Volatile
    private var paused = false

    @Volatile
    private var lastTicks = 0L

    fun start(paused: Boolean) {
        this.paused = paused
        post("Sessions/Playing", currentTicks())
        ticker = scope.launch {
            // The server drops sessions that stop checking in
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                send("Sessions/Playing/Progress", currentTicks())
            }
        }
    }

    fun setPaused(paused: Boolean) {
        this.paused = paused
        if (ticker == null) return
        post("Sessions/Playing/Progress", currentTicks())
    }

    /**
     * @param atLastReported the player has already moved on to another file, so its current
     * position no longer belongs to this one.
     */
    fun stop(atLastReported: Boolean = false) {
        if (ticker == null) return
        ticker?.cancel()
        ticker = null
        post("Sessions/Playing/Stopped", if (atLastReported) lastTicks else currentTicks())
    }

    private fun currentTicks(): Long =
        (positionSeconds().coerceAtLeast(0f).toDouble() * TICKS_PER_SECOND).toLong().also { lastTicks = it }

    private fun post(path: String, ticks: Long) {
        scope.launch { send(path, ticks) }
    }

    private fun send(path: String, ticks: Long) {
        val body = buildJsonObject {
            put("ItemId", target.itemId)
            put("MediaSourceId", target.mediaSourceId)
            target.playSessionId?.let { put("PlaySessionId", it) }
            put("PositionTicks", ticks)
            put("IsPaused", paused)
            put("CanSeek", true)
            put("PlayMethod", target.playMethod)
        }.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url("${target.baseUrl}/$path")
            .header("Authorization", target.authorization)
            .post(body)
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin playback report failed: $path" }
        }
    }

    companion object {
        private const val PROGRESS_INTERVAL_MS = 10_000L
        private const val TICKS_PER_SECOND = 10_000_000L
        private val JSON = "application/json".toMediaType()

        private val client: OkHttpClient by lazy {
            Injekt.get<NetworkHelper>().client.newBuilder()
                .dns(Dns.SYSTEM) // don't use DNS over HTTPS as it breaks IP addressing
                .build()
        }

        /**
         * Recognises a stream from the Jellyfin extension: a `MediaBrowser` authorization header and
         * a `{server}/Videos/{itemId}/...` url. Returns null for anything else.
         */
        fun parseTarget(videoUrl: String, headers: Headers?): Target? {
            val authorization = headers?.get("Authorization")
                ?.takeIf { it.startsWith("MediaBrowser ", ignoreCase = true) }
                ?: return null
            val url = videoUrl.toHttpUrlOrNull() ?: return null
            val segments = url.encodedPathSegments
            val videosIndex = segments.indexOfFirst { it.equals("videos", ignoreCase = true) }
            val itemId = segments.getOrNull(videosIndex + 1)?.takeIf { videosIndex >= 0 && it.isNotEmpty() }
                ?: return null

            fun query(name: String) = url.queryParameterNames
                .firstOrNull { it.equals(name, ignoreCase = true) }
                ?.let(url::queryParameter)

            val baseUrl = url.newBuilder()
                .encodedPath("/" + segments.take(videosIndex).joinToString("/"))
                .query(null)
                .fragment(null)
                .build()
                .toString()
                .trimEnd('/')

            return Target(
                baseUrl = baseUrl,
                authorization = authorization,
                itemId = itemId,
                mediaSourceId = query("MediaSourceId") ?: itemId,
                playSessionId = query("PlaySessionId"),
                playMethod = if (query("static").equals("true", ignoreCase = true)) "DirectPlay" else "Transcode",
            )
        }
    }
}
