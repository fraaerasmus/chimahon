package eu.kanade.tachiyomi.ui.browse.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MangaImportUtilTest {
    @Test
    fun `series title drops a trailing volume marker so volumes share one folder`() {
        assertEquals("Berserk", MangaImportUtil.getSeriesTitle("Berserk, Vol. 3"))
        assertEquals("Berserk", MangaImportUtil.getSeriesTitle("Berserk Vol. 3"))
        assertEquals("Berserk", MangaImportUtil.getSeriesTitle("Berserk 3"))
        assertEquals("Berserk Deluxe Edition", MangaImportUtil.getSeriesTitle("Berserk Deluxe Edition Volume 1"))
        assertEquals("Dune: The Graphic Novel", MangaImportUtil.getSeriesTitle("Dune: The Graphic Novel, Book 1"))
        assertEquals("One Piece", MangaImportUtil.getSeriesTitle("One Piece - Ch. 1044"))
        assertEquals("ベルセルク", MangaImportUtil.getSeriesTitle("ベルセルク 第3巻"))
        assertEquals("ベルセルク", MangaImportUtil.getSeriesTitle("ベルセルク 3巻"))
    }

    @Test
    fun `series title leaves titles without a marker alone`() {
        assertEquals("Dr. Stone", MangaImportUtil.getSeriesTitle("Dr. Stone"))
        assertEquals("20th Century Boys", MangaImportUtil.getSeriesTitle("20th Century Boys"))
        assertEquals("Chrono", MangaImportUtil.getSeriesTitle("Chrono"))
        assertEquals("Vol. 1", MangaImportUtil.getSeriesTitle("Vol. 1"))
    }

    @Test
    fun `base title still strips the file extension first`() {
        assertEquals("One Piece", MangaImportUtil.getBaseTitle("One Piece Vol. 1.cbz"))
    }
}
