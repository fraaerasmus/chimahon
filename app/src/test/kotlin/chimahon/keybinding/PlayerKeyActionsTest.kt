package chimahon.keybinding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerKeyActionsTest {

    private val tracks = listOf(1, 2, 3)

    @Test
    fun `cycling walks the tracks and then turns off`() {
        assertEquals(1, nextTrackId(tracks, current = -1, other = -1))
        assertEquals(2, nextTrackId(tracks, current = 1, other = -1))
        assertEquals(-1, nextTrackId(tracks, current = 3, other = -1))
    }

    @Test
    fun `cycling skips the track the other slot shows`() {
        assertEquals(3, nextTrackId(tracks, current = 1, other = 2))
        assertEquals(2, nextTrackId(tracks, current = -1, other = 1))
    }

    @Test
    fun `cycling with no tracks stays off`() {
        assertEquals(-1, nextTrackId(emptyList(), current = -1, other = -1))
    }

    @Test
    fun `cycling from a track that is gone turns off`() {
        assertEquals(-1, nextTrackId(tracks, current = 9, other = -1))
    }

    @Test
    fun `an mpv command splits on spaces`() {
        assertEquals(listOf("cycle", "sub"), tokenizeMpvCommand("  cycle   sub "))
        assertEquals(emptyList<String>(), tokenizeMpvCommand("   "))
    }

    @Test
    fun `quotes keep an mpv argument together`() {
        assertEquals(
            listOf("show-text", "two words", "3000"),
            tokenizeMpvCommand("""show-text "two words" 3000"""),
        )
        assertEquals(listOf("show-text", "it's"), tokenizeMpvCommand("""show-text "it's""""))
        assertEquals(listOf("show-text", """say "hi""""), tokenizeMpvCommand("""show-text 'say "hi"'"""))
        assertEquals(listOf("set", "title", ""), tokenizeMpvCommand("""set title """""))
    }

    @Test
    fun `a backslash escapes inside double quotes`() {
        assertEquals(listOf("show-text", """a "b" \c"""), tokenizeMpvCommand("""show-text "a \"b\" \\c""""))
    }
}
