package chimahon.custom.youtube

import android.webkit.CookieManager

/** The YouTube account signed in to the in-app browser, as far as its cookies tell. */
internal object YouTubeAccount {
    const val ORIGIN = "https://www.youtube.com"

    /** The cookie Google signs API requests with. Present only while an account is signed in. */
    fun sapisid(): String? = runCatching {
        CookieManager.getInstance().getCookie(ORIGIN)
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("SAPISID=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    val isSignedIn: Boolean get() = sapisid() != null
}
