package chimahon.dictionary

import chimahon.GlossaryEntry
import chimahon.LookupResult
import chimahon.TermResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LookupPolicyTest {

    @Test
    fun `a language code is french whatever its region or case`() {
        assertTrue(LookupLanguage.isFrench("fr"))
        assertTrue(LookupLanguage.isFrench(" FR-ca "))
        assertTrue(LookupLanguage.isFrench("fr_FR"))
        assertFalse(LookupLanguage.isFrench("fro"))
        assertFalse(LookupLanguage.isFrench("en"))
        assertEquals("pt", LookupLanguage.primary("pt-BR"))
    }

    @Test
    fun `other languages keep the order they came in`() {
        val first = result("house", matched = "house", definitionTags = listOf("non-lemma"))
        val second = result("home", matched = "house", definitionTags = listOf("noun"))

        assertEquals(listOf(first, second), LookupPolicy.rank(listOf(first, second, first), "en", 20))
    }

    @Test
    fun `ranks by longest match then real definitions before form-of-only entries`() {
        val shortReal = result("homme", matched = "homme", definitionTags = listOf("noun"))
        val longReal = result("homme politique", matched = "homme politique", definitionTags = listOf("noun"))
        val formOf = result("détestait", matched = "détestait", definitionTags = listOf("non-lemma"))
        val lemma = result("détester", matched = "détestait", definitionTags = listOf("verb"))

        assertEquals(
            listOf("homme politique", "détester", "détestait", "homme"),
            LookupPolicy.rank(
                results = listOf(shortReal, formOf, lemma, longReal),
                languageCode = "fr",
                maxResults = 20,
            ).map { it.term.expression },
        )
    }

    @Test
    fun `mixed glossaries count as a real definition and ordering is stable`() {
        val mixed = result("avait", matched = "avait", definitionTags = listOf("non-lemma", "verb"))
        val real = result("avoir", matched = "avait", definitionTags = listOf("verb"))
        val formOnly = result("avais", matched = "avait", definitionTags = listOf("non-lemma"))

        val ordered = LookupPolicy.rank(listOf(formOnly, mixed, real), "fr", 20)

        assertEquals(listOf("avait", "avoir", "avais"), ordered.map { it.term.expression })
        assertFalse(LookupPolicy.isFormOfOnly(mixed))
        assertTrue(LookupPolicy.isFormOfOnly(formOnly))
    }

    @Test
    fun `deduplicates by expression reading and matched text and applies result cap`() {
        val sameMatch = result("homme", matched = "homme", definitionTags = listOf("noun"))
        val otherMatch = result("homme", matched = "l'", definitionTags = listOf("noun"))
        val extra = result("humain", matched = "homme", definitionTags = listOf("noun"))

        val ordered = LookupPolicy.rank(
            listOf(sameMatch, sameMatch, otherMatch, extra),
            "fr",
            maxResults = 2,
        )

        assertEquals(listOf(sameMatch, extra), ordered)
    }

    @Test
    fun `highlight spans from selection start through an elision fallback match`() {
        assertEquals(
            LookupHighlight(startOffset = 0, codePointCount = 7),
            LookupPolicy.highlightFor("l'homme", "homme"),
        )
        assertEquals(
            LookupHighlight(startOffset = 0, codePointCount = 5),
            LookupPolicy.highlightFor("homme", "homme"),
        )
    }

    private fun result(
        expression: String,
        matched: String,
        definitionTags: List<String>,
    ): LookupResult = LookupResult(
        matched = matched,
        deinflected = expression,
        process = emptyArray(),
        term = TermResult(
            expression = expression,
            reading = expression,
            rules = "",
            glossaries = definitionTags.mapIndexed { index, tags ->
                GlossaryEntry(
                    dictName = "Test $index",
                    glossary = expression,
                    definitionTags = tags,
                    termTags = "",
                )
            }.toTypedArray(),
            frequencies = emptyArray(),
            pitches = emptyArray(),
        ),
        preprocessorSteps = 0,
    )
}
