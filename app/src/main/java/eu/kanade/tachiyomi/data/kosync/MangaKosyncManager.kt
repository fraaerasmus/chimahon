package eu.kanade.tachiyomi.data.kosync

import android.content.Context
import com.canopus.chimareader.kosync.KosyncApi
import com.canopus.chimareader.kosync.KosyncClient
import com.canopus.chimareader.kosync.KosyncDocumentId
import com.canopus.chimareader.kosync.KosyncManager
import com.canopus.chimareader.kosync.KosyncPagedProgress
import com.canopus.chimareader.kosync.KosyncSettingsRepository
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.io.LocalSourceFileSystem
import tachiyomi.source.local.isLocal
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * KOReader progress sync for manga chapters, sharing the novel reader's server login.
 *
 * A chapter takes part when it is one archive file on disk (a CBZ in the local source or a
 * downloaded CBZ); KOReader identifies it by the partial MD5 of that file, so the same archive
 * opened on a Kobo matches. Progress is the 1-based page number and page / page count, which is
 * what KOReader sends for documents with pages. Pull applies a remote position that is newer than
 * the last local page turn; push sends the current page unless it was already synced.
 */
class MangaKosyncManager(
    context: Context,
    private val settingsRepository: KosyncSettingsRepository,
    private val downloadProvider: DownloadProvider,
    private val sourceManager: SourceManager,
    private val localFileSystem: LocalSourceFileSystem,
    private val api: KosyncApi = KosyncClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val stateFile = File(context.applicationContext.filesDir, STATE_FILE_NAME)
    private val mutex = Mutex()
    private var states: MutableMap<Long, ChapterState>? = null

    /** Last local page turn per chapter, unix seconds; the database timestamp lags behind the reader. */
    private val pageTurns = ConcurrentHashMap<Long, Long>()

    val isEnabled: Boolean
        get() = settingsRepository.currentSettings().let { it.enabled && it.mangaEnabled && it.isConfigured } &&
            settingsRepository.hasUserKey()

    fun notePageTurn(chapterId: Long) {
        pageTurns[chapterId] = nowSeconds()
    }

    /**
     * Returns the page index to move to when the server holds a newer position from another device,
     * or null when the local position stands.
     */
    suspend fun pull(manga: Manga, chapter: Chapter, pageCount: Int): Int? {
        val settings = settingsRepository.currentSettings()
        if (!isEnabled || !settings.autoSyncEnabled || pageCount <= 0) return null
        val credentials = settingsRepository.credentials() ?: return null
        val document = documentId(manga, chapter) ?: return null

        val remote = api.getProgress(credentials, document) ?: return null
        val percentage = remote.percentage ?: return null
        if (remote.deviceId == settingsRepository.deviceId) return null
        val remoteSeconds = remote.timestamp ?: return null

        val state = loadState(chapter.id)
        val localSeconds = maxOf(
            toSeconds(chapter.lastModifiedAt),
            pageTurns[chapter.id] ?: 0L,
            state.lastServerTimestamp ?: 0L,
        )
        if (remoteSeconds <= localSeconds) return null

        val index = KosyncPagedProgress.pageIndex(remote.progress, percentage, pageCount) ?: return null
        saveState(chapter.id, state.copy(lastSyncedPage = index, lastServerTimestamp = remoteSeconds))
        pageTurns[chapter.id] = remoteSeconds
        logcat { "kosync: pulled page ${index + 1}/$pageCount for '${chapter.name}' from ${remote.device}" }
        return index.takeIf { it != chapter.lastPageRead.toInt() }
    }

    suspend fun push(manga: Manga, chapter: Chapter, pageIndex: Int, pageCount: Int) {
        val settings = settingsRepository.currentSettings()
        if (!isEnabled || !settings.pushEnabled || pageCount <= 0) return
        val credentials = settingsRepository.credentials() ?: return
        val state = loadState(chapter.id)
        if (state.lastSyncedPage == pageIndex) return
        val document = documentId(manga, chapter) ?: return

        val timestamp = api.putProgress(
            credentials = credentials,
            document = document,
            progress = KosyncPagedProgress.progress(pageIndex, pageCount),
            percentage = KosyncPagedProgress.percentage(pageIndex, pageCount),
            device = KosyncManager.DEVICE_NAME,
            deviceId = settingsRepository.deviceId,
            numericProgress = true,
        )
        saveState(chapter.id, loadState(chapter.id).copy(lastSyncedPage = pageIndex, lastServerTimestamp = timestamp))
        logcat { "kosync: pushed page ${pageIndex + 1}/$pageCount for '${chapter.name}'" }
    }

    /** The chapter's archive on disk, or null for online chapters and image folders. */
    private fun chapterFile(manga: Manga, chapter: Chapter): UniFile? {
        val source = sourceManager.getOrStub(manga.source)
        val file = if (source.isLocal()) {
            val (mangaDirName, chapterFileName) = chapter.url.split('/', limit = 2).takeIf { it.size == 2 }
                ?: return null
            localFileSystem.getFilesInMangaDirectory(mangaDirName).firstOrNull { it.name == chapterFileName }
        } else {
            downloadProvider.findChapterDirs(listOf(chapter), manga, source).second.firstOrNull()
        }
        return file?.takeIf { it.isFile }
    }

    /** KOReader's partial MD5 of the archive, cached per chapter until the file changes. */
    private suspend fun documentId(manga: Manga, chapter: Chapter): String? = withContext(ioDispatcher) {
        val file = chapterFile(manga, chapter) ?: return@withContext null
        val size = file.length()
        val modified = file.lastModified()
        val state = loadState(chapter.id)
        if (state.documentId != null && state.fileSize == size && state.fileModified == modified) {
            return@withContext state.documentId
        }
        val id = runCatching {
            file.filePath?.let { KosyncDocumentId.partialMd5(File(it)) }
                ?: file.openInputStream().use { KosyncDocumentId.partialMd5(it) }
        }.onFailure { logcat(LogPriority.WARN, it) { "kosync: could not hash ${file.name}" } }
            .getOrNull() ?: return@withContext null
        saveState(chapter.id, state.copy(documentId = id, fileSize = size, fileModified = modified))
        id
    }

    private suspend fun loadState(chapterId: Long): ChapterState = withContext(ioDispatcher) {
        mutex.withLock { allStates()[chapterId] ?: ChapterState() }
    }

    private suspend fun saveState(chapterId: Long, state: ChapterState) = withContext(ioDispatcher) {
        mutex.withLock {
            val all = allStates()
            all[chapterId] = state
            runCatching { stateFile.writeText(json.encodeToString(serializer, all)) }
                .onFailure { logcat(LogPriority.WARN, it) { "kosync: could not save manga state" } }
        }
        Unit
    }

    private fun allStates(): MutableMap<Long, ChapterState> = states ?: run {
        val loaded = runCatching { json.decodeFromString(serializer, stateFile.readText()) }
            .getOrDefault(emptyMap())
            .toMutableMap()
        states = loaded
        loaded
    }

    @Serializable
    private data class ChapterState(
        val documentId: String? = null,
        val fileSize: Long = -1,
        val fileModified: Long = -1,
        val lastSyncedPage: Int? = null,
        /** Server-assigned, unix seconds. */
        val lastServerTimestamp: Long? = null,
    )

    private companion object {
        const val STATE_FILE_NAME = "kosync_manga.json"
        val json = Json { ignoreUnknownKeys = true }
        val serializer = MapSerializer(Long.serializer(), ChapterState.serializer())

        fun nowSeconds(): Long = System.currentTimeMillis() / 1_000

        /** The chapters trigger stores seconds; anything that looks like milliseconds is scaled down. */
        fun toSeconds(timestamp: Long): Long = if (timestamp > 100_000_000_000L) timestamp / 1_000 else timestamp
    }
}
