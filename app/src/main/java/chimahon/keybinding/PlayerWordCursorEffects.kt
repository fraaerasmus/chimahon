package chimahon.keybinding

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keys bound to the popup act on its page. A lookup inside the popup can open a second popup
 * over it, which takes the focus, and the keys are then meant for that one.
 */
@Composable
fun PopupKeyScriptsEffect(wordCursor: PlayerWordCursor, webView: WebView?) {
    if (webView == null) return
    val rootView = LocalView.current.rootView
    LaunchedEffect(webView) {
        wordCursor.popupScripts.collect { script ->
            val focused = (rootView.findFocus() as? WebView)?.takeIf { it.isShown }
            (focused ?: webView).evaluateJavascript(script, null)
        }
    }
}
