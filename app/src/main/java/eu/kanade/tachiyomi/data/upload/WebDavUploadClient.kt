package eu.kanade.tachiyomi.data.upload

import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.network.await
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * The WebDAV calls the chapter upload needs, against the same server the WebDAV sync uses:
 * MKCOL, HEAD for an existence and size check, streaming PUT, and GET for the OCR sidecar.
 */
class WebDavUploadClient(
    private val settings: () -> Settings,
    private val client: OkHttpClient = defaultClient(),
) {
    constructor(syncPreferences: SyncPreferences) : this(
        settings = {
            Settings(
                baseUrl = syncPreferences.webDavUrl().get(),
                username = syncPreferences.webDavUsername().get(),
                password = syncPreferences.webDavPassword().get(),
                uploadFolder = syncPreferences.webDavUploadFolder().get(),
            )
        },
    )

    data class Settings(
        val baseUrl: String,
        val username: String,
        val password: String,
        val uploadFolder: String,
    ) {
        val isConfigured: Boolean
            get() = baseUrl.trim().startsWith("http") && username.isNotBlank() && password.isNotBlank()

        val folderSegments: List<String>
            get() = uploadFolder.split('/').map { it.trim() }.filter { it.isNotEmpty() }
    }

    class WebDavUploadException(message: String) : Exception(message)

    fun isConfigured(): Boolean = settings().isConfigured

    /** URL of a file under the upload folder; each segment is encoded on its own. */
    fun fileUrl(vararg segments: String): String {
        val current = settings()
        val builder = current.baseUrl.trim().trimEnd('/').toHttpUrl().newBuilder()
        (current.folderSegments + segments).forEach { builder.addPathSegment(it) }
        return builder.build().toString()
    }

    /** Creates the upload folder and [segments] under it, one MKCOL per level; existing levels are fine. */
    suspend fun ensureFolder(vararg segments: String) {
        val current = settings()
        val builder = current.baseUrl.trim().trimEnd('/').toHttpUrl().newBuilder()
        for (segment in current.folderSegments + segments) {
            builder.addPathSegment(segment)
            val url = builder.build().toString()
            val request = Request.Builder()
                .url(url)
                .method("MKCOL", null)
                .header("Authorization", credentials(current))
                .header("Content-Length", "0")
                .build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful && response.code != 405 && response.code != 409 && response.code != 301) {
                    throw WebDavUploadException("Could not create folder $url (HTTP ${response.code}).")
                }
            }
        }
    }

    /** Size of the remote file from a HEAD, null when it does not exist, -1 when the server sends no length. */
    suspend fun remoteSize(url: String): Long? {
        val request = Request.Builder().url(url).head().header("Authorization", credentials(settings())).build()
        client.newCall(request).await().use { response ->
            return when {
                response.code == 404 -> null
                response.isSuccessful -> response.header("Content-Length")?.toLongOrNull() ?: -1L
                else -> throw WebDavUploadException("HEAD $url failed (HTTP ${response.code}).")
            }
        }
    }

    /** Streams [size] bytes from [open] to [url]; the bytes go up exactly as read. */
    suspend fun put(url: String, size: Long, mediaType: MediaType = CBZ_MEDIA_TYPE, open: () -> InputStream) {
        val body = object : RequestBody() {
            override fun contentType(): MediaType = mediaType
            override fun contentLength(): Long = size
            override fun writeTo(sink: BufferedSink) {
                open().source().use { sink.writeAll(it) }
            }
        }
        val request = Request.Builder().url(url).put(body).header("Authorization", credentials(settings())).build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw WebDavUploadException("PUT $url failed (HTTP ${response.code}).")
        }
    }

    /** The text at [url], or null when the server has no such file yet. */
    suspend fun getText(url: String): String? {
        val request = Request.Builder().url(url).get().header("Authorization", credentials(settings())).build()
        client.newCall(request).await().use { response ->
            return when {
                response.code == 404 -> null
                response.isSuccessful -> response.body.string()
                else -> throw WebDavUploadException("GET $url failed (HTTP ${response.code}).")
            }
        }
    }

    private fun credentials(current: Settings): String = Credentials.basic(current.username.trim(), current.password)

    companion object {
        val CBZ_MEDIA_TYPE: MediaType = "application/x-cbz".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
