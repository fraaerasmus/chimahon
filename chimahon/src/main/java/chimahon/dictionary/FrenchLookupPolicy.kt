package chimahon.dictionary

import chimahon.LookupResult

data class LookupHighlight(
    val startOffset: Int,
    val codePointCount: Int,
)

object FrenchLookupPolicy {
    private val frenchElision = Regex("^(?:l|d|j|m|t|s|n|c|qu)['’]", RegexOption.IGNORE_CASE)
    private val recursivePunctuation = Regex("[\\s\\p{Punct}「」『』【】（）〔〕［］｛｝〈〉《》…、。！？!?]+")

    fun lookupQueries(text: String, languageCode: String): List<String> {
        val stripped = stripElision(text, languageCode) ?: return listOf(text)
        return listOf(text, stripped).distinct()
    }

    fun stripElision(text: String, languageCode: String): String? {
        if (languageCode.primaryLanguage() != "fr") return null
        val match = frenchElision.find(text) ?: return null
        return text.substring(match.value.length).takeIf { it.isNotEmpty() }
    }

    fun recursiveQuery(text: String, languageCode: String): String? {
        if (languageCode.primaryLanguage() == "fr") return text.trim().takeIf { it.isNotEmpty() }
        val cleaned = text.replace(recursivePunctuation, "").trim()
        return cleaned.takeIf { it.isNotEmpty() && it.any { char -> char.code > 127 } }
    }

    fun mergeResults(
        results: List<LookupResult>,
        languageCode: String,
        maxResults: Int,
    ): List<LookupResult> {
        if (languageCode.primaryLanguage() != "fr") {
            return results.distinctBy { it.term.expression to it.term.reading }.take(maxResults)
        }
        return results.withIndex()
            .sortedWith(
                compareBy<IndexedValue<LookupResult>> { if (isFormOfOnly(it.value)) 1 else 0 }
                    .thenByDescending { matchedCodePointCount(it.value) }
                    .thenBy { it.index },
            )
            .map { it.value }
            .distinctBy { Triple(it.term.expression, it.term.reading, it.matched) }
            .take(maxResults)
    }

    fun isFormOfOnly(result: LookupResult): Boolean =
        result.term.glossaries.isNotEmpty() &&
            result.term.glossaries.all { glossary ->
                glossary.definitionTags.split(Regex("\\s+")).any { it == "non-lemma" }
            }

    fun formOfOnlyRank(result: LookupResult, languageCode: String): Int =
        if (languageCode.primaryLanguage() == "fr" && isFormOfOnly(result)) 1 else 0

    fun matchedCodePointCount(result: LookupResult): Int =
        result.matched.codePointCount(0, result.matched.length)

    fun highlightFor(selectionText: String, matched: String): LookupHighlight {
        val matchStart = selectionText.indexOf(matched)
        val highlightEnd = if (matchStart >= 0) matchStart + matched.length else matched.length
        val safeEnd = highlightEnd.coerceIn(0, selectionText.length)
        return LookupHighlight(
            startOffset = 0,
            codePointCount = selectionText.codePointCount(0, safeEnd),
        )
    }

    private fun String.primaryLanguage(): String =
        trim().lowercase().substringBefore('-').substringBefore('_')
}
