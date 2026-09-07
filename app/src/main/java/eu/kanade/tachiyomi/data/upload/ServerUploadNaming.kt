package eu.kanade.tachiyomi.data.upload

import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import kotlin.math.floor

/**
 * Names for chapters uploaded to the server drop folder.
 *
 * calibre titles a book from the file stem, so the stem carries the series and the chapter:
 * `<series>, Ch. <number>` (`Berserk, Ch. 012`). The comma matters: calibre's default file
 * name pattern splits `Title - Author` on " - ", which would turn every chapter into a book
 * titled after the series alone. The number is zero-padded to three digits so calibre's title
 * sort keeps chapters in order, and the whole stem strips back to the series through
 * `MangaImportUtil.getSeriesTitle` when the file returns via OPDS.
 */
object ServerUploadNaming {
    fun seriesFolder(manga: Manga): String = seriesTitle(manga.ogTitle)

    fun seriesTitle(title: String): String =
        DiskUtil.buildValidFilename(title.replace(" - ", " – ").trim()).ifBlank { "Untitled" }

    fun stem(manga: Manga, chapter: Chapter): String =
        DiskUtil.buildValidFilename("${seriesTitle(manga.ogTitle)}, ${chapterPart(chapter)}", MAX_STEM_BYTES)

    /** `Ch. 012` / `Ch. 012.5` for a recognised number, otherwise the chapter name. */
    fun chapterPart(chapter: Chapter): String {
        val number = chapter.chapterNumber.takeIf { it >= 0 }
            ?: return DiskUtil.buildValidFilename(chapter.name.replace(" - ", " – ").trim()).ifBlank { "Chapter" }
        val whole = floor(number).toInt()
        val fraction = number.toString().substringAfter('.', "").trimEnd('0')
        return buildString {
            append("Ch. ")
            append(whole.toString().padStart(3, '0'))
            if (fraction.isNotEmpty()) append('.').append(fraction)
        }
    }

    private const val MAX_STEM_BYTES = 200
}
