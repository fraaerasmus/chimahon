package chimahon.dictionary.es

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class SpanishDeinflectorTest {

    @ParameterizedTest
    @MethodSource("validDeinflections")
    fun `valid deinflections produce expected term`(testCase: TestCase) {
        val results = SpanishDeinflector.deinflect(testCase.source, "es")
        Assertions.assertTrue(
            results.any { it.text == testCase.term },
            "Expected '${testCase.source}' to deinflect to '${testCase.term}', but got: ${results.map { it.text }}",
        )
    }

    @ParameterizedTest
    @MethodSource("invalidDeinflections")
    fun `invalid deinflections do not produce term`(testCase: TestCase) {
        val results = SpanishDeinflector.deinflect(testCase.source, "es")
        Assertions.assertFalse(
            results.any { it.text == testCase.term },
            "Expected '${testCase.source}' to NOT deinflect to '${testCase.term}', but got: ${results.map { it.text }}",
        )
    }

    data class TestCase(val term: String, val source: String)

    companion object {
        @JvmStatic
        fun validDeinflections() = listOf(
            // Nouns
            TestCase("casa", "casas"),
            TestCase("lápiz", "lápices"),
            TestCase("buey", "bueyes"),

            // Feminine adjectives
            TestCase("rojo", "roja"),

            // Present indicative
            TestCase("hablar", "hablo"),
            TestCase("hablar", "hablas"),
            TestCase("hablar", "hablamos"),
            TestCase("comer", "como"),
            TestCase("comer", "comes"),
            TestCase("vivir", "vivo"),
            TestCase("vivir", "vives"),
            TestCase("pensar", "pienso"),
            TestCase("volver", "vuelvo"),

            // Preterite
            TestCase("hablar", "hablaron"),
            TestCase("comer", "comieron"),
            TestCase("vivir", "vivieron"),
            TestCase("hacer", "hiciste"),
            TestCase("ser", "fue"),
            TestCase("ir", "fuimos"),

            // Imperfect
            TestCase("hablar", "hablaba"),
            TestCase("comer", "comía"),
            TestCase("vivir", "vivías"),

            // Future
            TestCase("hablar", "hablaré"),
            TestCase("comer", "comerá"),
            TestCase("decir", "diré"),
            TestCase("hacer", "harás"),
            TestCase("tener", "tendrá"),
            TestCase("poner", "pondré"),

            // Conditional
            TestCase("hablar", "hablaría"),
            TestCase("decir", "diría"),
            TestCase("poder", "podría"),

            // Present subjunctive
            TestCase("hablar", "hable"),
            TestCase("comer", "coma"),
            TestCase("vivir", "viva"),
            TestCase("ser", "sea"),
            TestCase("ir", "vaya"),

            // Imperfect subjunctive
            TestCase("hablar", "hablara"),
            TestCase("hablar", "hablase"),
            TestCase("comer", "comiera"),
            TestCase("comer", "comiese"),
            TestCase("comer", "comiéramos"),
            TestCase("comer", "comieras"),
            TestCase("vivir", "viviesen"),
            TestCase("vivir", "viviera"),
            TestCase("vivir", "vivierais"),
            TestCase("ser", "fuera"),
            TestCase("ser", "fuesen"),
            TestCase("ir", "fuéramos"),

            // Progressive / gerund
            TestCase("hablar", "hablando"),
            TestCase("comer", "comiendo"),
            TestCase("leer", "leyendo"),
            TestCase("vivir", "viviendo"),

            // Imperative
            TestCase("hablar", "habla"),
            TestCase("comer", "come"),
            TestCase("hacer", "haz"),
            TestCase("poner", "pon"),

            // Participle
            TestCase("hablar", "hablado"),
            TestCase("comer", "comido"),
            TestCase("vivir", "vivido"),
            TestCase("hacer", "hecho"),
            TestCase("decir", "dicho"),

            // Reflexive / pronoun substitution
            TestCase("levantar", "levantarse"),
            TestCase("levantar", "levantarme"),
            TestCase("comer", "comerse"),
        )

        @JvmStatic
        fun invalidDeinflections() = listOf(
            // -er/-ir verbs must NOT gain the -ar imperfect subjunctive endings
            TestCase("comer", "comara"),
            TestCase("vivir", "vivasen"),
            // -ar verbs must not gain the -er/-ir -iera endings
            TestCase("hablar", "habliera"),
        )
    }
}