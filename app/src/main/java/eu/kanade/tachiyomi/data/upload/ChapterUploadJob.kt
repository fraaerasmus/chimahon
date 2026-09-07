package eu.kanade.tachiyomi.data.upload

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/** Uploads the downloaded CBZ chapters of one series, or one chapter of it, to the server. */
class ChapterUploadJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val uploadManager: ServerUploadManager = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle("Uploading chapters to the server")
            setSmallIcon(android.R.drawable.stat_sys_upload)
        }.build()
        return ForegroundInfo(
            Notifications.ID_SERVER_UPLOAD_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    override suspend fun doWork(): Result {
        val mangaId = inputData.getLong(KEY_MANGA_ID, -1L)
        val chapterId = inputData.getLong(KEY_CHAPTER_ID, -1L).takeIf { it > 0 }
        val manga = getManga.await(mangaId) ?: return Result.failure()
        if (!uploadManager.isEnabled(mangaId)) return Result.success()

        setForegroundSafely()
        val source = sourceManager.getOrStub(manga.source)
        val chapters = getChaptersByMangaId.await(mangaId).filter { chapterId == null || it.id == chapterId }
        var failed = 0
        for (chapter in chapters) {
            if (isStopped) return Result.retry()
            try {
                uploadManager.uploadChapter(manga, chapter, source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                logcat(LogPriority.WARN, e) { "ServerUpload: upload failed for '${chapter.name}'" }
            }
        }
        return when {
            failed == 0 -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        private const val TAG = "ServerUpload"
        private const val KEY_MANGA_ID = "manga_id"
        private const val KEY_CHAPTER_ID = "chapter_id"
        private const val MAX_ATTEMPTS = 4

        private fun uniqueName(mangaId: Long) = "$TAG:$mangaId"

        /** Uploads every downloaded chapter of the series, or only [chapterId] when given. */
        fun start(context: Context, mangaId: Long, chapterId: Long? = null) {
            val wifiOnly = Injekt.get<DownloadPreferences>().downloadOnlyOverWifi().get()
            val request = OneTimeWorkRequestBuilder<ChapterUploadJob>()
                .addTag(TAG)
                .setInputData(workDataOf(KEY_MANGA_ID to mangaId, KEY_CHAPTER_ID to (chapterId ?: -1L)))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueName(mangaId), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun cancel(context: Context, mangaId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(mangaId))
        }
    }
}
