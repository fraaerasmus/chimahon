package chimahon.novel.opds

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpdsComicImporterTest {
    @Test
    fun `series title drops a trailing volume marker so volumes share one folder`() {
        assertEquals("Berserk", OpdsComicImporter.seriesTitle("Berserk, Vol. 3"))
        assertEquals("Berserk", OpdsComicImporter.seriesTitle("Berserk Vol. 3"))
        assertEquals("Berserk", OpdsComicImporter.seriesTitle("Berserk 3"))
        assertEquals("Berserk Deluxe Edition", OpdsComicImporter.seriesTitle("Berserk Deluxe Edition Volume 1"))
        assertEquals("Dune: The Graphic Novel", OpdsComicImporter.seriesTitle("Dune: The Graphic Novel, Book 1"))
        assertEquals("One Piece", OpdsComicImporter.seriesTitle("One Piece - Ch. 1044"))
        assertEquals("ベルセルク", OpdsComicImporter.seriesTitle("ベルセルク 第3巻"))
        assertEquals("ベルセルク", OpdsComicImporter.seriesTitle("ベルセルク 3巻"))
    }

    @Test
    fun `series title leaves titles without a marker alone`() {
        assertEquals("Dr. Stone", OpdsComicImporter.seriesTitle("Dr. Stone"))
        assertEquals("20th Century Boys", OpdsComicImporter.seriesTitle("20th Century Boys"))
        assertEquals("Chrono", OpdsComicImporter.seriesTitle("Chrono"))
        assertEquals("Vol. 1", OpdsComicImporter.seriesTitle("Vol. 1"))
    }
}
