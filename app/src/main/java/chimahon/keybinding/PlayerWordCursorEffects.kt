package chimahon.keybinding

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult

/**
 * A key asked for the popup: [open] looks the word at the given offset up, as a tap on its first
 * character would. Does nothing for a line without the key cursor.
 */
@Composable
fun WordCursorOpenEffect(
    wordCursor: PlayerWordCursor?,
    subtitleText: String,
    textLayout: TextLayoutResult?,
    textLayerOrigin: Offset,
    open: (layout: TextLayoutResult, offset: Int) -> Unit,
) {
    if (wordCursor == null) return
    val openAt by wordCursor.openAt.collectAsState()
    LaunchedEffect(openAt, textLayout, textLayerOrigin) {
        val offset = openAt?.takeIf { it in subtitleText.indices } ?: return@LaunchedEffect
        val layout = textLayout ?: return@LaunchedEffect
        wordCursor.openAt.value = null
        open(layout, offset)
    }
}

/**
 * Keys bound to the popup act on its page. A lookup inside the popup can open a second popup
 * over it, which takes the focus, and the keys are then meant for that one.
 */
@Composable
fun PopupKeyScriptsEffect(wordCursor: PlayerWordCursor, webView: WebView) {
    val rootView = LocalView.current.rootView
    LaunchedEffect(webView) {
        wordCursor.popupScripts.collect { script ->
            val focused = (rootView.findFocus() as? WebView)?.takeIf { it.isShown }
            (focused ?: webView).evaluateJavascript(script, null)
        }
    }
}
