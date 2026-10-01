package chimahon.dictionary

import chimahon.Frequency
import chimahon.FrequencyEntry
import chimahon.GlossaryEntry
import chimahon.TermResult
import chimahon.dictionary.fr.FrenchDeinflector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeinflectedLookupTest {

    @Test
    fun `drops a verb rule that lands on a noun entry`() {
        val results = lookup("ment", term("mer", rules = "n"), term("mentir", rules = "v"))

        assertFalse(results.any { it.term.expression == "mer" })
        assertFalse(DeinflectedLookup.matchesPartOfSpeech(setOf("v"), "n"))
        assertTrue(DeinflectedLookup.matchesPartOfSpeech(setOf("v"), "v"))
        assertTrue(DeinflectedLookup.matchesPartOfSpeech(setOf("v"), "adj"))
        assertTrue(DeinflectedLookup.matchesPartOfSpeech(setOf("v"), ""))
        assertTrue(DeinflectedLookup.matchesPartOfSpeech(emptySet(), "n"))
    }

    @Test
    fun `keeps a noun reached through both a verb rule and the plural rule`() {
        val results = lookup("portes", term("porte", rules = "n"), term("porter", rules = "v"))

        assertEquals(setOf("porte", "porter"), results.map { it.term.expression }.toSet())
        assertTrue(results.all { it.matched == "portes" })
    }

    @Test
    fun `breaks ties at the same length by frequency`() {
        val results = lookup(
            "tables",
            term("tabler", rules = "v", frequency = 9000),
            term("table", rules = "n", frequency = 300),
        )

        assertEquals(listOf("table", "tabler"), results.map { it.term.expression })
    }

    @Test
    fun `ranks the exact headword before deinflected ones and longer matches first`() {
        val results = lookup(
            "marche vite",
            term("marcher", rules = "v"),
            term("marche", rules = "n"),
            term("ma", rules = "adj"),
        )

        assertEquals(listOf("marche", "marcher", "ma"), results.map { it.term.expression })
        assertEquals(listOf("marche", "marche", "ma"), results.map { it.matched })
    }

    @Test
    fun `follows form-of glossaries to lemmas the rules cannot reach`() {
        val results = lookup(
            "vendu",
            term("vendu", rules = "v", glossary = """[["vendre",["past participle"]]]"""),
            term("vendre", rules = "v"),
        )

        assertEquals(listOf("vendu", "vendre"), results.map { it.term.expression })
        assertEquals(listOf("vendu", "vendu"), results.map { it.matched })
        assertEquals("vendre", results[1].deinflected)
    }

    @Test
    fun `reads form-of targets from single and listed deinflection glossaries`() {
        assertEquals(
            listOf("grand"),
            DeinflectedLookup.formOfTargets(term("grandes", glossary = """["grand",["feminine","plural"]]""")),
        )
        assertEquals(
            listOf("aller", "allée"),
            DeinflectedLookup.formOfTargets(
                term("allées", glossary = """[["aller",["participle"]],["allée",["plural"]]]"""),
            ),
        )
        assertEquals(emptyList<String>(), DeinflectedLookup.formOfTargets(term("table", glossary = "meuble")))
        assertEquals(
            emptyList<String>(),
            DeinflectedLookup.formOfTargets(term("table", glossary = """[{"tag":"div","content":"meuble"}]""")),
        )
    }

    private fun lookup(text: String, vararg terms: TermResult) =
        DeinflectedLookup.lookup(text, FrenchDeinflector, "fr", 20) { candidate ->
            terms.filter { it.expression == candidate }
        }

    private fun term(
        expression: String,
        rules: String = "",
        glossary: String = expression,
        frequency: Int? = null,
    ) = TermResult(
        expression = expression,
        reading = "",
        rules = rules,
        glossaries = arrayOf(
            GlossaryEntry(dictName = "Test", glossary = glossary, definitionTags = "", termTags = ""),
        ),
        frequencies = frequency
            ?.let { arrayOf(FrequencyEntry("Freq", arrayOf(Frequency(it, it.toString())))) }
            ?: emptyArray(),
        pitches = emptyArray(),
    )
}
