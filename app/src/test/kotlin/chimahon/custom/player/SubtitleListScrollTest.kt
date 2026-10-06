package chimahon.custom.player

import eu.kanade.tachiyomi.ui.player.PlayerViewModel.SubtitleCue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubtitleListScrollTest {

    // A 1000px list with 500px of content padding above and below: offsets run from -500 to 500.
    private val viewportStart = -500
    private val viewportEnd = 500

    @Test
    fun `an item in the middle of the viewport needs no scroll`() {
        assertEquals(0, centeredScrollDelta(-40, 80, viewportStart, viewportEnd))
    }

    @Test
    fun `the first item is scrolled up from the end of the top padding`() {
        assertEquals(40, centeredScrollDelta(0, 80, viewportStart, viewportEnd))
    }

    @Test
    fun `an item on the bottom edge is scrolled fully into view`() {
        // Where the viewport height used to put the active line: half of it below the bottom edge.
        assertEquals(500, centeredScrollDelta(460, 80, viewportStart, viewportEnd))
    }

    @Test
    fun `an item above the middle is scrolled back down`() {
        assertEquals(-200, centeredScrollDelta(-240, 80, viewportStart, viewportEnd))
    }

    @Test
    fun `opening between lines lands on the last line that started`() {
        val cues = listOf(cue(0, 10.0), cue(1, 20.0), cue(2, 30.0))

        assertEquals(1, fallbackPosition(cues, 25.0))
        assertEquals(2, fallbackPosition(cues, 90.0))
    }

    @Test
    fun `opening before the first line lands on it`() {
        assertEquals(0, fallbackPosition(listOf(cue(0, 10.0), cue(1, 20.0)), 3.0))
    }

    private fun cue(index: Int, positionSeconds: Double) = SubtitleCue(
        index = index,
        text = "line $index",
        positionSeconds = positionSeconds,
    )
}
