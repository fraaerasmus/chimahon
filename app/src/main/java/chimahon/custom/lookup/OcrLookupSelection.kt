package chimahon.custom.lookup

import chimahon.dictionary.LookupTextScanner
import chimahon.dictionary.LookupTextSelection
import chimahon.ocr.isLanguageWholeWordScan
import eu.kanade.tachiyomi.ui.reader.viewer.OcrTextBlock
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
