package chimahon.custom.player

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * The gesture options the fork adds. They are stored under the keys they had while they sat
 * inside upstream's `GesturePreferences` and `PlayerPreferences`, so those two classes stay
 * identical to upstream and nobody's settings move.
 */
class CustomGesturePreferences(private val store: PreferenceStore) {

    fun longPressGesture() = store.getEnum("pref_long_press_gesture", LongPressGesture.Screenshot)

    fun subtitleSwipeVertical() = store.getEnum("pref_subtitle_swipe_vertical", VerticalSwipeGesture.SubtitleActions)

    /** Percent of the stock distance a horizontal swipe seeks. */
    fun horizontalSeekSensitivity() = store.getInt("pref_horizontal_seek_sensitivity", 100)

    fun allowGesturesWhenLocked() = store.getBoolean("pref_allow_gestures_when_locked", false)
}
