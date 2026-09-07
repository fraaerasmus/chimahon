package eu.kanade.tachiyomi.data.upload

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.MokuroSidecarCopier
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

/**
 * Per-series opt-in upload of downloaded CBZ chapters to the server's drop folder over WebDAV,
 * followed by fetching the `.mokuro` OCR sidecar the server writes beside the archive.
 *
 * Only chapters stored as one CBZ take part; the server identifies a chapter by the partial MD5 of
 * the archive, and the bytes go up as they are on disk. Uploads and sidecar polling run as
 * WorkManager jobs so they survive the app being closed and retry on their own.
 */
class ServerUploadManager(
    private val context: Context,
    private val syncPreferences: SyncPreferences,
    private val downloadProvider: DownloadProvider,
    private val mokuroSidecarCopier: MokuroSidecarCopier,
    private val client: WebDavUploadClient = WebDavUploadClient(syncPreferences),
) {
    fun isEnabled(mangaId: Long): Boolean = syncPreferences.serverUploadEnabled(mangaId).get()

    fun enabledChanges(mangaId: Long): Flow<Boolean> = syncPreferences.serverUploadEnabled(mangaId).changes()

    /** Flips the series toggle, starting or cancelling its upload job; returns a message for the UI. */
    fun toggle(manga: Manga): String {
        val preference = syncPreferences.serverUploadEnabled(manga.id)
        if (preference.get()) {
            preference.set(false)
            ChapterUploadJob.cancel(context, manga.id)
            return "Stopped uploading ${manga.title} to the server"
        }
        if (!client.isConfigured()) {
            return "Set the WebDAV URL, username and password under Settings > Data and storage > Sync first"
        }
        preference.set(true)
        ChapterUploadJob.start(context, manga.id)
        return "Uploading the downloaded chapters of ${manga.title} to the server"
    }

    /** Called by the downloader once a chapter is on disk. */
    fun onDownloadComplete(download: Download, chapterFile: UniFile?) {
        if (!isEnabled(download.manga.id)) return
        if (!chapterFile.isSingleCbz()) {
            logcat { "ServerUpload: skipping chapter ${download.chapter.id}, not a single CBZ file" }
            return
        }
        ChapterUploadJob.start(context, download.manga.id, download.chapter.id)
    }

    /** The chapter's CBZ on disk, or null when it is not downloaded as one archive. */
    fun chapterFile(manga: Manga, chapter: Chapter, source: Source): UniFile? =
        downloadProvider.findChapterDir(chapter.name, chapter.scanlator, chapter.url, manga.ogTitle, source)
            ?.takeIf { it.isSingleCbz() }

    /**
     * Uploads one chapter unless the server already holds a file of the same size, then schedules
     * the sidecar fetch. Returns false when the chapter does not qualify. Throws on network errors.
     */
    suspend fun uploadChapter(manga: Manga, chapter: Chapter, source: Source): Boolean {
        val file = chapterFile(manga, chapter, source) ?: run {
            logcat { "ServerUpload: skipping '${chapter.name}', not a single CBZ file" }
            return false
        }
        val series = ServerUploadNaming.seriesFolder(manga)
        val stem = ServerUploadNaming.stem(manga, chapter)
        val url = client.fileUrl(series, "$stem.cbz")
        val size = file.length()

        client.ensureFolder(series)
        if (client.remoteSize(url) == size) {
            logcat { "ServerUpload: '$stem.cbz' already on the server with the same size" }
        } else {
            client.put(url, size) { file.openInputStream() }
            logcat { "ServerUpload: uploaded '$stem.cbz' ($size bytes)" }
        }
        MokuroSidecarFetchJob.start(context, manga.id, chapter.id)
        return true
    }

    enum class SidecarResult { SAVED, NOT_READY, SKIPPED }

    /** Fetches the server's `.mokuro` for the chapter and stores it as the reader's sibling sidecar. */
    suspend fun fetchSidecar(manga: Manga, chapter: Chapter, source: Source): SidecarResult {
        val file = chapterFile(manga, chapter, source) ?: return SidecarResult.SKIPPED
        if (mokuroSidecarCopier.hasSidecar(file)) return SidecarResult.SKIPPED
        val url = client.fileUrl(ServerUploadNaming.seriesFolder(manga), "${ServerUploadNaming.stem(manga, chapter)}.mokuro")
        val content = client.getText(url) ?: return SidecarResult.NOT_READY
        if (content.isBlank()) return SidecarResult.NOT_READY
        return if (mokuroSidecarCopier.saveSidecar(file, content)) {
            logcat { "ServerUpload: saved OCR sidecar for '${chapter.name}'" }
            SidecarResult.SAVED
        } else {
            logcat(LogPriority.WARN) { "ServerUpload: could not write the OCR sidecar for '${chapter.name}'" }
            SidecarResult.SKIPPED
        }
    }

    private fun UniFile?.isSingleCbz(): Boolean =
        this != null && isFile && name.orEmpty().endsWith(".cbz", ignoreCase = true)
}
