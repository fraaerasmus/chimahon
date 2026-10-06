package chimahon.novel.opds

import eu.kanade.tachiyomi.ui.browse.source.ImportHandler
import eu.kanade.tachiyomi.ui.browse.source.MangaImportUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/** Puts comic archives downloaded from an OPDS catalog into the local source. */
object OpdsComicImporter {

    data class Result(val seriesFolder: String, val chapterName: String)

    /**
     * Saves [file] as `local/<series>/<title>.<ext>` and refreshes the series so the chapter shows
     * up without a library update. The bytes are copied as received, so the file's KOReader
     * document id matches the same download on another device. [file] is deleted afterwards.
     *
     * The series folder comes from the entry's series metadata when the catalog provides it and from
     * the title with its volume marker stripped otherwise; the chapter file is named after the
     * entry title, which keeps volumes ordered and lets chapter recognition pick the number up.
     */
    suspend fun import(file: File, displayName: String, entry: OpdsEntry): Result = withContext(Dispatchers.IO) {
        try {
            val mangaRepository: MangaRepository = Injekt.get()
            val storageManager: StorageManager = Injekt.get()
            val libraryPreferences: LibraryPreferences = Injekt.get()
            val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get()

            val localSourceDir = storageManager.getLocalSourceDirectory()
                ?: error("The local source folder is not available.")
            val seriesTitle = entry.series?.takeIf { it.isNotBlank() } ?: seriesTitle(entry.title)
            val seriesFolder = MangaImportUtil.getSafeFolderName(seriesTitle).ifBlank { "Untitled" }
            val extension = displayName.substringAfterLast('.', "cbz")
            val chapterName = MangaImportUtil.getSafeFolderName(entry.title)
                .ifBlank { displayName.substringBeforeLast('.') }
            val fileName = "$chapterName.$extension"

            val mangaDir = localSourceDir.createDirectory(seriesFolder)
                ?: error("Could not create the folder $seriesFolder.")
            mangaDir.findFile(fileName)?.delete()
            val target = mangaDir.createFile(fileName) ?: error("Could not create $fileName.")
            file.inputStream().use { input ->
                target.openOutputStream().use { output -> input.copyTo(output) }
            }

            ImportHandler.addMangaToLibrary(seriesFolder, mangaRepository, libraryPreferences)
            val manga = mangaRepository.getMangaByUrlAndSourceId(seriesFolder, LocalSource.ID)
                ?: error("Could not add $seriesFolder to the library.")
            updateMangaFromRemote(manga, fetchDetails = true, fetchChapters = true)
            Result(seriesFolder, chapterName)
        } finally {
            file.delete()
        }
    }

    // Trailing volume/chapter markers as catalogs write them: ", Vol. 3", " Volume 3", " Book 1", " Ch. 12",
    // " 第3巻", or a bare trailing number.
    private val trailingMarker = Regex(
        """[\s,:\-–_]*(?:\b(?:v|vol|volume|ch|chapter|book|part|tome|no)\.?\s*|第\s*|\s)[0-9]+(?:\.[0-9]+)?\s*(?:巻|話|冊)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Series title for a catalog entry title that has no series metadata, so that every volume of
     * "Berserk, Vol. 3" style titles lands in one "Berserk" folder. It takes a title, not a file
     * name, so a dot in the title is left alone.
     */
    fun seriesTitle(title: String): String {
        val stripped = trailingMarker.replace(title.trim(), "")
            .trim()
            .trimEnd(',', '-', '–', '_', ':')
            .trim()
        return stripped.ifBlank { title.trim() }
    }
}
