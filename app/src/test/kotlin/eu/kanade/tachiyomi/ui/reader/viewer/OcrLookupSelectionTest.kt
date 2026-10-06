package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OcrLookupSelectionTest {
    // A block's text joins its lines without a separator: "je pars avectoi demain".
    private val block = OcrTextBlock(
        xmin = 0f,
        ymin = 0f,
        xmax = 1f,
        ymax = 1f,
        lines = listOf("je pars avec", "toi demain"),
    )

    @Test
    fun `line starts are where each later line begins in the block text`() {
        assertEquals(setOf(12), block.lineStartOffsets())
    }

    @Test
    fun `a French tap stays inside the tapped line when the line starts are passed`() {
        val tapOnToi = 13

        val selection = extractOcrLookupSelection(block.fullText, tapOnToi, "fr", block.lineStartOffsets())

        assertEquals("toi demain", selection?.text)
        assertEquals(12, selection?.startOffset)
    }

    @Test
    fun `without the line starts the same tap glues in the previous line's last word`() {
        val tapOnToi = 13

        val selection = extractOcrLookupSelection(block.fullText, tapOnToi, "fr")

        assertEquals(8, selection?.startOffset)
    }
}
