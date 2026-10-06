package chimahon.custom.lookup

import chimahon.dictionary.LookupTextScanner
import chimahon.dictionary.LookupTextSelection
import chimahon.ocr.isLanguageWholeWordScan
import eu.kanade.tachiyomi.ui.reader.viewer.OcrTextBlock
import eu.kanade.tachiyomi.ui.reader.viewer.fullText
import eu.kanade.tachiyomi.ui.reader.viewer.orderedFullText
import eu.kanade.tachiyomi.ui.reader.viewer.orderedLineIndices

/**
 * The text a tap on OCR or subtitle text looks up, starting at the beginning of the tapped word
 * for the languages that need it.
 *
 * [lineBreaks] are the offsets in [text] where an OCR line starts. For
 * space-delimited languages they stop the scan from running into a neighboring
 * line (block text concatenates lines without a separator); CJK scans ignore
 * them because words there legitimately wrap across lines.
 */
internal fun extractOcrLookupSelection(
    text: String,
    start: Int,
    languageCode: String,
    lineBreaks: Set<Int> = emptySet(),
): LookupTextSelection? = LookupTextScanner.scan(
    text = text,
    tapOffset = start,
    languageCode = languageCode,
    scanAcrossSpaces = true,
    maxCodePoints = 80,
    lineBreaks = if (isLanguageWholeWordScan(languageCode)) lineBreaks else emptySet(),
)

/**
 * The lookup a tap on an OCR block starts, with the scan kept inside the tapped line. Upstream's
 * tap handlers call these by their full name, so they need no import.
 */
object OcrLookup {
    /** For a tap at [offset] of the block's `fullText`. */
    fun selectionAt(block: OcrTextBlock, offset: Int, languageCode: String): LookupTextSelection? =
        extractOcrLookupSelection(block.fullText, offset, languageCode, block.lineStartOffsets())

    /** For a tap at [offset] of the block's `orderedFullText`, the text in reading order. */
    fun orderedSelectionAt(block: OcrTextBlock, offset: Int, languageCode: String): LookupTextSelection? =
        extractOcrLookupSelection(block.orderedFullText, offset, languageCode, block.orderedLineStartOffsets())
}

/** Offsets in the block's `fullText` where each line after the first begins. */
internal fun OcrTextBlock.lineStartOffsets(): Set<Int> = lineStartOffsets(lines)

/** Offsets in the block's `orderedFullText` where each line after the first begins. */
internal fun OcrTextBlock.orderedLineStartOffsets(): Set<Int> =
    lineStartOffsets(orderedLineIndices().map { lines[it] })

private fun lineStartOffsets(lines: List<String>): Set<Int> {
    val offsets = mutableSetOf<Int>()
    var start = 0
    for (i in 0 until lines.lastIndex) {
        start += lines[i].length
        offsets += start
    }
    return offsets
}
