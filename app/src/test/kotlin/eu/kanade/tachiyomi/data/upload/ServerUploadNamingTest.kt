package eu.kanade.tachiyomi.data.upload

import eu.kanade.tachiyomi.ui.browse.source.MangaImportUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

class ServerUploadNamingTest {
    private val berserk = Manga.create().copy(ogTitle = "Berserk")

    @Test
    fun `stem carries the series and a zero padded chapter number`() {
        assertEquals("Berserk, Ch. 012", ServerUploadNaming.stem(berserk, chapter(12.0, "Chapter 12: Guts")))
        assertEquals("Berserk, Ch. 012.5", ServerUploadNaming.stem(berserk, chapter(12.5, "Ch. 12.5")))
        assertEquals("Berserk, Ch. 001", ServerUploadNaming.stem(berserk, chapter(1.0, "1")))
        assertEquals("Berserk", ServerUploadNaming.seriesFolder(berserk))
    }

    @Test
    fun `unnumbered chapters fall back to their name`() {
        assertEquals("Berserk, Oneshot", ServerUploadNaming.stem(berserk, chapter(-1.0, "Oneshot")))
    }

    @Test
    fun `a title with a dash separator does not read as Title - Author to calibre`() {
        val manga = Manga.create().copy(ogTitle = "Dune - The Graphic Novel")
        assertEquals("Dune – The Graphic Novel, Ch. 003", ServerUploadNaming.stem(manga, chapter(3.0, "3")))
        assertEquals("Dune – The Graphic Novel", ServerUploadNaming.seriesFolder(manga))
    }

    @Test
    fun `the stem strips back to the series when it comes home through OPDS`() {
        for (chapter in listOf(chapter(12.0, "12"), chapter(12.5, "12.5"), chapter(1044.0, "1044"))) {
            val stem = ServerUploadNaming.stem(berserk, chapter)
            assertEquals("Berserk", MangaImportUtil.getSeriesTitle(stem))
            assertEquals(ServerUploadNaming.seriesFolder(berserk), MangaImportUtil.getSafeFolderName(MangaImportUtil.getSeriesTitle(stem)))
        }
        val japanese = Manga.create().copy(ogTitle = "ベルセルク")
        assertEquals("ベルセルク", MangaImportUtil.getSeriesTitle(ServerUploadNaming.stem(japanese, chapter(3.0, "3"))))
    }

    private fun chapter(number: Double, name: String) = Chapter.create().copy(chapterNumber = number, name = name)
}
