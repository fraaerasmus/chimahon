package com.canopus.chimareader.kosync

import android.content.Context
import android.util.Log
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.Bookmark
import com.canopus.chimareader.data.FileNames
import com.canopus.chimareader.data.epub.EpubBook
import com.canopus.chimareader.data.epub.EpubParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

/**
 * Reading-position sync against a KOReader kosync server, independent of the ッツ/Drive sync.
 *
 * Pull applies a newer remote position, paragraph-exact when the remote XPointer resolves against
 * the chapter and by percentage otherwise. Push sends a crengine XPointer plus the character
 * percentage. A document is identified by a partial MD5 of the stored source EPUB, which is how
 * KOReader identifies it too, so the same file on a Kobo lines up without changing any setting
 * there.
 */
class KosyncManager(
    private val context: Context,
    private val settingsRepository: KosyncSettingsRepository,
    private val api: KosyncApi = KosyncClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val settingsFlow: Flow<KosyncSettings> get() = settingsRepository.settings

    fun loadSettings(): KosyncSettings = settingsRepository.currentSettings()

    val isEnabled: Boolean
        get() = settingsRepository.currentSettings().let { it.enabled && it.isConfigured } &&
            settingsRepository.hasUserKey()

    suspend fun testConnection(credentials: KosyncCredentials? = null) {
        val resolved = credentials ?: settingsRepository.credentials()
            ?: throw KosyncException("Enter the server, username and password first.")
        api.authorize(resolved)
    }

    suspend fun register(credentials: KosyncCredentials) {
        api.register(credentials)
    }

    suspend fun pull(metadata: BookMetadata): KosyncResult =
        pull(bookDirectory(metadata), metadata.title.orEmpty())

    suspend fun push(metadata: BookMetadata): KosyncResult =
        push(bookDirectory(metadata), metadata.title.orEmpty())

    internal suspend fun pull(bookDir: File, title: String): KosyncResult {
        val settings = settingsRepository.currentSettings()
        if (!settings.enabled) return KosyncResult.Skipped
        val credentials = settingsRepository.credentials() ?: return KosyncResult.Skipped
        val document = documentId(bookDir) ?: return noDocumentId(bookDir, title)

        val remote = api.getProgress(credentials, document) ?: return KosyncResult.Skipped
        val percentage = remote.percentage ?: return KosyncResult.Skipped
        if (remote.deviceId == settingsRepository.deviceId) return KosyncResult.UpToDate(title)

        val local = BookStorage.loadBookmark(bookDir)
        val remoteSeconds = remote.timestamp
        val localSeconds = local?.lastModified?.let { it / 1_000 }
        if (local != null && (remoteSeconds == null || localSeconds == null || remoteSeconds <= localSeconds)) {
            return KosyncResult.UpToDate(title)
        }

        val bookmark = remoteBookmark(bookDir, remote.progress, percentage, remoteSeconds)
            ?: return KosyncResult.Skipped
        BookStorage.saveBookmark(bookmark, bookDir)
        saveState(bookDir, KosyncBookState(bookmark.characterCount, remoteSeconds))
        return KosyncResult.Pulled(title, percentage)
    }

    internal suspend fun push(bookDir: File, title: String): KosyncResult {
        val settings = settingsRepository.currentSettings()
        if (!settings.enabled || !settings.pushEnabled) return KosyncResult.Skipped
        val credentials = settingsRepository.credentials() ?: return KosyncResult.Skipped

        val bookmark = BookStorage.loadBookmark(bookDir) ?: return KosyncResult.Skipped
        if (loadState(bookDir).lastSyncedCharacterCount == bookmark.characterCount) {
            return KosyncResult.UpToDate(title)
        }
        val document = documentId(bookDir) ?: return noDocumentId(bookDir, title)
        val bookInfo = withContext(ioDispatcher) { BookStorage.loadOrBuildBookInfo(bookDir) }
        val totalCharacters = bookInfo?.characterCount ?: 0
        val percentage = if (totalCharacters > 0) {
            (bookmark.characterCount.toDouble() / totalCharacters).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val book = loadBook(bookDir)
        val spineIndex = book?.rawSpineIndex(bookmark.chapterIndex) ?: bookmark.chapterIndex
        // Always emit a pointer that resolves. KOReader applies a reflowable pull with GotoXPointer
        // and no percentage fallback, so a pointer it cannot resolve drops the device to page 1.
        val xpointer = withContext(ioDispatcher) {
            val body = book?.let { chapterBody(it, bookmark.chapterIndex) }
            body?.let { KosyncXPointer.forProgress(spineIndex, it, bookmark.progress) }
                ?: KosyncXPointer.chapterStart(spineIndex)
        }

        val timestamp = api.putProgress(
            credentials = credentials,
            document = document,
            progress = xpointer,
            percentage = percentage,
            device = DEVICE_NAME,
            deviceId = settingsRepository.deviceId,
        )
        saveState(bookDir, KosyncBookState(bookmark.characterCount, timestamp))
        return KosyncResult.Pushed(title, percentage)
    }

    /**
     * Turns a remote position into a local bookmark. [progress] is a crengine XPointer for
     * reflowable documents; KOReader sends a bare page number for image formats instead, and that
     * carries no EPUB position, so those fall through to the percentage.
     */
    private suspend fun remoteBookmark(
        bookDir: File,
        progress: String?,
        percentage: Double,
        timestampSeconds: Long?,
    ): Bookmark? {
        val bookInfo = withContext(ioDispatcher) { BookStorage.loadOrBuildBookInfo(bookDir) } ?: return null
        val lastModified = timestampSeconds?.times(1_000) ?: System.currentTimeMillis()
        val targetCharacter = (percentage.coerceIn(0.0, 1.0) * bookInfo.characterCount).roundToInt()

        val spineIndex = progress?.let(KosyncXPointer::spineIndex)
        val book = spineIndex?.let { loadBook(bookDir) }
        val chapterIndex = spineIndex?.let { book?.chapterIndexForSpine(it) }
        if (book != null && chapterIndex != null && progress != null) {
            val info = bookInfo.chapterInfo[chapterIndex.toString()]
            val resolved = withContext(ioDispatcher) {
                chapterBody(book, chapterIndex)?.let { KosyncXPointer.resolveProgress(progress, it) }
            } ?: info?.takeIf { it.chapterCount > 0 }?.let {
                ((targetCharacter - it.currentTotal).toDouble() / it.chapterCount).coerceIn(0.0, 1.0)
            } ?: 0.0
            val characterCount = info
                ?.let { it.currentTotal + (it.chapterCount * resolved).toInt() }
                ?: targetCharacter
            return Bookmark(
                chapterIndex = chapterIndex,
                progress = resolved,
                characterCount = characterCount.coerceIn(0, bookInfo.characterCount),
                lastModified = lastModified,
            )
        }

        val fallback = bookInfo.resolveCharacterPosition(targetCharacter)
        return Bookmark(
            chapterIndex = fallback?.first ?: 0,
            progress = fallback?.second ?: 0.0,
            characterCount = targetCharacter.coerceIn(0, bookInfo.characterCount),
            lastModified = lastModified,
        )
    }

    private fun noDocumentId(bookDir: File, title: String): KosyncResult {
        Log.i(TAG, "No source EPUB for '${bookDir.name}'; re-import the book to sync it with KOReader.")
        return KosyncResult.NoDocumentId(title)
    }

    private fun bookDirectory(metadata: BookMetadata): File =
        BookStorage.getBookDirectory(context, metadata.folder ?: metadata.id)

    /** The packed EPUB the importer kept verbatim; absent for books imported before it did so. */
    private suspend fun documentId(bookDir: File): String? = withContext(ioDispatcher) {
        File(bookDir, FileNames.sourceEpub)
            .takeIf { it.isFile }
            ?.let(KosyncDocumentId::partialMd5)
    }

    private suspend fun loadBook(bookDir: File): EpubBook? = withContext(ioDispatcher) {
        try {
            BookStorage.loadEpub(bookDir)
        } catch (e: Exception) {
            Log.w(TAG, "Could not load EPUB for kosync: ${bookDir.name}", e)
            null
        }
    }

    private fun chapterBody(book: EpubBook, chapterIndex: Int) =
        try {
            EpubParser().parseChapter(book, chapterIndex)?.let(KosyncChapterDom::parseBody)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read chapter $chapterIndex for kosync", e)
            null
        }

    private suspend fun loadState(bookDir: File): KosyncBookState = withContext(ioDispatcher) {
        val file = File(bookDir, STATE_FILE_NAME)
        if (!file.isFile) return@withContext KosyncBookState()
        runCatching { json.decodeFromString(KosyncBookState.serializer(), file.readText()) }
            .getOrDefault(KosyncBookState())
    }

    private suspend fun saveState(bookDir: File, state: KosyncBookState) = withContext(ioDispatcher) {
        runCatching {
            File(bookDir, STATE_FILE_NAME).writeText(json.encodeToString(KosyncBookState.serializer(), state))
        }
        Unit
    }

    companion object {
        const val DEVICE_NAME = "Chimahon Custom"
        private const val TAG = "KosyncManager"
        private const val STATE_FILE_NAME = "kosync.json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * crengine builds one `DocFragment` per spine item, including the ones the reader skips, so the
 * DocFragment number tracks the raw spine position rather than the reader's chapter index.
 */
internal fun EpubBook.rawSpineIndex(chapterIndex: Int): Int {
    var linear = 0
    spine.items.forEachIndexed { raw, item ->
        if (item.linear) {
            if (linear == chapterIndex) return raw
            linear++
        }
    }
    return chapterIndex
}

/** The reader chapter for a raw spine position, or null when that item is not part of the reading order. */
internal fun EpubBook.chapterIndexForSpine(rawIndex: Int): Int? {
    val item = spine.items.getOrNull(rawIndex) ?: return null
    if (!item.linear) return null
    return spine.items.take(rawIndex).count { it.linear }
}
