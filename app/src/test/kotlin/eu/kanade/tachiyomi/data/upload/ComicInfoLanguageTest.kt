package eu.kanade.tachiyomi.data.upload

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ComicInfoLanguageTest {
    @Test
    fun `source languages map to ISO codes`() {
        assertEquals("ja", ComicInfoLanguage.fromSourceLang("ja"))
        assertEquals("fr", ComicInfoLanguage.fromSourceLang("FR"))
        assertEquals("ar", ComicInfoLanguage.fromSourceLang("ar"))
        assertEquals("zh", ComicInfoLanguage.fromSourceLang("zh-Hans"))
        assertEquals("pt", ComicInfoLanguage.fromSourceLang("pt_BR"))
    }

    @Test
    fun `language-neutral sources get no tag`() {
        assertNull(ComicInfoLanguage.fromSourceLang("all"))
        assertNull(ComicInfoLanguage.fromSourceLang("other"))
        assertNull(ComicInfoLanguage.fromSourceLang(""))
        assertNull(ComicInfoLanguage.fromSourceLang(null))
        assertNull(ComicInfoLanguage.fromSourceLang("localsourcelang"))
    }
}
