package eu.kanade.tachiyomi.ui.player

import okhttp3.Headers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JellyfinPlaybackReporterTest {

    private val auth = "MediaBrowser Client=\"Aniyomi\", Version=\"1\", DeviceId=\"abc\", Device=\"Phone\", Token=\"t0ken\""
    private val headers = Headers.headersOf("Authorization", auth)

    @Test
    fun `direct stream url yields a direct play target`() {
        val target = JellyfinPlaybackReporter.parseTarget(
            "https://media.example.org/Videos/item123/stream?static=True&PlaySessionId=sess1",
            headers,
        )

        assertEquals(
            JellyfinPlaybackReporter.Target(
                baseUrl = "https://media.example.org",
                authorization = auth,
                itemId = "item123",
                mediaSourceId = "item123",
                playSessionId = "sess1",
                playMethod = "DirectPlay",
            ),
            target,
        )
    }

    @Test
    fun `transcoding url keeps the server path prefix and media source`() {
        val target = JellyfinPlaybackReporter.parseTarget(
            "http://media.example.org:8096/jellyfin/videos/item123/master.m3u8" +
                "?DeviceId=abc&MediaSourceId=src9&VideoBitrate=4000000&PlaySessionId=sess2&api_key=t0ken",
            headers,
        )!!

        assertEquals("http://media.example.org:8096/jellyfin", target.baseUrl)
        assertEquals("item123", target.itemId)
        assertEquals("src9", target.mediaSourceId)
        assertEquals("sess2", target.playSessionId)
        assertEquals("Transcode", target.playMethod)
    }

    @Test
    fun `streams without a MediaBrowser authorization are ignored`() {
        val url = "https://media.example.org/Videos/item123/stream?static=True"

        assertNull(JellyfinPlaybackReporter.parseTarget(url, null))
        assertNull(JellyfinPlaybackReporter.parseTarget(url, Headers.headersOf("Referer", "https://example.org/")))
        assertNull(JellyfinPlaybackReporter.parseTarget(url, Headers.headersOf("Authorization", "Bearer xyz")))
    }

    @Test
    fun `urls that are not a Jellyfin video path are ignored`() {
        assertNull(JellyfinPlaybackReporter.parseTarget("https://cdn.example.org/hls/master.m3u8", headers))
        assertNull(JellyfinPlaybackReporter.parseTarget("https://media.example.org/Videos", headers))
        assertNull(JellyfinPlaybackReporter.parseTarget("/storage/emulated/0/video.mkv", headers))
    }
}
