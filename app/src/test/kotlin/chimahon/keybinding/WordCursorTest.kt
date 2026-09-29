package chimahon.keybinding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WordCursorTest {

    /** A dictionary that knows [words] and answers with the longest one the query starts with. */
    private fun dictionary(vararg words: String): (String) -> String? = { query ->
        words.filter { query.startsWith(it) }.maxByOrNull { it.length }
    }

    private fun words(text: String, languageCode: String, measure: (String) -> String?) =
        cursorWords(text, languageCode, measure).map { text.substring(it.start, it.start + it.length) }

    @Test
    fun `japanese is cut where the dictionary says words end`() {
        val measure = dictionary("猫", "が", "好き", "好", "です")

        assertEquals(listOf("猫", "が", "好き", "です"), words("猫が好きです。", "ja", measure))
    }

    @Test
    fun `a character the dictionary does not know is a word of its own`() {
        val measure = dictionary("猫", "です")

        assertEquals(listOf("猫", "ぬ", "です"), words("猫ぬです", "ja", measure))
    }

    @Test
    fun `punctuation, spaces and line breaks are stepped over`() {
        val measure = dictionary("猫", "犬")

        assertEquals(listOf("猫", "犬"), words("「猫」、\n 犬！", "ja", measure))
    }

    @Test
    fun `a spaced language falls back to whole words`() {
        assertEquals(listOf("the", "cat", "sat"), words("the cat sat.", "en") { null })
    }

    @Test
    fun `a french elision is split from the word it leans on`() {
        val measure = dictionary("l'", "homme", "est", "là")

        assertEquals(listOf("l'", "homme", "est", "là"), words("l'homme est là", "fr", measure))
    }

    @Test
    fun `a phrase the dictionary knows stays together`() {
        val measure = dictionary("tout à fait", "tout", "bien")

        assertEquals(listOf("tout à fait", "bien"), words("tout à fait bien", "fr", measure))
    }

    @Test
    fun `text with nothing to look up has no words`() {
        assertEquals(emptyList<String>(), words("…！ ", "ja") { null })
        assertNull(WordCursor.start("…！ ", emptyList(), wasPaused = false))
    }

    @Test
    fun `the cursor starts on the first word and stops at both ends`() {
        val text = "猫が好き"
        val cursor = WordCursor.start(text, cursorWords(text, "ja", dictionary("猫", "が", "好き")), wasPaused = true)!!

        assertEquals(CursorWord(0, 1), cursor.word)
        assertEquals(cursor, cursor.moved(-1))
        assertEquals(CursorWord(1, 1), cursor.moved(1).word)
        assertEquals(CursorWord(2, 2), cursor.moved(1).moved(1).moved(1).word)
    }
}
