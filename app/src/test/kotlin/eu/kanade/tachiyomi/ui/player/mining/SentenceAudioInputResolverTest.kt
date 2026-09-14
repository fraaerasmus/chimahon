package eu.kanade.tachiyomi.ui.player.mining

import chimahon.anki.AnkiSentenceAudioFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SentenceAudioInputResolverTest {

    @Test
    fun `jellyfin direct stream with api_key in the query is accepted`() {
        val url = "https://media.example.com/Videos/abc123/stream?static=True&api_key=0123456789abcdef"

        val spec = resolved(url)

        assertEquals(url, spec.value)
        assertEquals(SentenceAudioInputKind.REMOTE_HTTP, spec.kind)
        assertEquals(SentenceAudioInputOrigin.ORIGINAL_VIDEO, spec.origin)
    }

    @Test
    fun `jellyfin hls transcode with api_key in the query is accepted`() {
        val url = "https://media.example.com/Videos/abc123/master.m3u8?MediaSourceId=abc123&api_key=0123456789abcdef&VideoCodec=h264"

        assertEquals(SentenceAudioInputKind.REMOTE_HTTP, resolved(url).kind)
    }

    @Test
    fun `signed cdn urls and token queries are accepted`() {
        listOf(
            "https://cdn.example.com/ep1.mp4?token=abc&expires=123",
            "https://cdn.example.com/ep1.mp4?Policy=abc&Signature=def&Key-Pair-Id=ghi",
            "https://cdn.example.com/ep1.mp4?X-Amz-Signature=abc",
            "http://cdn.example.com/ep1.mp4?sig=abc",
        ).forEach { url ->
            assertEquals(url, resolved(url).value, url)
        }
    }

    @Test
    fun `auth headers are forwarded and unknown or unsafe headers are dropped instead of failing`() {
        val headers = listOf(
            "Authorization" to "MediaBrowser Token=\"abc\"",
            "X-Emby-Token" to "abc",
            "Cookie" to "session=1",
            "User-Agent" to "Chimahon",
            "X-Custom-Extension-Header" to "value",
            "Referer" to "https://example.com\r\nInjected: yes",
        )

        val spec = resolved("https://media.example.com/Videos/abc123/stream?static=True&api_key=abc", headers)

        assertEquals(
            listOf(
                "Authorization" to "MediaBrowser Token=\"abc\"",
                "X-Emby-Token" to "abc",
                "Cookie" to "session=1",
                "User-Agent" to "Chimahon",
            ),
            spec.headers,
        )
    }

    @Test
    fun `dash manifests and userinfo urls are still rejected`() {
        listOf(
            "https://media.example.com/Videos/abc123/stream.mpd?api_key=abc",
            "https://user:pass@media.example.com/Videos/abc123/stream",
        ).forEach { url ->
            val resolution = SentenceAudioInputResolver.resolveForCapture(snapshot(url, emptyList()))
            assertTrue(resolution is SentenceAudioInputResolution.Unavailable, url)
            assertEquals(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE, (resolution as SentenceAudioInputResolution.Unavailable).failure)
        }
    }

    @Test
    fun `sanitized log value redacts the api key`() {
        val sanitized = SentenceAudioInputResolver.sanitizeForLog(
            "https://media.example.com/Videos/abc123/stream?static=True&api_key=0123456789abcdef",
        )

        assertEquals("https://media.example.com/Videos/abc123/stream?static=True&api_key=[REDACTED]", sanitized)
    }

    private fun resolved(url: String, headers: List<Pair<String, String>> = emptyList()): SentenceAudioInputSpec {
        val resolution = SentenceAudioInputResolver.resolveForCapture(snapshot(url, headers))
        assertTrue(resolution is SentenceAudioInputResolution.Available, "expected $url to resolve, got $resolution")
        return (resolution as SentenceAudioInputResolution.Available).input
    }

    private fun snapshot(url: String, headers: List<Pair<String, String>>) = SentenceAudioInputSnapshot(
        originalVideoValue = url,
        playableValue = url,
        headers = headers,
        ffmpegStreamArgs = emptyList(),
        ffmpegVideoArgs = emptyList(),
        seekable = true,
        selectedAudioId = 1,
        audioTrackCount = 1,
        selectedAudioFfmpegIndex = 1,
        selectedAudioIsExternal = false,
        selectedExternalAudioValue = null,
    )
}
