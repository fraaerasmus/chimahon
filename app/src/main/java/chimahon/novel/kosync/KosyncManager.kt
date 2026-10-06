package chimahon.novel.kosync

import chimahon.custom.core.JsonFileStore
import chimahon.novel.data.BookStorage
import chimahon.novel.data.Bookmark
import chimahon.novel.data.epub.EpubBook
import chimahon.novel.data.epub.EpubParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
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
    private val session: KosyncSession,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val positionStore: KosyncPositionStore = SidecarPositionStore,
) {
    /** Whether a book pulls when it opens or comes back to the front. */
    val canPull: Boolean get() = session.canPull

    /** Whether a book pushes when it is left. */
    val canPush: Boolean get() = session.canPush

    /**
     * The pull a book opens with. It does nothing unless sync on open is on, calls [onSyncing]
     * right before it talks to the server, and never throws: a kosync failure must not keep the
     * book from opening.
     */
    suspend fun pullOnOpen(bookDir: File, onSyncing: () -> Unit) {
        if (!canPull) return
        onSyncing()
        withContext(ioDispatcher) { runCatching { pull(bookDir) } }
    }

    /**
     * Moves the book to a newer position from another device and returns the bookmark it became,
     * or null when the local position stands.
     */
    suspend fun pull(bookDir: File): Bookmark? {
        if (!session.canPull) return null
        val document = documentId(bookDir) ?: return null

        val local = positionStore.load(bookDir)
        // A local position that carries no date is never replaced.
        val localSeconds = local?.let { it.lastModified?.div(1_000) ?: Long.MAX_VALUE }
        val remote = session.fetchNewer(document, localSeconds) ?: return null
        val percentage = remote.percentage ?: return null

        val bookmark = remoteBookmark(bookDir, remote.progress, percentage, remote.timestamp) ?: return null
        positionStore.save(bookDir, bookmark)
        saveState(bookDir, KosyncBookState(bookmark.characterCount, remote.timestamp))
        return bookmark
    }

    /** Sends the book's position unless it is the one last pulled or pushed. */
    suspend fun push(bookDir: File) {
        if (!session.canPush) return
        val bookmark = positionStore.load(bookDir) ?: return
        if (loadState(bookDir).lastSyncedCharacterCount == bookmark.characterCount) return
        val document = documentId(bookDir) ?: return
        val bookInfo = withContext(ioDispatcher) { KosyncBookIndex.loadOrBuild(bookDir) }
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

        val timestamp = session.push(document, xpointer, percentage)
        saveState(bookDir, KosyncBookState(bookmark.characterCount, timestamp))
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
        val bookInfo = withContext(ioDispatcher) { KosyncBookIndex.loadOrBuild(bookDir) } ?: return null
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

    /**
     * The packed EPUB kept verbatim beside the extracted book: `book.epub` in the extraction cache
     * of a public book, `<folder>.epub` for a private import, `source.epub` for books this fork
     * imported before upstream kept the file itself. Absent for older imports.
     */
    private suspend fun documentId(bookDir: File): String? = withContext(ioDispatcher) {
        val epub = sourceEpub(bookDir)
        if (epub == null) {
            logcat(LogPriority.INFO) { "kosync: no source EPUB for '${bookDir.name}'; re-import the book to sync it" }
        }
        epub?.let(KosyncDocumentId::partialMd5)
    }

    private fun sourceEpub(bookDir: File): File? {
        listOf("book.epub", "${bookDir.name}.epub", LEGACY_SOURCE_EPUB)
            .map { File(bookDir, it) }
            .firstOrNull { it.isFile }
            ?.let { return it }
        return bookDir.listFiles { file -> file.isFile && file.name.endsWith(".epub", ignoreCase = true) }
            ?.maxByOrNull { it.length() }
    }

    private suspend fun loadBook(bookDir: File): EpubBook? = withContext(ioDispatcher) {
        try {
            BookStorage.loadEpub(bookDir)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "kosync: could not load EPUB ${bookDir.name}" }
            null
        }
    }

    private fun chapterBody(book: EpubBook, chapterIndex: Int) =
        try {
            EpubParser().parseChapter(book, chapterIndex)?.let(KosyncChapterDom::parseBody)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "kosync: could not read chapter $chapterIndex" }
            null
        }

    private fun stateStore(bookDir: File) =
        JsonFileStore(File(bookDir, STATE_FILE_NAME), KosyncBookState.serializer(), ::KosyncBookState)

    private suspend fun loadState(bookDir: File): KosyncBookState = withContext(ioDispatcher) {
        stateStore(bookDir).load()
    }

    private suspend fun saveState(bookDir: File, state: KosyncBookState) = withContext(ioDispatcher) {
        runCatching { stateStore(bookDir).save(state) }
        Unit
    }

    companion object {
        private const val STATE_FILE_NAME = "kosync.json"
        private const val LEGACY_SOURCE_EPUB = "source.epub"
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
