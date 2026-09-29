package chimahon.keybinding

import chimahon.dictionary.FrenchLookupPolicy
import chimahon.dictionary.LookupTextScanner
import chimahon.ocr.isLanguageWholeWordScan

/** A word of a subtitle, as a range of characters in its text. */
data class CursorWord(val start: Int, val length: Int)

/** The word picked out of a subtitle with the keys. */
data class WordCursor(
    val text: String,
    val words: List<CursorWord>,
    val index: Int,
    /** Whether the player was paused before the cursor paused it, so it is left that way after. */
    val wasPaused: Boolean,
) {
    val word get() = words[index]

    fun moved(by: Int) = copy(index = (index + by).coerceIn(0, words.lastIndex))

    companion object {
        /** A cursor on the first of [words], or null when there are none. */
        fun start(text: String, words: List<CursorWord>, wasPaused: Boolean): WordCursor? {
            return if (words.isEmpty()) null else WordCursor(text, words, index = 0, wasPaused = wasPaused)
        }
    }
}

/**
 * The words of [text] a cursor steps through. Each starts where the last one ended, and is as long
 * as the dictionary's best match there, the way a tap on that character would look it up.
 *
 * [measure] takes the text from a word's start on and gives back the part the dictionary matched,
 * or null. A spot it does not know is a whole word in a spaced language and one character otherwise.
 */
fun cursorWords(text: String, languageCode: String, measure: (String) -> String?): List<CursorWord> {
    val words = mutableListOf<CursorWord>()
    var offset = 0
    while (offset < text.length) {
        val oneCharacter = Character.charCount(text.codePointAt(offset))
        val scanEnd = scanEnd(text, offset, languageCode, acrossSpaces = true)
        if (scanEnd == null) {
            offset += oneCharacter
            continue
        }

        val query = text.substring(offset, scanEnd)
        val matched = measure(query)
        val length = when {
            !matched.isNullOrEmpty() -> {
                val matchedCodePoints = FrenchLookupPolicy.highlightFor(query, matched).codePointCount
                query.offsetByCodePoints(0, matchedCodePoints)
            }
            isLanguageWholeWordScan(languageCode) ->
                scanEnd(text, offset, languageCode, acrossSpaces = false)!! - offset
            else -> oneCharacter
        }.coerceAtLeast(oneCharacter)

        words += CursorWord(offset, length)
        offset += length
    }
    return words
}

/** Where a lookup from [offset] stops reading, or null when there is nothing to look up there. */
private fun scanEnd(text: String, offset: Int, languageCode: String, acrossSpaces: Boolean): Int? {
    val selection = LookupTextScanner.scan(
        text = text,
        tapOffset = offset,
        languageCode = languageCode,
        scanAcrossSpaces = acrossSpaces,
        maxCodePoints = 80,
    )
    // French scans from the start of the word, which is behind an offset in its middle.
    return selection?.endOffset?.takeIf { it > offset }
}
