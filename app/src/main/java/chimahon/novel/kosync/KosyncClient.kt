package chimahon.novel.kosync

import chimahon.custom.core.CustomHttp
import chimahon.custom.core.ServerException
import chimahon.custom.core.withHttpScheme
import chimahon.custom.core.withTimeouts
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface KosyncApi {
    suspend fun register(credentials: KosyncCredentials)

    suspend fun authorize(credentials: KosyncCredentials)

    suspend fun getProgress(credentials: KosyncCredentials, document: String): KosyncRemoteProgress?

    /**
     * Returns the server-assigned timestamp (unix seconds) when the server reports one.
     * [numericProgress] sends [progress] as a JSON number, which is what KOReader does for page-based
     * documents; reflowable documents send the XPointer string.
     */
    suspend fun putProgress(
        credentials: KosyncCredentials,
        document: String,
        progress: String,
        percentage: Double,
        device: String,
        deviceId: String,
        numericProgress: Boolean = false,
    ): Long?
}

/**
 * The KOReader progress sync protocol (`plugins/kosync.koplugin/api.json`).
 *
 * Every request carries the `x-auth-user` / `x-auth-key` pair, where the key is the md5 of the
 * password rather than the password itself. Timestamps the server returns are unix seconds.
 */
class KosyncClient(
    client: OkHttpClient = CustomHttp.client,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    pullConnectTimeout: Duration = 3.seconds,
    pullReadTimeout: Duration = 4.seconds,
) : KosyncApi {
    private val client = client.withTimeouts(connect = 10.seconds, read = 15.seconds)

    // A pull holds a book or chapter back from opening, so it gives up sooner than a push, and
    // the call timeout bounds it as a whole however many addresses the server name resolves to.
    private val pullClient = client.withTimeouts(
        connect = pullConnectTimeout,
        read = pullReadTimeout,
        call = pullConnectTimeout + pullReadTimeout,
    )

    override suspend fun register(credentials: KosyncCredentials) {
        val payload = buildJsonObject {
            put("username", credentials.username)
            put("password", credentials.userKey)
        }
        request(credentials, "POST", "users/create", payload)
    }

    override suspend fun authorize(credentials: KosyncCredentials) {
        request(credentials, "GET", "users/auth")
    }

    override suspend fun getProgress(credentials: KosyncCredentials, document: String): KosyncRemoteProgress? {
        val body = request(
            credentials,
            "GET",
            "syncs/progress/$document",
            notFoundIsNull = true,
            client = pullClient,
        ) ?: return null
        return KosyncRemoteProgress(
            progress = body.string("progress"),
            percentage = body["percentage"]?.jsonPrimitive?.doubleOrNull,
            device = body.string("device"),
            deviceId = body.string("device_id"),
            timestamp = body["timestamp"]?.jsonPrimitive?.longOrNull,
        )
    }

    override suspend fun putProgress(
        credentials: KosyncCredentials,
        document: String,
        progress: String,
        percentage: Double,
        device: String,
        deviceId: String,
        numericProgress: Boolean,
    ): Long? {
        val payload = buildJsonObject {
            put("document", document)
            val page = progress.toLongOrNull()?.takeIf { numericProgress }
            if (page != null) put("progress", page) else put("progress", progress)
            put("percentage", percentage)
            put("device", device)
            put("device_id", deviceId)
        }
        return request(credentials, "PUT", "syncs/progress", payload)
            ?.get("timestamp")?.jsonPrimitive?.longOrNull
    }

    private suspend fun request(
        credentials: KosyncCredentials,
        method: String,
        path: String,
        payload: JsonObject? = null,
        notFoundIsNull: Boolean = false,
        client: OkHttpClient = this.client,
    ): JsonObject? = withContext(ioDispatcher) {
        val url = credentials.serverUrl.withHttpScheme().toHttpUrlOrNull()
            ?.newBuilder()?.addPathSegments(path)?.build()
            ?: throw ServerException("The server address is not a valid URL.")
        val request = Request.Builder()
            .url(url)
            // A byte body keeps the content type bare, as KOReader sends it; a string body
            // would have a charset appended.
            .method(method, payload?.toString()?.toByteArray(Charsets.UTF_8)?.toRequestBody(JSON))
            .headers(
                Headers.Builder()
                    .add("Accept", "application/vnd.koreader.v1+json")
                    .addUnsafeNonAscii("x-auth-user", credentials.username)
                    .add("x-auth-key", credentials.userKey)
                    .build(),
            )
            .build()
        client.newCall(request).await().use { response ->
            val status = response.code
            val text = response.body.string()
            when {
                response.isSuccessful ->
                    runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                        ?: if (text.isBlank()) {
                            JsonObject(emptyMap())
                        } else {
                            throw ServerException("Unexpected response from server.", status)
                        }
                status == 404 && notFoundIsNull -> null
                status == 401 -> throw ServerException("Incorrect username or password.", status)
                status == 402 -> throw ServerException("Username is already taken.", status)
                status == 403 -> throw ServerException("Unknown user.", status)
                else -> throw ServerException("Server returned HTTP $status.", status)
            }
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        val JSON = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
    }
}
