package chimahon.novel.opds

import chimahon.custom.core.CustomHttp
import chimahon.custom.core.ServerException
import chimahon.custom.core.withTimeouts
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.net.URLDecoder
import kotlin.time.Duration.Companion.seconds

class OpdsClient(
    client: OkHttpClient = CustomHttp.client,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val client = client.withTimeouts(connect = 15.seconds, read = 60.seconds)

    suspend fun fetchFeed(catalog: OpdsCatalog, url: String): OpdsFeed = withContext(ioDispatcher) {
        get(catalog, url).use { OpdsFeedParser.parseFeed(url, it.body.bytes()) }
    }

    /** Resolves the feed's search template, fetching the OpenSearch description if needed. */
    suspend fun searchTemplate(catalog: OpdsCatalog, feed: OpdsFeed): String? = withContext(ioDispatcher) {
        feed.searchTemplate ?: feed.searchDescriptionHref?.let { href ->
            try {
                get(catalog, href).use { OpdsFeedParser.parseOpenSearchDescription(href, it.body.bytes()) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }

    data class Download(val file: File, val fileName: String)

    /**
     * Streams [url] to a temp file in [directory] untouched; the import path copies it verbatim.
     * [fallbackName] carries the extension to use when neither the server nor the URL names the file.
     * A cancelled download stops at the next buffer and leaves no file behind.
     */
    suspend fun download(
        catalog: OpdsCatalog,
        url: String,
        directory: File,
        fallbackName: String,
        format: OpdsFormat = OpdsFormat.EPUB,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): Download = withContext(ioDispatcher) {
        get(catalog, url).use { response ->
            val target = File(directory, "opds-${System.nanoTime()}.${format.extensions.first()}")
            try {
                val total = response.body.contentLength().takeIf { it > 0 }
                var downloaded = 0L
                response.body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
                val fallbackExtension = if (format.hasExtension(fallbackName)) {
                    fallbackName.substringAfterLast('.')
                } else {
                    format.extensions.first()
                }
                val name = (
                    fileNameFromDisposition(response.header("Content-Disposition"))
                        ?: url.substringBefore('?').substringAfterLast('/').takeIf { format.hasExtension(it) }
                        ?: fallbackName
                    )
                    .replace('/', '_')
                    .let { if (format.hasExtension(it)) it else "$it.$fallbackExtension" }
                Download(target, name)
            } catch (error: Exception) {
                target.delete()
                throw error
            }
        }
    }

    /** The successful response for [url]; the caller closes it. */
    private suspend fun get(catalog: OpdsCatalog, url: String): Response {
        val request = Request.Builder()
            .url(url.toHttpUrlOrNull() ?: throw ServerException("Not a valid address: $url"))
            .header("Accept", "application/atom+xml, application/epub+zip, application/x-cbz, */*")
            .apply {
                if (catalog.username.isNotBlank()) {
                    header("Authorization", Credentials.basic(catalog.username, catalog.password, Charsets.UTF_8))
                }
            }
            .build()
        val response = client.newCall(request).await()
        if (response.isSuccessful) return response
        response.close()
        val status = response.code
        throw ServerException(
            when (status) {
                401, 403 -> "Authentication failed (HTTP $status)."
                404 -> "Not found (HTTP 404)."
                else -> "Server returned HTTP $status."
            },
            status,
        )
    }

    companion object {
        fun fileNameFromDisposition(header: String?): String? {
            if (header.isNullOrBlank()) return null
            Regex("filename\\*\\s*=\\s*(?:UTF-8|utf-8)''([^;]+)").find(header)?.let { match ->
                return runCatching { URLDecoder.decode(match.groupValues[1].trim(), "UTF-8") }.getOrNull()
            }
            Regex("filename\\s*=\\s*\"([^\"]+)\"").find(header)?.let { return it.groupValues[1] }
            Regex("filename\\s*=\\s*([^;]+)").find(header)?.let { return it.groupValues[1].trim() }
            return null
        }
    }
}
