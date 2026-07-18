package chimahon.dictionary

import chimahon.GlossaryEntry
import chimahon.LookupResult
import chimahon.TermResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrenchLookupPolicyTest {

    @Test
    fun `strips recognized french elisions and keeps trailing lookup context`() {
        assertEquals("homme politique", FrenchLookupPolicy.stripElision("l'homme politique", "fr"))
        assertEquals("accord", FrenchLookupPolicy.stripElision("D’accord", "fr"))
        assertEquals("il", FrenchLookupPolicy.stripElision("qu'il", "fr"))
        assertNull(FrenchLookupPolicy.stripElision("aujourd'hui", "fr"))
        assertNull(FrenchLookupPolicy.stripElision("l'homme", "en"))
    }

    @Test
    fun `produces original and stripped french lookup queries`() {
        assertEquals(
            listOf("l'homme dort", "homme dort"),
            FrenchLookupPolicy.lookupQueries("l'homme dort", "fr"),
        )
        assertEquals(listOf("l'homme dort"), FrenchLookupPolicy.lookupQueries("l'homme dort", "en"))
    }

    @Test
    fun `preserves french recursive words and existing japanese filtering`() {
        assertEquals("l'homme politique", FrenchLookupPolicy.recursiveQuery(" l'homme politique ", "fr"))
        assertEquals("猫", FrenchLookupPolicy.recursiveQuery("「猫」", "ja"))
        assertNull(FrenchLookupPolicy.recursiveQuery("homme", "ja"))
    }

    @Test
    fun `ranks real definitions before form-of-only entries then by longest match`() {
        val shortReal = result("homme", matched = "homme", definitionTags = listOf("noun"))
        val longFormOf = result("détester", matched = "détestait", definitionTags = listOf("non-lemma"))
        val longReal = result("homme politique", matched = "homme politique", definitionTags = listOf("noun"))

        assertEquals(
            listOf("homme politique", "homme", "détester"),
            FrenchLookupPolicy.mergeResults(
                results = listOf(shortReal, longFormOf, longReal),
                languageCode = "fr",
                maxResults = 20,
            ).map { it.term.expression },
        )
    }

    @Test
    fun `mixed glossaries count as a real definition and ordering is stable`() {
        val mixed = result("avait", matched = "avait", definitionTags = listOf("non-lemma", "verb"))
        val real = result("avoir", matched = "avait", definitionTags = listOf("verb"))
        val formOnly = result("avaient", matched = "avaient", definitionTags = listOf("non-lemma"))

        val ordered = FrenchLookupPolicy.mergeResults(listOf(mixed, real, formOnly), "fr", 20)

        assertEquals(listOf("avait", "avoir", "avaient"), ordered.map { it.term.expression })
        assertFalse(FrenchLookupPolicy.isFormOfOnly(mixed))
        assertTrue(FrenchLookupPolicy.isFormOfOnly(formOnly))
    }

    @Test
    fun `deduplicates by expression reading and matched text and applies result cap`() {
        val sameMatch = result("homme", matched = "homme", definitionTags = listOf("noun"))
        val otherMatch = result("homme", matched = "l'", definitionTags = listOf("noun"))
        val extra = result("humain", matched = "homme", definitionTags = listOf("noun"))

        val ordered = FrenchLookupPolicy.mergeResults(
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
            FrenchLookupPolicy.highlightFor("l'homme", "homme"),
        )
        assertEquals(
            LookupHighlight(startOffset = 0, codePointCount = 5),
            FrenchLookupPolicy.highlightFor("homme", "homme"),
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
