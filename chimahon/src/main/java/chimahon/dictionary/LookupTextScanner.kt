package chimahon.dictionary

data class LookupTextSelection(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val tapOffset: Int,
)

object LookupTextScanner {
    private val frenchElisionClitics = setOf("l", "d", "j", "m", "t", "s", "n", "c", "qu")

    fun scan(
        text: String,
        tapOffset: Int,
        languageCode: String,
        scanAcrossSpaces: Boolean = false,
        maxCodePoints: Int = Int.MAX_VALUE,
    ): LookupTextSelection? {
        if (text.isEmpty() || maxCodePoints <= 0) return null
        val normalizedTap = codePointStartOffset(text, tapOffset)
        if (normalizedTap !in text.indices || !isLookupCodePoint(text.codePointAt(normalizedTap))) return null

        val isFrench = languageCode.primaryLanguage() == "fr"
        val start = if (isFrench) findFrenchWordStart(text, normalizedTap) else normalizedTap
        val end = findScanEnd(
            text = text,
            start = start,
            isFrench = isFrench,
            scanAcrossSpaces = isFrench && scanAcrossSpaces,
            maxCodePoints = maxCodePoints,
        )
        if (end <= start) return null

        return LookupTextSelection(
            text = text.substring(start, end),
            startOffset = start,
            endOffset = end,
            tapOffset = normalizedTap,
        )
    }

    private fun findFrenchWordStart(text: String, tapOffset: Int): Int {
        var start = tapOffset
        while (start > 0) {
            val previous = previousCodePointStartOffset(text, start)
            val codePoint = text.codePointAt(previous)
            when {
                isFrenchWordCodePoint(codePoint) -> start = previous
                isWordInternalDelimiter(text, previous) -> {
                    if (isApostrophe(codePoint) && isFrenchElisionApostrophe(text, previous)) break
                    start = previous
                }
                else -> break
            }
        }
        return start
    }

    private fun findScanEnd(
        text: String,
        start: Int,
        isFrench: Boolean,
        scanAcrossSpaces: Boolean,
        maxCodePoints: Int,
    ): Int {
        var end = start
        var count = 0
        while (end < text.length && count < maxCodePoints) {
            val codePoint = text.codePointAt(end)
            when {
                isFrench && isFrenchWordCodePoint(codePoint) -> {
                    end += Character.charCount(codePoint)
                    count++
                }
                isFrench && isWordInternalDelimiter(text, end) -> {
                    end += Character.charCount(codePoint)
                    count++
                }
                isFrench && scanAcrossSpaces && codePoint == ' '.code -> {
                    var next = end
                    var spaces = 0
                    while (next < text.length && text.codePointAt(next) == ' '.code) {
                        next++
                        spaces++
                    }
                    if (next >= text.length || !isFrenchWordCodePoint(text.codePointAt(next))) break
                    if (count + spaces > maxCodePoints) break
                    end = next
                    count += spaces
                }
                !isFrench && isLookupCodePoint(codePoint) -> {
                    end += Character.charCount(codePoint)
                    count++
                }
                else -> break
            }
        }
        return end
    }

    private fun isFrenchElisionApostrophe(text: String, offset: Int): Boolean {
        val codePoint = text.codePointAt(offset)
        if (!isApostrophe(codePoint)) return false
        val previous = previousCodePointStartOffset(text, offset)
        val next = offset + Character.charCount(codePoint)
        if (previous == offset || next >= text.length) return false
        if (!isFrenchWordCodePoint(text.codePointAt(previous)) || !isFrenchWordCodePoint(text.codePointAt(next))) {
            return false
        }

        var cliticStart = offset
        while (cliticStart > 0) {
            val candidate = previousCodePointStartOffset(text, cliticStart)
            if (!isFrenchWordCodePoint(text.codePointAt(candidate))) break
            cliticStart = candidate
        }
        return text.substring(cliticStart, offset).lowercase() in frenchElisionClitics
    }

    private fun isWordInternalDelimiter(text: String, offset: Int): Boolean {
        val codePoint = text.codePointAt(offset)
        if (!isApostrophe(codePoint) && codePoint !in internalHyphens) return false
        val previous = previousCodePointStartOffset(text, offset)
        val next = offset + Character.charCount(codePoint)
        return previous != offset &&
            next < text.length &&
            isFrenchWordCodePoint(text.codePointAt(previous)) &&
            isFrenchWordCodePoint(text.codePointAt(next))
    }

    private fun isFrenchWordCodePoint(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return Character.isLetterOrDigit(codePoint) ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt()
    }

    private fun isLookupCodePoint(codePoint: Int): Boolean {
        if (Character.isWhitespace(codePoint)) return false
        return Character.getType(codePoint) !in punctuationAndSymbolTypes
    }

    private fun codePointStartOffset(text: String, offset: Int): Int {
        val coerced = offset.coerceIn(0, text.length)
        return if (
            coerced in 1 until text.length &&
            Character.isLowSurrogate(text[coerced]) &&
            Character.isHighSurrogate(text[coerced - 1])
        ) {
            coerced - 1
        } else {
            coerced
        }
    }

    private fun previousCodePointStartOffset(text: String, offset: Int): Int {
        if (offset <= 0) return 0
        return codePointStartOffset(text, offset - 1)
    }

    private fun isApostrophe(codePoint: Int): Boolean = codePoint == '\''.code || codePoint == '\u2019'.code

    private fun String.primaryLanguage(): String =
        trim().lowercase().substringBefore('-').substringBefore('_')

    private val internalHyphens = setOf('-'.code, '\u2010'.code, '\u2011'.code)

    private val punctuationAndSymbolTypes = setOf(
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        Character.MATH_SYMBOL.toInt(),
        Character.CURRENCY_SYMBOL.toInt(),
        Character.MODIFIER_SYMBOL.toInt(),
        Character.OTHER_SYMBOL.toInt(),
    )
}
