package com.canopus.chimareader.kosync

import com.canopus.chimareader.data.epub.EpubBook
import com.canopus.chimareader.data.epub.EpubSpine
import com.canopus.chimareader.data.epub.SpineItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KosyncSpineIndexTest {
    // crengine numbers a DocFragment per spine item, but the reader only walks the linear ones, so
    // a non-linear item in the middle shifts the two indexes apart.
    private val book = EpubBook(
        spine = EpubSpine(
            items = listOf(
                SpineItem(idref = "cover", linear = true),
                SpineItem(idref = "advert", linear = false),
                SpineItem(idref = "chapter1", linear = true),
                SpineItem(idref = "chapter2", linear = true),
            ),
        ),
    )

    @Test
    fun `maps reader chapters onto raw spine positions`() {
        assertEquals(0, book.rawSpineIndex(0))
        assertEquals(2, book.rawSpineIndex(1))
        assertEquals(3, book.rawSpineIndex(2))
    }

    @Test
    fun `maps raw spine positions back to reader chapters`() {
        assertEquals(0, book.chapterIndexForSpine(0))
        assertEquals(1, book.chapterIndexForSpine(2))
        assertEquals(2, book.chapterIndexForSpine(3))
    }

    @Test
    fun `skipped and unknown spine positions have no chapter`() {
        assertNull(book.chapterIndexForSpine(1))
        assertNull(book.chapterIndexForSpine(9))
        assertNull(book.chapterIndexForSpine(-1))
    }

    @Test
    fun `round trips every chapter`() {
        book.linearSpineItems.indices.forEach { chapter ->
            assertEquals(chapter, book.chapterIndexForSpine(book.rawSpineIndex(chapter)))
        }
    }
}
