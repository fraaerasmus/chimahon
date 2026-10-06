package chimahon.custom.player

import eu.kanade.tachiyomi.ui.player.controls.SubtitleSwipeAction
import eu.kanade.tachiyomi.ui.player.controls.calculateNewHorizontalGestureValue
import eu.kanade.tachiyomi.ui.player.controls.resolveSubtitleSwipeAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CustomGesturesTest {
    @Test
    fun `drags tracked on the horizontal axis only never replay or hide`() {
        assertEquals(SubtitleSwipeAction.Previous, resolveSubtitleSwipeAction(-200f, 0f, 30f))
        assertEquals(SubtitleSwipeAction.Next, resolveSubtitleSwipeAction(200f, 0f, 30f))
        assertNull(resolveSubtitleSwipeAction(0f, 0f, 30f))
    }

    @Test
    fun `seek sensitivity scales the stock seconds per pixel`() {
        assertEquals(0.15f, CustomGestures.horizontalSeekSecondsPerPixel(100), 0.0001f)
        assertEquals(0.015f, CustomGestures.horizontalSeekSecondsPerPixel(10), 0.0001f)
        assertEquals(0.45f, CustomGestures.horizontalSeekSecondsPerPixel(300), 0.0001f)
    }

    @Test
    fun `a swipe seeks further at a higher sensitivity`() {
        val slow = calculateNewHorizontalGestureValue(60, 0f, 400f, CustomGestures.horizontalSeekSecondsPerPixel(50))
        val fast = calculateNewHorizontalGestureValue(60, 0f, 400f, CustomGestures.horizontalSeekSecondsPerPixel(200))

        assertEquals(90, slow)
        assertEquals(180, fast)
    }
}
