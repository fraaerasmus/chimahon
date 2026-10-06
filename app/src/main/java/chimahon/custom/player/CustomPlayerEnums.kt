package chimahon.custom.player

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

/**
 * Action performed on long press
 */
enum class LongPressGesture(val stringRes: StringResource) {
    Screenshot(stringRes = MR.strings.long_press_screenshot),
    DoubleSpeed(stringRes = MR.strings.long_press_double_speed),
}

/**
 * Action performed by vertical swipes while subtitle swipe controls are enabled
 */
enum class VerticalSwipeGesture(val stringRes: StringResource) {
    SubtitleActions(stringRes = MR.strings.vertical_swipe_subtitle_actions),
    VolumeBrightness(stringRes = MR.strings.vertical_swipe_volume_brightness),
}
