package eu.kanade.tachiyomi.ui.youtube

import android.os.Bundle
import android.webkit.WebView

internal object YouTubeBrowserSession {

    private var snapshot: Snapshot? = null

    fun capture(webView: WebView) {
        val currentUrl = webView.url
        val state = Bundle().takeIf { webView.saveState(it) != null }
        snapshot = if (state != null || currentUrl != null) {
            Snapshot(state, currentUrl)
        } else {
            null
        }
    }

    fun consume(): Snapshot? = snapshot.also { snapshot = null }

    fun clear() {
        snapshot = null
    }

    data class Snapshot(
        val state: Bundle?,
        val currentUrl: String?,
    )
}
