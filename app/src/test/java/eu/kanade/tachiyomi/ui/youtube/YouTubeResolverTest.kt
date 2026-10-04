package eu.kanade.tachiyomi.ui.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import java.util.Locale

class YouTubeResolverTest {

    private fun stream(
        itag: Int,
        bitrate: Int,
        format: MediaFormat = MediaFormat.WEBMA_OPUS,
        trackId: String? = null,
        trackName: String? = null,
        type: AudioTrackType? = null,
        locale: Locale? = null,
        isUrl: Boolean = true,
    ): AudioStream = AudioStream.Builder()
        .setId("$itag-${trackId ?: "x"}")
        .setContent("https://example.invalid/$itag/${trackId ?: "x"}", isUrl)
        .setMediaFormat(format)
        .setAverageBitrate(bitrate)
        .setAudioTrackId(trackId)
        .setAudioTrackName(trackName)
        .setAudioTrackType(type)
        .setAudioLocale(locale)
        .build()

    @Test
    fun singleLanguageVideoYieldsOneBestTrackNamedAudio() {
        val tracks = YouTubeResolver.selectAudioTracks(
            listOf(
                stream(139, 48, MediaFormat.M4A),
                stream(140, 128, MediaFormat.M4A),
                stream(249, 50),
                stream(250, 70),
                stream(251, 160),
            ),
        )

        assertEquals(1, tracks.size)
        assertEquals("https://example.invalid/251/x", tracks.single().url)
        assertEquals("Audio", tracks.single().lang)
    }

    @Test
    fun dubbedVideoYieldsOriginalFirstThenDubWithMarker() {
        val tracks = YouTubeResolver.selectAudioTracks(
            listOf(
                stream(139, 48, MediaFormat.M4A, "en-US.10", "English (US)", AudioTrackType.DUBBED, Locale.US),
                stream(139, 48, MediaFormat.M4A, "fr-FR.4", "French (FR) original", AudioTrackType.ORIGINAL, Locale.FRANCE),
                stream(251, 160, trackId = "en-US.10", trackName = "English (US)", type = AudioTrackType.DUBBED, locale = Locale.US),
                stream(140, 128, MediaFormat.M4A, "fr-FR.4", "French (FR) original", AudioTrackType.ORIGINAL, Locale.FRANCE),
                stream(251, 160, trackId = "fr-FR.4", trackName = "French (FR) original", type = AudioTrackType.ORIGINAL, locale = Locale.FRANCE),
            ),
        )

        assertEquals(
            listOf(
                "French (FR) original" to "https://example.invalid/251/fr-FR.4",
                "English (US) (dubbed)" to "https://example.invalid/251/en-US.10",
            ),
            tracks.map { it.lang to it.url },
        )
    }

    @Test
    fun nonUrlStreamsAreSkipped() {
        val tracks = YouTubeResolver.selectAudioTracks(
            listOf(
                stream(251, 160, isUrl = false),
                stream(140, 128, MediaFormat.M4A),
            ),
        )

        assertEquals(listOf("https://example.invalid/140/x"), tracks.map { it.url })
    }
}
