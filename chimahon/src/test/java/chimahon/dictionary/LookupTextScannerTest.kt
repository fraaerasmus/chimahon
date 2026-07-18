package chimahon.dictionary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class LookupTextScannerTest {

    @ParameterizedTest
    @MethodSource("frenchSelections")
    fun `selects french lookup text from the correct word start`(
        source: String,
        tapOffset: Int,
        expectedText: String,
        expectedStart: Int,
    ) {
        val selection = LookupTextScanner.scan(
            text = source,
            tapOffset = tapOffset,
            languageCode = "fr",
            scanAcrossSpaces = true,
            maxCodePoints = 80,
        )

        assertEquals(expectedText, selection?.text)
        assertEquals(expectedStart, selection?.startOffset)
        assertEquals(expectedStart + expectedText.length, selection?.endOffset)
    }

    @ParameterizedTest
    @MethodSource("nonLookupCharacters")
    fun `rejects punctuation and whitespace taps`(source: String, tapOffset: Int) {
        assertNull(
            LookupTextScanner.scan(
                text = source,
                tapOffset = tapOffset,
                languageCode = "fr",
                scanAcrossSpaces = true,
                maxCodePoints = 80,
            ),
        )
    }

    @ParameterizedTest
    @MethodSource("nonFrenchSelections")
    fun `preserves existing forward-only scanning for other languages`(
        source: String,
        tapOffset: Int,
        expectedText: String,
    ) {
        val selection = LookupTextScanner.scan(
            text = source,
            tapOffset = tapOffset,
            languageCode = "en",
            scanAcrossSpaces = true,
            maxCodePoints = 80,
        )

        assertEquals(expectedText, selection?.text)
        assertEquals(tapOffset, selection?.startOffset)
    }

    companion object {
        @JvmStatic
        fun frenchSelections() = listOf(
            Arguments.of("jamais", 4, "jamais", 0),
            Arguments.of("l'homme dort.", 3, "homme dort", 2),
            Arguments.of("l’homme dort.", 3, "homme dort", 2),
            Arguments.of("l'homme", 0, "l'homme", 0),
            Arguments.of("Qu'il", 3, "il", 3),
            Arguments.of("aujourd'hui", 9, "aujourd'hui", 0),
            Arguments.of("porte-monnaie rouge", 8, "porte-monnaie rouge", 0),
            Arguments.of("coup de main, ensuite", 2, "coup de main", 0),
        )

        @JvmStatic
        fun nonLookupCharacters() = listOf(
            Arguments.of("bonjour monde", 7),
            Arguments.of("bonjour, monde", 7),
            Arguments.of("l'homme", 1),
        )

        @JvmStatic
        fun nonFrenchSelections() = listOf(
            Arguments.of("jamais", 4, "is"),
            Arguments.of("hello world", 1, "ello"),
            Arguments.of("l'homme", 2, "homme"),
        )
    }
}
