package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.ui.player.PlayerViewModel.VideoTrack
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference

class TrackSelectTest {

    private val tracks = listOf(
        VideoTrack(-1, "Off", null),
        VideoTrack(1, "French (FR) original", ""),
        VideoTrack(2, "English (US) (dubbed)", ""),
    )

    private fun trackSelect(audioLanguages: String = ""): TrackSelect {
        val store = InMemoryPreferenceStore(
            sequenceOf(InMemoryPreference("pref_audio_lang", audioLanguages, "")),
        )
        return TrackSelect(SubtitlePreferences(store), AudioPreferences(store))
    }

    @Test
    fun audioWithoutPreferenceLeavesTheChoiceToTheSource() {
        assertNull(trackSelect().getPreferredTrackIndex(tracks, subtitle = false))
    }

    @Test
    fun audioPreferenceStillMatchesByTitle() {
        val track = trackSelect("en").getPreferredTrackIndex(tracks, subtitle = false)

        assertEquals(2, track?.id)
    }
}
