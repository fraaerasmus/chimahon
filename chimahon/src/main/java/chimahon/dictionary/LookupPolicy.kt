package chimahon.dictionary

import chimahon.LookupResult

data class LookupHighlight(
    val startOffset: Int,
    val codePointCount: Int,
)

/** The one place that says which language a code such as `fr-CA` or `FR_fr` stands for. */
object LookupLanguage {
    fun primary(languageCode: String): String =
        languageCode.trim().lowercase().substringBefore('-').substringBefore('_')

    /** French lookups scan from the start of the tapped word and across the words after it. */
    fun isFrench(languageCode: String): Boolean = primary(languageCode) == "fr"
}

/** How lookup results are ordered and highlighted, on top of what [DeinflectedLookup] returns. */
object LookupPolicy {
    private val whitespace = Regex("\\s+")

    /**
     * For French, entries that only say "form of another word" go below real definitions of the
     * same match. Other languages keep the order they came in.
     */
    fun rank(
        results: List<LookupResult>,
        languageCode: String,
        maxResults: Int,
    ): List<LookupResult> {
        if (!LookupLanguage.isFrench(languageCode)) {
            return results.distinctBy { it.term.expression to it.term.reading }.take(maxResults)
        }
        return results.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<LookupResult>> { matchedCodePointCount(it.value) }
                    .thenBy { if (isFormOfOnly(it.value)) 1 else 0 }
                    .thenBy { it.index },
            )
            .map { it.value }
            .distinctBy { Triple(it.term.expression, it.term.reading, it.matched) }
            .take(maxResults)
    }

    fun isFormOfOnly(result: LookupResult): Boolean =
        result.term.glossaries.isNotEmpty() &&
            result.term.glossaries.all { glossary ->
                glossary.definitionTags.split(whitespace).any { it == "non-lemma" }
            }

    fun formOfOnlyRank(result: LookupResult, languageCode: String): Int =
        if (LookupLanguage.isFrench(languageCode) && isFormOfOnly(result)) 1 else 0

    fun matchedCodePointCount(result: LookupResult): Int =
        result.matched.codePointCount(0, result.matched.length)

    /** The highlight runs from the start of the selection to the end of the match in it. */
    fun highlightFor(selectionText: String, matched: String): LookupHighlight {
        val matchStart = selectionText.indexOf(matched)
        val highlightEnd = if (matchStart >= 0) matchStart + matched.length else matched.length
        val safeEnd = highlightEnd.coerceIn(0, selectionText.length)
        return LookupHighlight(
            startOffset = 0,
            codePointCount = selectionText.codePointCount(0, safeEnd),
        )
    }
}
