package eu.kanade.tachiyomi.ui.player.controls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubtitleSwipeActionTest {

    @Test
    fun `swipe up replays the current subtitle`() {
        assertEquals(SubtitleSwipeAction.ReplayCurrent, resolveSubtitleSwipeAction(0f, -31f, 30f))
    }

    @Test
    fun `swipe down toggles subtitle visibility`() {
        assertEquals(SubtitleSwipeAction.ToggleVisibility, resolveSubtitleSwipeAction(0f, 31f, 30f))
    }

    @Test
    fun `horizontal swipes move between subtitle cues`() {
        assertEquals(SubtitleSwipeAction.Previous, resolveSubtitleSwipeAction(-31f, 0f, 30f))
        assertEquals(SubtitleSwipeAction.Next, resolveSubtitleSwipeAction(31f, 0f, 30f))
    }

    @Test
    fun `drags tracked on the horizontal axis only never replay or hide`() {
        assertEquals(SubtitleSwipeAction.Previous, resolveSubtitleSwipeAction(-200f, 0f, 30f))
        assertEquals(SubtitleSwipeAction.Next, resolveSubtitleSwipeAction(200f, 0f, 30f))
        assertNull(resolveSubtitleSwipeAction(0f, 0f, 30f))
    }

    @Test
    fun `seek sensitivity scales the stock seconds per pixel`() {
        assertEquals(0.15f, horizontalSeekSecondsPerPixel(100), 0.0001f)
        assertEquals(0.015f, horizontalSeekSecondsPerPixel(10), 0.0001f)
        assertEquals(0.45f, horizontalSeekSecondsPerPixel(300), 0.0001f)
    }

    @Test
    fun `a swipe seeks further at a higher sensitivity`() {
        val slow = calculateNewHorizontalGestureValue(60, 0f, 400f, horizontalSeekSecondsPerPixel(50))
        val fast = calculateNewHorizontalGestureValue(60, 0f, 400f, horizontalSeekSecondsPerPixel(200))

        assertEquals(90, slow)
        assertEquals(180, fast)
    }

    @Test
    fun `diagonal and short swipes have no subtitle action`() {
        assertNull(resolveSubtitleSwipeAction(30f, 30f, 30f))
        assertNull(resolveSubtitleSwipeAction(0f, 29f, 30f))
    }
}
