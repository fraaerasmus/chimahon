package eu.kanade.tachiyomi.ui.player.settings

import chimahon.custom.player.VerticalSwipeGesture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class GesturePreferencesTest {

    @Test
    fun verticalSwipeKeepsSubtitleActionsByDefault() {
        val preference = GesturePreferences(InMemoryPreferenceStore()).subtitleSwipeVertical()

        assertEquals("pref_subtitle_swipe_vertical", preference.key())
        assertEquals(VerticalSwipeGesture.SubtitleActions, preference.defaultValue())
    }

    @Test
    fun seekSwipeKeepsTheStockDistanceByDefault() {
        val preference = GesturePreferences(InMemoryPreferenceStore()).horizontalSeekSensitivity()

        assertEquals("pref_horizontal_seek_sensitivity", preference.key())
        assertEquals(100, preference.defaultValue())
    }

    @Test
    fun gesturesStayLockedByDefault() {
        val preference = PlayerPreferences(InMemoryPreferenceStore()).allowGesturesWhenLocked()

        assertEquals("pref_allow_gestures_when_locked", preference.key())
        assertEquals(false, preference.defaultValue())
    }
}
