package chimahon.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Verifies English (non-Japanese) paragraph/line reading order produced by [OwOCRMerger].
 *
 * Regression tests for a reported bug where phrases did not appear in the same order
 * as in the original image and text selection was misaligned. The underlying cause was
 * `mergeOverlappingLines` re-sorting horizontal lines by their left edge, which scrambled
 * multi-line paragraphs whose lines start at different horizontal positions.
 */
class OwOCRMergerEnglishOrderTest {

    private fun englishConfig() = MergeConfig(
        language = OcrLanguage.ENGLISH,
        furiganaFilter = false,
        mergeCloseParagraphs = true,
        supportCenterAlignedText = true,
        imageWidth = 1000.0,
        imageHeight = 1000.0,
    )

    private fun line(
        text: String,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
    ): EngineLine = EngineLine(
        text = text,
        bbox = NormalizedBBox(left, top, right, bottom),
        writingDirection = WritingDirection.LTR,
        language = OcrLanguage.ENGLISH,
    )

    @Test
    fun `lines overlapping a y-band are ordered top to bottom in reading order`() {
        // The upper line starts further right than the lower line. Sorting by the left
        // edge would place the lower line first; reading order must be top-to-bottom.
        val upper = line("BBBB", left = 0.14, top = 0.10, right = 0.44, bottom = 0.16)
        val lower = line("AAAA", left = 0.10, top = 0.20, right = 0.50, bottom = 0.26)

        val results = OwOCRMerger.merge(listOf(lower, upper), englishConfig())

        assertEquals(1, results.size)
        val paragraph = results.single()
        assertEquals("BBBB\nAAAA", paragraph.text)
        // Constituent boxes (line geometries) must follow the same order as the text lines.
        val firstBoxTop = paragraph.constituentBoxes!!.first().y
        val secondBoxTop = paragraph.constituentBoxes!![1].y
        assertEquals(true, firstBoxTop < secondBoxTop)
    }

    @Test
    fun `same-row side-by-side phrases stay as separate lines left to right`() {
        // Close enough that mergeCloseParagraphsLegacy combines them into one paragraph,
        // but the fragment-merge break keeps them as distinct lines in left-to-right order.
        val left = line("HELLO", left = 0.10, top = 0.10, right = 0.40, bottom = 0.16)
        val right = line("WORLD", left = 0.45, top = 0.10, right = 0.75, bottom = 0.16)

        val results = OwOCRMerger.merge(listOf(right, left), englishConfig())

        assertEquals(1, results.size)
        assertEquals("HELLO\nWORLD", results.single().text)
    }

    @Test
    fun `distinct bubbles far apart remain separate paragraphs`() {
        val bubble1 = line("HELLO", left = 0.10, top = 0.10, right = 0.30, bottom = 0.16)
        val bubble2 = line("WORLD", left = 0.50, top = 0.10, right = 0.70, bottom = 0.16)

        val results = OwOCRMerger.merge(listOf(bubble1, bubble2), englishConfig())

        assertEquals(2, results.size)
        assertEquals(listOf("HELLO", "WORLD"), results.map { it.text })
    }

    @Test
    fun `comic-style 2x2 grid reads row by row left to right`() {
        val topLeft = line("ONE", left = 0.10, top = 0.10, right = 0.30, bottom = 0.16)
        val topRight = line("TWO", left = 0.50, top = 0.10, right = 0.70, bottom = 0.16)
        val bottomLeft = line("THREE", left = 0.10, top = 0.40, right = 0.30, bottom = 0.46)
        val bottomRight = line("FOUR", left = 0.50, top = 0.40, right = 0.70, bottom = 0.46)

        val results = OwOCRMerger.merge(
            listOf(bottomRight, bottomLeft, topRight, topLeft),
            englishConfig(),
        )

        assertEquals(listOf("ONE", "TWO", "THREE", "FOUR"), results.map { it.text })
    }
}
