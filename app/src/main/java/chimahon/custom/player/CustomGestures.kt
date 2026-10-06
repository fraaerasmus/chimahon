package chimahon.custom.player

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerInputScope
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The fork's gesture options as the gesture handler reads them. A new option is a field here and
 * its use in the handler; the handler collects nothing more.
 */
data class CustomGestureConfig(
    val longPress: LongPressGesture,
    val verticalSwipe: VerticalSwipeGesture,
    val seekSensitivityPercent: Int,
    val allowGesturesWhenLocked: Boolean,
) {
    val horizontalSeekSecondsPerPixel: Float
        get() = CustomGestures.horizontalSeekSecondsPerPixel(seekSensitivityPercent)

    /** Whether subtitle swipes also take vertical drags, leaving none for volume and brightness. */
    fun subtitleSwipesTakeVertical(subtitleSwipeControls: Boolean): Boolean =
        subtitleSwipeControls && verticalSwipe == VerticalSwipeGesture.SubtitleActions

    /** Upstream's screenshot switch only applies while long press takes screenshots. */
    fun longPressIsOff(screenshotDisabled: Boolean): Boolean =
        longPress == LongPressGesture.Screenshot && screenshotDisabled
}

/**
 * The current options as state, so a gesture detector that was started once still reads the
 * latest values.
 */
@Composable
fun rememberCustomGestureConfig(): State<CustomGestureConfig> {
    val preferences = remember { Injekt.get<CustomGesturePreferences>() }
    val longPress by preferences.longPressGesture().collectAsState()
    val verticalSwipe by preferences.subtitleSwipeVertical().collectAsState()
    val seekSensitivity by preferences.horizontalSeekSensitivity().collectAsState()
    val allowGesturesWhenLocked by preferences.allowGesturesWhenLocked().collectAsState()
    return rememberUpdatedState(
        CustomGestureConfig(longPress, verticalSwipe, seekSensitivity, allowGesturesWhenLocked),
    )
}

/** Gesture pieces the fork adds to the player's gesture handler. */
object CustomGestures {
    private const val HORIZONTAL_SEEK_SECONDS_PER_PIXEL = 0.15f

    /**
     * Seconds a horizontal swipe seeks for each pixel travelled, scaled by the user's sensitivity.
     */
    fun horizontalSeekSecondsPerPixel(sensitivityPercent: Int): Float {
        return HORIZONTAL_SEEK_SECONDS_PER_PIXEL * sensitivityPercent / 100f
    }

    /**
     * Subtitle swipes when vertical swipes belong to volume and brightness: only horizontal drags
     * are claimed. [onSwipe] gets the horizontal distance of a drag that ended within
     * [timeLimitMillis].
     */
    suspend fun PointerInputScope.detectQuickHorizontalSwipe(
        timeLimitMillis: Long,
        onSwipe: (totalDragX: Float) -> Unit,
    ) {
        var startedAt = 0L
        var totalDragX = 0f
        detectHorizontalDragGestures(
            onDragStart = {
                startedAt = SystemClock.uptimeMillis()
                totalDragX = 0f
            },
            onDragEnd = {
                if (SystemClock.uptimeMillis() - startedAt > timeLimitMillis) {
                    return@detectHorizontalDragGestures
                }
                onSwipe(totalDragX)
            },
            onDragCancel = { totalDragX = 0f },
        ) { change, dragAmount ->
            totalDragX += dragAmount
            change.consume()
        }
    }
}
