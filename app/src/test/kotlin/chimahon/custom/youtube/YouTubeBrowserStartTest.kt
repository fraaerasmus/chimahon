package chimahon.custom.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YouTubeBrowserStartTest {
    private val history = YouTubeCustomPreferences.START_PAGE_HISTORY
    private val home = YouTubeCustomPreferences.START_PAGE_HOME

    @Test
    fun `watch history opens only for a signed-in account`() {
        assertEquals(history, YouTubeBrowserStart.startPage(preferred = history, signedIn = true))
        assertEquals(home, YouTubeBrowserStart.startPage(preferred = history, signedIn = false))
    }

    @Test
    fun `home opens whether signed in or not`() {
        assertEquals(home, YouTubeBrowserStart.startPage(preferred = home, signedIn = true))
        assertEquals(home, YouTubeBrowserStart.startPage(preferred = home, signedIn = false))
    }
}
