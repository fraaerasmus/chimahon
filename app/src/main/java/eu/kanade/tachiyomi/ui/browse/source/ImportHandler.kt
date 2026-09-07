package eu.kanade.tachiyomi.ui.browse.source

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.canopus.chimareader.data.BookImporter
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.NovelCategory
import com.canopus.chimareader.data.NovelCategoryStorage
import com.canopus.chimareader.opds.OpdsEntry
import com.hippo.unifile.UniFile
import tachiyomi.source.local.LocalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object ImportHandler {

    suspend fun importMangaFiles(context: Context, uris: List<Uri>, folderName: String) = withContext(Dispatchers.IO) {
        val mangaRepository: MangaRepository = Injekt.get()
        val storageManager: StorageManager = Injekt.get()
        val libraryPreferences: LibraryPreferences = Injekt.get()
        
        val localSourceDir = storageManager.getLocalSourceDirectory() ?: return@withContext
        val safeFolderName = MangaImportUtil.getSafeFolderName(folderName)
        val mangaDir = localSourceDir.createDirectory(safeFolderName) ?: return@withContext
        
        uris.forEach { uri ->
            val fileName = getFileName(context, uri) ?: return@forEach
            val file = mangaDir.createFile(fileName) ?: return@forEach
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        
        addMangaToLibrary(safeFolderName, mangaRepository, libraryPreferences)
    }

    suspend fun importMangaFolder(context: Context, uri: Uri, folderName: String) = withContext(Dispatchers.IO) {
        val mangaRepository: MangaRepository = Injekt.get()
        val storageManager: StorageManager = Injekt.get()
        val libraryPreferences: LibraryPreferences = Injekt.get()
        
        val localSourceDir = storageManager.getLocalSourceDirectory() ?: return@withContext
        val safeFolderName = MangaImportUtil.getSafeFolderName(folderName)
        val mangaDir = localSourceDir.createDirectory(safeFolderName) ?: return@withContext
        
        val sourceDir = UniFile.fromUri(context, uri) ?: return@withContext
        val destChapterDir = mangaDir.createDirectory(sourceDir.name ?: "Imported Folder") ?: return@withContext
        
        sourceDir.listFiles()?.forEach { file ->
            if (!file.isDirectory) {
                val destFile = destChapterDir.createFile(file.name) ?: return@forEach
                file.openInputStream().use { input ->
                    destFile.openOutputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        
        addMangaToLibrary(safeFolderName, mangaRepository, libraryPreferences)
    }

    // Chimahon -->
    data class OpdsComicImport(val seriesFolder: String, val chapterName: String)

    /**
     * Saves a comic archive downloaded from an OPDS catalog as `local/<series>/<title>.<ext>` and
     * refreshes the series so the chapter shows up without a library update. The bytes are copied
     * as received, so the file's KOReader document id matches the same download on another device.
     *
     * The series folder comes from the entry's series metadata when the catalog provides it and from
     * the title with its volume marker stripped otherwise; the chapter file is named after the
     * entry title, which keeps volumes ordered and lets chapter recognition pick the number up.
     */
    suspend fun importComicFromOpds(
        context: Context,
        file: java.io.File,
        displayName: String,
        entry: OpdsEntry,
    ): OpdsComicImport = withContext(Dispatchers.IO) {
        try {
            val mangaRepository: MangaRepository = Injekt.get()
            val storageManager: StorageManager = Injekt.get()
            val libraryPreferences: LibraryPreferences = Injekt.get()
            val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get()

            val localSourceDir = storageManager.getLocalSourceDirectory()
                ?: error("The local source folder is not available.")
            val seriesTitle = entry.series?.takeIf { it.isNotBlank() } ?: MangaImportUtil.getSeriesTitle(entry.title)
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

            val manga = addMangaToLibrary(seriesFolder, mangaRepository, libraryPreferences)
            updateMangaFromRemote(manga, fetchDetails = true, fetchChapters = true)
            OpdsComicImport(seriesFolder, chapterName)
        } finally {
            file.delete()
        }
    }
    // Chimahon <--

    private suspend fun addMangaToLibrary(
        safeFolderName: String,
        mangaRepository: MangaRepository,
        libraryPreferences: LibraryPreferences
    ): Manga {
        val existingManga = mangaRepository.getMangaByUrlAndSourceId(safeFolderName, LocalSource.ID)
        val manga = if (existingManga == null) {
            val newManga = Manga.create().copy(
                url = safeFolderName,
                ogTitle = safeFolderName,
                source = LocalSource.ID,
                favorite = true,
                dateAdded = System.currentTimeMillis(),
            )
            val added = mangaRepository.insertNetworkManga(listOf(newManga))
            if (added.isNotEmpty()) added.first() else newManga
        } else {
            if (!existingManga.favorite) {
                mangaRepository.update(MangaUpdate(id = existingManga.id, favorite = true))
            }
            existingManga
        }
        
        if (existingManga == null) {
            val defaultCategoryId = libraryPreferences.defaultCategory().get()
            if (defaultCategoryId != -1) {
                mangaRepository.setMangaCategories(manga.id, listOf(defaultCategoryId.toLong()))
            }
        }
        return manga
    }

    suspend fun importNovels(context: Context, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val novelCategoryStorage: NovelCategoryStorage = Injekt.get()
        
        uris.forEach { uri ->
            val result = BookImporter.importEpub(context, uri)
            val bookMetadata = result.metadata ?: return@forEach
            
            // Add to default novel category
            val categories = novelCategoryStorage.loadAllCategories()
            val defaultCategory = categories.find { it.isSystemCategory } 
                ?: categories.firstOrNull() // Should always have a system category now
            
            if (defaultCategory != null) {
                val existingCategoryIds = bookMetadata.categoryIds
                    .filter { it.isNotBlank() }
                    .distinct()
                val updatedCategoryIds = if (existingCategoryIds.any { it != NovelCategory.UNCATEGORIZED_ID }) {
                    existingCategoryIds.filterNot { it == NovelCategory.UNCATEGORIZED_ID }
                } else {
                    (existingCategoryIds + defaultCategory.id).distinct()
                }
                val updatedMetadata = bookMetadata.copy(
                    categoryIds = updatedCategoryIds
                )
                val bookDir = java.io.File(BookStorage.getBooksDirectory(context), updatedMetadata.id)
                BookStorage.saveMetadata(updatedMetadata, bookDir)
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
    }
}
