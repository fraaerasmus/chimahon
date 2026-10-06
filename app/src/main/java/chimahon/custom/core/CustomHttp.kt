package chimahon.custom.core

import okhttp3.OkHttpClient
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * HTTP for the servers the user runs themselves: KOReader sync, OPDS catalogs and the WebDAV
 * upload folder.
 *
 * This is deliberately not the app's `NetworkHelper` client. That one carries the WebView cookie
 * jar, a disk cache, the Cloudflare interceptor, the user's DNS-over-HTTPS choice (which cannot
 * resolve a name on a LAN or a VPN) and a call timeout that would cut a large transfer short.
 */
object CustomHttp {
    /** One connection pool and dispatcher for all of them; each caller sets its own [withTimeouts]. */
    val client: OkHttpClient by lazy { OkHttpClient.Builder().build() }
}

/** A client with these timeouts that shares this one's connections. [call] bounds a whole request. */
fun OkHttpClient.withTimeouts(
    connect: Duration,
    read: Duration,
    write: Duration = read,
    call: Duration? = null,
): OkHttpClient = newBuilder()
    .connectTimeout(connect.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    .readTimeout(read.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    .writeTimeout(write.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    .apply { if (call != null) callTimeout(call.inWholeMilliseconds, TimeUnit.MILLISECONDS) }
    .build()

/** The server answered, but not with what was asked for. [message] is written for the user. */
class ServerException(message: String, val statusCode: Int? = null) : IOException(message)

/** A typed address as a URL: `host:8083/opds` becomes `http://host:8083/opds`. */
fun String.withHttpScheme(): String = trim().let { if (it.contains("://")) it else "http://$it" }

/**
 * One line for the UI that names the cause: what the server said, or why it could not be reached.
 * The app's `await()` wraps network failures in a plain IOException, so the causes are checked too.
 */
fun Throwable.describeForUser(): String {
    val chain = generateSequence(this) { it.cause }.take(5).toList()
    chain.firstOrNull { it is ServerException }?.message?.let { return it }
    for (error in chain) {
        when (error) {
            is UnknownHostException -> return "Server not found. Check the address and that this device can reach it."
            is ConnectException -> return "Could not connect to the server."
            is InterruptedIOException -> return "The server did not respond in time."
        }
    }
    return chain.firstNotNullOfOrNull { error -> error.message?.takeIf { it.isNotBlank() } }
        ?: this::class.java.simpleName
}
