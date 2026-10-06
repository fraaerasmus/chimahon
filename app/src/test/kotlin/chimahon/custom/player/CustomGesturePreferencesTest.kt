package chimahon.custom.player

import chimahon.custom.core.FakePreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomGesturePreferencesTest {
    private val preferences = CustomGesturePreferences(FakePreferenceStore())

    // These options were first stored from inside upstream's preference classes. The keys must not
    // change, or everyone's gesture settings fall back to the defaults.
    @Test
    fun `the options keep the keys they were first stored under`() {
        assertEquals("pref_long_press_gesture", preferences.longPressGesture().key())
        assertEquals("pref_subtitle_swipe_vertical", preferences.subtitleSwipeVertical().key())
        assertEquals("pref_horizontal_seek_sensitivity", preferences.horizontalSeekSensitivity().key())
        assertEquals("pref_allow_gestures_when_locked", preferences.allowGesturesWhenLocked().key())
    }

    @Test
    fun `out of the box the player behaves as upstream's does`() {
        assertEquals(LongPressGesture.Screenshot, preferences.longPressGesture().get())
        assertEquals(VerticalSwipeGesture.SubtitleActions, preferences.subtitleSwipeVertical().get())
        assertEquals(100, preferences.horizontalSeekSensitivity().get())
        assertEquals(false, preferences.allowGesturesWhenLocked().get())
    }

    @Test
    fun `the config tells the handler what a swipe and a long press mean`() {
        val stock = CustomGestureConfig(LongPressGesture.Screenshot, VerticalSwipeGesture.SubtitleActions, 100, false)
        assertEquals(true, stock.subtitleSwipesTakeVertical(subtitleSwipeControls = true))
        assertEquals(false, stock.subtitleSwipesTakeVertical(subtitleSwipeControls = false))
        assertEquals(true, stock.longPressIsOff(screenshotDisabled = true))
        assertEquals(false, stock.longPressIsOff(screenshotDisabled = false))

        val changed = stock.copy(longPress = LongPressGesture.DoubleSpeed, verticalSwipe = VerticalSwipeGesture.VolumeBrightness)
        assertEquals(false, changed.subtitleSwipesTakeVertical(subtitleSwipeControls = true))
        // Hold for 2x ignores the screenshot switch.
        assertEquals(false, changed.longPressIsOff(screenshotDisabled = true))
        assertEquals(2 * stock.horizontalSeekSecondsPerPixel, stock.copy(seekSensitivityPercent = 200).horizontalSeekSecondsPerPixel)
    }
}
