package chimahon.custom.youtube

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.youtube.YouTubeSource
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The in-app YouTube browser's navigation: web back and forward, minimizing with the session
 * kept for next time, and leaving for good. Upstream's screen had one back action and a close.
 */
class YouTubeBrowserNavigation(
    private val navigator: Navigator,
    private val webView: () -> WebView?,
) {
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    private var retainSessionOnDispose = true

    fun update(view: WebView?) {
        canGoBack = view?.canGoBack() == true
        canGoForward = view?.canGoForward() == true
    }

    fun webBack() {
        val view = webView()
        if (view?.canGoBack() == true) {
            view.goBack()
        }
    }

    fun webForward() {
        val view = webView()
        if (view?.canGoForward() == true) {
            view.goForward()
        }
    }

    fun minimize() {
        navigator.pop()
    }

    fun exit() {
        retainSessionOnDispose = false
        YouTubeBrowserSession.clear()
        navigator.pop()
    }

    fun backOrMinimize() {
        val view = webView()
        if (view?.canGoBack() == true) {
            view.goBack()
        } else {
            minimize()
        }
    }

    /** Before the screen tears the WebView down: keep the login and, unless leaving for good, the session. */
    fun onDispose(view: WebView) {
        CookieManager.getInstance().flush()
        if (retainSessionOnDispose) {
            YouTubeBrowserSession.capture(view)
        }
    }
}

@Composable
fun YouTubeBrowserMinimizeButton(navigation: YouTubeBrowserNavigation) {
    IconButton(onClick = navigation::minimize) {
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            contentDescription = stringResource(AMR.strings.youtube_browser_minimize),
        )
    }
}

@Composable
fun YouTubeBrowserActions(navigation: YouTubeBrowserNavigation) {
    IconButton(
        onClick = navigation::webBack,
        enabled = navigation.canGoBack,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(MR.strings.action_webview_back),
        )
    }
    IconButton(
        onClick = navigation::webForward,
        enabled = navigation.canGoForward,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = stringResource(MR.strings.action_webview_forward),
        )
    }
    IconButton(onClick = navigation::exit) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = stringResource(AMR.strings.youtube_browser_exit),
        )
    }
}

/**
 * Which page a fresh browser opens on. Explicit browse targets win; otherwise the retained
 * session is restored, then the configured start page is loaded. A fresh session set to start
 * on Watch history falls back to Home when it turns out nobody is signed in.
 *
 * One instance per WebView.
 */
class YouTubeBrowserStart(private val preferences: YouTubeCustomPreferences) {
    private var pendingFreshHistoryLoginCheck = false
    private var clearHistoryOnHomeFinish = false

    /** Loads the first page into [view]. [onRestored] runs when a retained session was restored instead. */
    fun loadFirstPage(view: WebView, targetUrl: String?, listingQuery: String?, onRestored: () -> Unit) {
        val retainedSession = YouTubeBrowserSession.consume()
        val restoredState = retainedSession?.state?.let { state ->
            runCatching { view.restoreState(state) }.getOrNull()
        }
        when {
            targetUrl != null -> view.loadUrl(YouTubeSource.baseUrl + targetUrl)
            listingQuery == GetRemoteAnime.QUERY_LATEST ->
                view.loadUrl(YouTubeSource.baseUrl + YouTubeSource.SUBSCRIPTIONS_SUFFIX)
            restoredState != null -> onRestored()
            retainedSession?.currentUrl != null -> view.loadUrl(retainedSession.currentUrl)
            preferences.preferredStartPage == YouTubeCustomPreferences.START_PAGE_HISTORY -> {
                pendingFreshHistoryLoginCheck = true
                view.loadUrl(YOUTUBE_HISTORY_URL)
            }
            else -> view.loadUrl(YouTubeSource.baseUrl)
        }
    }

    /**
     * [currentWebView] is the screen's live WebView, to drop a late answer for one that is gone.
     * [onHistoryCleared] runs after the detour through the history page has been dropped from
     * the back stack.
     */
    fun onPageFinished(
        view: WebView?,
        url: String?,
        currentWebView: () -> WebView?,
        onHistoryCleared: () -> Unit,
    ) {
        if (clearHistoryOnHomeFinish && isYouTubeHomeUrl(url)) {
            clearHistoryOnHomeFinish = false
            view?.clearHistory()
            onHistoryCleared()
        }

        if (pendingFreshHistoryLoginCheck && isYouTubeHistoryUrl(url)) {
            pendingFreshHistoryLoginCheck = false
            checkYouTubeLoggedIn(view) { isLoggedIn ->
                val activeView = view
                if (
                    isLoggedIn == false &&
                    activeView != null &&
                    activeView === currentWebView() &&
                    isYouTubeHistoryUrl(activeView.url)
                ) {
                    clearHistoryOnHomeFinish = true
                    activeView.loadUrl(YOUTUBE_HOME_URL)
                }
            }
        }
    }

    private fun isYouTubeHistoryUrl(url: String?): Boolean {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return uri.isYouTubeHost() && uri.path.orEmpty().trimEnd('/') == "/feed/history"
    }

    private fun isYouTubeHomeUrl(url: String?): Boolean {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return uri.isYouTubeHost() && uri.path.orEmpty().trimEnd('/').isEmpty()
    }

    private fun Uri.isYouTubeHost(): Boolean {
        val normalizedHost = host.orEmpty().lowercase()
        return normalizedHost == "youtube.com" || normalizedHost.endsWith(".youtube.com")
    }

    private fun checkYouTubeLoggedIn(view: WebView?, onResult: (Boolean?) -> Unit) {
        if (view == null) {
            onResult(null)
            return
        }

        val script = """
(function() {
    try {
        if (!window.ytcfg || typeof window.ytcfg.get !== 'function') return null;
        var loggedIn = window.ytcfg.get('LOGGED_IN');
        if (loggedIn === undefined || loggedIn === null) return null;
        return loggedIn === true || loggedIn === 'true';
    } catch (e) {
        return null;
    }
})();
        """.trimIndent()
        view.evaluateJavascript(script) { value ->
            onResult(
                when (value) {
                    "true" -> true
                    "false" -> false
                    else -> null
                },
            )
        }
    }

    private companion object {
        const val YOUTUBE_HOME_URL = "https://m.youtube.com/"
        const val YOUTUBE_HISTORY_URL = "https://www.youtube.com/feed/history"
    }
}
