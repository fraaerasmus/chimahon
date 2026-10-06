package chimahon.novel.kosync

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import chimahon.novel.data.Bookmark
import chimahon.novel.ui.reader.NovelReaderActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * The novel reader's KOReader sync around leaving a book and coming back to it. The first open is
 * covered by [KosyncManager.pullOnOpen].
 *
 * The reader activity calls [onStart] and [onStop] from its own overrides, after `super`. This is
 * deliberately not a lifecycle observer: observers hear the stop before the activity's own onStop
 * has flushed the chapter rows the push reads.
 */
class KosyncReaderLifecycle(
    private val activity: ComponentActivity,
    private val title: () -> String,
    private val onPulled: (Bookmark) -> Unit,
) {
    private val manager: KosyncManager by lazy { Injekt.get() }
    private var stoppedOnce = false

    private fun bookDir(): File? =
        activity.intent.getStringExtra(NovelReaderActivity.EXTRA_BOOK_DIR)?.let(::File)

    /**
     * Coming back after a stop: another device may have pushed a newer position while this one
     * was away. The pull writes the DB rows, then [onPulled] moves the view there.
     */
    fun onStart() {
        if (!stoppedOnce) return
        if (!manager.canPull) return
        val bookDir = bookDir() ?: return
        val title = title()
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { manager.pull(bookDir, title) }.getOrNull() }
            val bookmark = (result as? KosyncResult.Pulled)?.bookmark ?: return@launch
            onPulled(bookmark)
        }
    }

    /** Leaving: the activity has just flushed the chapter rows, so the push sends the final position. */
    fun onStop() {
        stoppedOnce = true
        if (!manager.canPush) return
        val bookDir = bookDir() ?: return
        val title = title()
        closingScope.launch { runCatching { manager.push(bookDir, title) } }
    }

    private companion object {
        /** Outlives the activity so a closing push is not cancelled with it. */
        val closingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
