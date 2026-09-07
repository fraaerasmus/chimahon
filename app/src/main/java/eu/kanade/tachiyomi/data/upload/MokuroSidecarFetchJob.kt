package eu.kanade.tachiyomi.data.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/**
 * Polls the server for the `.mokuro` sidecar of an uploaded chapter. The server sweeps every
 * five minutes and OCR takes a while, so the first look is after two minutes and each retry
 * waits twice as long as the last, giving up about six hours after the upload.
 */
class MokuroSidecarFetchJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val uploadManager: ServerUploadManager = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()

    override suspend fun doWork(): Result {
        val mangaId = inputData.getLong(KEY_MANGA_ID, -1L)
        val chapterId = inputData.getLong(KEY_CHAPTER_ID, -1L)
        val startedAt = inputData.getLong(KEY_STARTED_AT, System.currentTimeMillis())
        val manga = getManga.await(mangaId) ?: return Result.failure()
        val chapter = getChaptersByMangaId.await(mangaId).firstOrNull { it.id == chapterId } ?: return Result.failure()
        val source = sourceManager.getOrStub(manga.source)

        val outcome = try {
            uploadManager.fetchSidecar(manga, chapter, source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "ServerUpload: sidecar fetch failed for '${chapter.name}'" }
            ServerUploadManager.SidecarResult.NOT_READY
        }
        return when (outcome) {
            ServerUploadManager.SidecarResult.SAVED, ServerUploadManager.SidecarResult.SKIPPED -> Result.success()
            ServerUploadManager.SidecarResult.NOT_READY ->
                if (System.currentTimeMillis() - startedAt > GIVE_UP_AFTER_MILLIS) {
                    logcat { "ServerUpload: no OCR sidecar for '${chapter.name}' after six hours, giving up" }
                    Result.failure()
                } else {
                    Result.retry()
                }
        }
    }

    companion object {
        private const val TAG = "MokuroSidecarFetch"
        private const val KEY_MANGA_ID = "manga_id"
        private const val KEY_CHAPTER_ID = "chapter_id"
        private const val KEY_STARTED_AT = "started_at"
        private const val INITIAL_DELAY_MINUTES = 2L
        private const val GIVE_UP_AFTER_MILLIS = 6L * 60 * 60 * 1000

        fun start(context: Context, mangaId: Long, chapterId: Long) {
            val request = OneTimeWorkRequestBuilder<MokuroSidecarFetchJob>()
                .addTag(TAG)
                .setInputData(
                    workDataOf(
                        KEY_MANGA_ID to mangaId,
                        KEY_CHAPTER_ID to chapterId,
                        KEY_STARTED_AT to System.currentTimeMillis(),
                    ),
                )
                .setInitialDelay(INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("$TAG:$chapterId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
