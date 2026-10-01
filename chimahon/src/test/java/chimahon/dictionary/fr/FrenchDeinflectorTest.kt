package chimahon.dictionary.fr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FrenchDeinflectorTest {

    @Test
    fun `reaches the lemma for conjugated verbs and plurals`() {
        val expected = mapOf(
            "mangeaient" to "manger",
            "détestait" to "détester",
            "parlons" to "parler",
            "finissent" to "finir",
            "étaient" to "être",
            "allait" to "aller",
            "prenaient" to "prendre",
            "tables" to "table",
            "chevaux" to "cheval",
            "pommes" to "pomme",
            "Mangeaient" to "manger",
        )

        val missing = expected.filter { (word, lemma) ->
            FrenchDeinflector.preProcess(word)
                .flatMap { FrenchDeinflector.deinflect(it, "fr") }
                .none { it.text == lemma }
        }

        assertEquals(emptyMap<String, String>(), missing)
    }

    @Test
    fun `reports the part of speech and step count of each candidate`() {
        val candidates = FrenchDeinflector.deinflect("tables", "fr")

        assertEquals(0, candidates.first { it.text == "tables" }.steps)
        val singular = candidates.first { it.text == "table" && "n" in it.conditions }
        assertEquals(1, singular.steps)
    }
}
