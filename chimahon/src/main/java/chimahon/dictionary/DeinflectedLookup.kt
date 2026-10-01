package chimahon.dictionary

import chimahon.LookupResult
import chimahon.TermResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Prefix lookup for languages deinflected in Kotlin. Every prefix of the text is deinflected
 * and each candidate is resolved through [query] (an exact-match dictionary query).
 */
object DeinflectedLookup {

    private data class Hit(val result: LookupResult, val steps: Int, val index: Int)

    fun lookup(
        text: String,
        deinflector: Deinflector,
        languageCode: String,
        maxResults: Int,
        query: (String) -> List<TermResult>,
    ): List<LookupResult> {
        val hits = mutableListOf<Hit>()
        for (i in text.length downTo 1) {
            val substring = text.substring(0, i)
            // One text can be reached through several rules, each with its own part of speech.
            val candidates = linkedMapOf<String, MutableList<DeinflectionResult>>()
            for (preprocessed in deinflector.preProcess(substring).distinct()) {
                for (deinflected in deinflector.deinflect(preprocessed, languageCode)) {
                    candidates.getOrPut(deinflected.text) { mutableListOf() } += deinflected
                }
            }

            // Lemmas the dictionary itself points at ("vendu" -> "vendre"), for forms the rules miss.
            val targets = linkedMapOf<String, Int>()
            fun addHits(candidateText: String, variants: List<DeinflectionResult>) {
                for (term in query(candidateText)) {
                    val steps = variants
                        .filter { matchesPartOfSpeech(it.conditions, term.rules) }
                        .minOfOrNull { it.steps }
                        ?: continue
                    val result = LookupResult(
                        matched = substring,
                        deinflected = candidateText,
                        process = emptyArray(),
                        term = term,
                        preprocessorSteps = 0,
                    )
                    hits += Hit(result, steps, hits.size)
                    formOfTargets(term).forEach { targets.putIfAbsent(it, steps + 1) }
                }
            }

            candidates.forEach { (candidateText, variants) -> addHits(candidateText, variants) }
            targets.entries.toList()
                .filter { it.key !in candidates }
                .forEach { addHits(it.key, listOf(DeinflectionResult(it.key, 0, steps = it.value))) }
        }

        return hits.sortedWith(hitOrder)
            .map { it.result }
            .distinctBy { it.term.expression to it.term.reading }
            .take(maxResults)
    }

    /**
     * False when a deinflection rule for one part of speech lands on an entry of another,
     * e.g. the verb rule "ent" -> "er" turning "ment" into the noun "mer". Entries without
     * a part of speech the rules know about are kept.
     */
    fun matchesPartOfSpeech(conditions: Set<String>, termRules: String): Boolean {
        if (conditions.isEmpty()) return true
        val entryConditions = termRules.split(whitespace).filter { it in conditionHierarchy }
        if (entryConditions.isEmpty()) return true
        val expanded = conditions + conditions.flatMap { conditionHierarchy[it].orEmpty() }
        return entryConditions.any { it in expanded }
    }

    /** Lemmas named by Yomitan deinflection glossaries: `[uninflectedTerm, inflectionRule[]]`. */
    fun formOfTargets(term: TermResult): List<String> {
        return term.glossaries
            .asSequence()
            .map { it.glossary.trim() }
            .filter { it.startsWith("[") }
            .flatMap { glossary ->
                val root = runCatching { Json.parseToJsonElement(glossary) }.getOrNull()
                when {
                    root !is JsonArray -> emptyList()
                    root.isFormOf() -> listOf(root.formOfTarget())
                    else -> root.filter { it.isFormOf() }.map { it.formOfTarget() }
                }
            }
            .filter { it.isNotBlank() && it != term.expression }
            .distinct()
            .toList()
    }

    // Longest match, then fewest deinflection steps, exact headword, most frequent, lookup order.
    private val hitOrder = compareByDescending<Hit> { FrenchLookupPolicy.matchedCodePointCount(it.result) }
        .thenBy { it.steps }
        .thenBy { if (it.result.term.expression == it.result.deinflected) 0 else 1 }
        .thenBy { frequencyRank(it.result.term) }
        .thenBy { it.index }

    private fun frequencyRank(term: TermResult): Int =
        term.frequencies.firstOrNull()?.frequencies?.minOfOrNull { it.value } ?: Int.MAX_VALUE

    private fun JsonElement.isFormOf(): Boolean {
        if (this !is JsonArray || size != 2) return false
        val target = this[0]
        val rules = this[1]
        return target is JsonPrimitive && target.isString &&
            rules is JsonArray && rules.isNotEmpty() &&
            rules.all { it is JsonPrimitive && it.isString }
    }

    private fun JsonElement.formOfTarget(): String = ((this as JsonArray)[0] as JsonPrimitive).content

    private val whitespace = Regex("\\s+")
}
