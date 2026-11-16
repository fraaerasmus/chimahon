package chimahon.custom.player

import androidx.compose.ui.geometry.Offset
import eu.kanade.tachiyomi.ui.player.controls.NativeSubtitleEvent
import eu.kanade.tachiyomi.ui.player.controls.NativeSubtitleGeometry
import eu.kanade.tachiyomi.ui.player.controls.NativeSubtitleSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NativeSubtitleLookupTest {
    // One 10 x 20 box per character, laid out left to right from (0, 100), in a 200 x 200 frame.
    private fun event(id: Int, text: String) = NativeSubtitleEvent(
        id = id,
        text = text,
        start = 1.0,
        end = 2.0,
        spans = text.indices.map { NativeSubtitleSpan(start = it, end = it + 1, x = it * 10, y = 100, w = 10, h = 20) },
    )

    private val line = event(1, "toi demain")
    private val geometry = NativeSubtitleGeometry(version = 1, w = 200, h = 200, events = listOf(event(0, "sign"), line))

    @Test
    fun `the cursor starts on the line mpv drew last`() {
        assertEquals("toi demain", geometry.primaryLine()?.text)
    }

    @Test
    fun `a character's point is the middle of its box, scaled to the view`() {
        // The view is twice the size of mpv's frame.
        assertEquals(Offset(45f * 2, 110f * 2), geometry.pointAt(line, 4, 400, 400))
        assertNull(geometry.pointAt(line, 99, 400, 400))
    }

    @Test
    fun `the point of a character is looked up as that character`() {
        val point = geometry.pointAt(line, 4, 200, 200)!!

        val request = geometry.lookupAt(point.x, point.y, 200, 200, "sub-text-geometry")

        assertEquals(4, request?.tapCharOffset)
    }

    @Test
    fun `a French lookup starts at the beginning of the tapped word`() {
        val tapOnMain = geometry.pointAt(line, 7, 200, 200)!!
        val request = geometry.lookupAt(tapOnMain.x, tapOnMain.y, 200, 200, "sub-text-geometry")!!

        val lookup = NativeSubtitleLookup.startingAtWord(request, "fr")

        assertEquals(4, lookup?.charOffset)
        assertEquals(7, lookup?.tapCharOffset)
        assertEquals("demain", lookup?.lookupString)
    }

    @Test
    fun `the cursor highlight covers its word in view coordinates`() {
        val highlight = geometry.highlight(line, start = 4, length = 6, width = 200, height = 200)!!

        assertEquals(4, highlight.charOffset)
        assertEquals(6, highlight.matchedCharCount)
        assertEquals(line.text.length, highlight.nativeRectangles.size)
        assertEquals("", highlight.lookupString)
    }
}
