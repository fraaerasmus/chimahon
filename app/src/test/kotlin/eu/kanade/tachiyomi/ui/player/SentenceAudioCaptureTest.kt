package eu.kanade.tachiyomi.ui.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SentenceAudioCaptureTest {
    @Test
    fun `uses selected external audio track`() {
        val input = resolveSentenceAudioInput(
            videoUrl = "https://example.com/video-only.mp4",
            playbackPath = "https://example.com/video-only.mp4",
            selectedAudioId = 2,
            audioTracks = listOf(
                PlayerViewModel.VideoTrack(
                    id = 2,
                    name = "Audio",
                    language = "ja",
                    externalFilename = "https://example.com/audio.webm",
                ),
            ),
        )

        assertEquals("https://example.com/audio.webm", input)
    }

    @Test
    fun `uses playback path for muxed media`() {
        val input = resolveSentenceAudioInput(
            videoUrl = "https://example.com/fallback.mp4",
            playbackPath = "file:///video/episode.mkv",
            selectedAudioId = 1,
            audioTracks = emptyList(),
        )

        assertEquals("file:///video/episode.mkv", input)
    }

    @Test
    fun `uses video url when playback path is blank`() {
        val input = resolveSentenceAudioInput(
            videoUrl = "https://example.com/episode.mp4",
            playbackPath = "",
            selectedAudioId = -1,
            audioTracks = emptyList(),
        )

        assertEquals("https://example.com/episode.mp4", input)
    }
}
