package eu.kanade.tachiyomi.data.download

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.online.HttpSource
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter

/**
 * Fetches `.mokuro` files from mokuro.moe and copies them to the download folder
 * when a chapter download completes.
 */
class MokuroSidecarCopier(
    private val httpClient: OkHttpClient,
) {

    /**
     * Called after a download completes successfully.
     * Fetches the `.mokuro` file and saves it as a sidecar in the download folder.
     */
    fun onDownloadComplete(
        download: Download,
        chapterDir: UniFile?,
    ) {
        val source = download.source as? HttpSource ?: return
        val chapter = download.chapter
        val mokuroUrl = buildMokuroUrl(source, chapter) ?: return

        val mokuroContent = fetchMokuroContent(mokuroUrl) ?: return

        if (chapterDir == null || !chapterDir.exists()) return
        if (saveSidecar(chapterDir, mokuroContent)) {
            logcat { "MokuroSidecarCopier: saved .mokuro sidecar for chapter=${chapter.id}" }
        }
    }

    /** Whether the reader's sibling sidecar (`<chapter>.mokuro`) already exists for [chapterDir]. */
    fun hasSidecar(chapterDir: UniFile): Boolean {
        val (targetDir, sidecarName) = sidecarLocation(chapterDir) ?: return false
        return targetDir.findFile(sidecarName)?.isFile == true
    }

    /**
     * Writes [mokuroContent] where the reader looks for it: `<chapter>.mokuro` beside a CBZ, or
     * inside a chapter folder, with the chapter URL hash suffix stripped from the name.
     */
    fun saveSidecar(chapterDir: UniFile, mokuroContent: String): Boolean {
        val (targetDir, sidecarName) = sidecarLocation(chapterDir) ?: return false
        val sidecarFile = targetDir.findFile(sidecarName)
            ?: targetDir.createFile(sidecarName)
            ?: return false
        return try {
            sidecarFile.openOutputStream().use { output ->
                output.write(mokuroContent.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "MokuroSidecarCopier: failed to save ${sidecarFile.name}" }
            false
        }
    }

    private fun sidecarLocation(chapterDir: UniFile): Pair<UniFile, String>? {
        val isCbz = chapterDir.name?.endsWith(".cbz", ignoreCase = true) == true
        val targetDir = if (isCbz) chapterDir.parentFile ?: return null else chapterDir
        val chapterName = chapterDir.name ?: return null
        val sidecarBaseName = if (isCbz) chapterName.substringBeforeLast('.') else chapterName
        return targetDir to "${sidecarBaseName.removeChapterUrlHash()}.mokuro"
    }

    private fun buildMokuroUrl(source: HttpSource, chapter: Chapter): String? {
        if (!source.name.equals("Mokuro", ignoreCase = true)) return null

        val parts = chapter.url.split("|", limit = 2)
        if (parts.size != 2) return null
        val (seriesPath, volumeName) = parts

        return "https://mokuro.moe/mokuro-reader".toHttpUrl().newBuilder()
            .addPathSegment(seriesPath)
            .addPathSegment("$volumeName.mokuro")
            .build()
            .toString()
    }

    private fun fetchMokuroContent(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://mokuro.moe/catalog")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logcat(LogPriority.ERROR) { "MokuroSidecarCopier: failed to fetch .mokuro from $url (${response.code})" }
                    return null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "MokuroSidecarCopier: error fetching .mokuro from $url" }
            null
        }
    }

    private fun String.removeChapterUrlHash(): String {
        return replace(Regex("_[A-Za-z0-9]{6}$"), "")
    }
}
