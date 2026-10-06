package chimahon.custom.player

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.PointerInputScope

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
