package chimahon.keybinding

import android.app.Application
import androidx.lifecycle.viewModelScope
import chimahon.DictionaryRepository
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryPaths
import eu.kanade.tachiyomi.ui.dictionary.orderLookupResultsForDisplay
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.controls.SubtitleLookupRequest
import eu.kanade.tachiyomi.ui.reader.viewer.lookupWithSearchResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Looking a subtitle word up with keys alone. It holds the cursor and tells the player's subtitle
 * layer and dictionary popup, which own the screen, what to do.
 */
class PlayerWordCursor(
    private val viewModel: PlayerViewModel,
    private val context: Application = Injekt.get(),
    private val repository: DictionaryRepository = Injekt.get(),
    private val dictionaryPreferences: DictionaryPreferences = Injekt.get(),
) {
    private val _cursor = MutableStateFlow<WordCursor?>(null)
    val cursor = _cursor.asStateFlow()

    /** The offset in the subtitle the popup is asked to open at. The subtitle layer clears it. */
    val openAt = MutableStateFlow<Int?>(null)

    private val _popupScripts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val popupScripts = _popupScripts.asSharedFlow()

    private var subtitleText = ""
    private var popupOpen = false

    /** True while keys belong to the lookup and not to the player. */
    val isActive get() = _cursor.value != null || popupOpen

    /** Pauses and puts the cursor on the first word of the subtitle on screen. */
    fun start() {
        val text = subtitleText
        if (text.isBlank() || isActive) return
        val wasPaused = viewModel.paused.value
        viewModel.pause()
        viewModel.viewModelScope.launch {
            val words = withContext(Dispatchers.IO) { cursorWords(text, profile().languageCode, ::measure) }
            val started = WordCursor.start(text, words, wasPaused).takeIf { subtitleText == text }
            if (started == null && !wasPaused) viewModel.unpause()
            _cursor.value = started
        }
    }

    fun move(by: Int) {
        val moved = _cursor.value?.moved(by) ?: return
        _cursor.value = moved
        if (popupOpen) openAt.value = moved.word.start
    }

    fun openPopup() {
        // Asking for the word the popup already shows would close it, as a second tap does.
        if (popupOpen) return
        openAt.value = _cursor.value?.word?.start
    }

    /** Takes the cursor away and lets the player carry on, unless it was paused to begin with. */
    fun end() {
        val ended = _cursor.value ?: return
        _cursor.value = null
        if (!ended.wasPaused) viewModel.unpause()
    }

    fun runInPopup(script: String) {
        if (popupOpen) _popupScripts.tryEmit(script)
    }

    /** Called by the subtitle layer with the line it shows. A cursor on another line is dropped. */
    fun onSubtitleText(text: String) {
        subtitleText = text
        if (_cursor.value?.text != text) _cursor.value = null
    }

    /** Called by the player controls. However the popup was closed, the cursor goes with it. */
    fun onPopupOpen(open: Boolean) {
        val closed = popupOpen && !open
        popupOpen = open
        if (closed) end()
    }

    private fun profile() = dictionaryPreferences.profileResolver.resolve(
        animeId = viewModel.currentAnime.value?.id ?: 0L,
        sourceId = viewModel.currentSource.value?.id ?: 0L,
        sourceLang = viewModel.currentSource.value?.lang.orEmpty(),
    )

    /** What the popup would match and highlight for [query]. */
    private fun measure(query: String): String? {
        val profile = profile()
        val result = lookupWithSearchResolution(repository, query, getDictionaryPaths(context, profile), profile)
        return orderLookupResultsForDisplay(result.results, profile, context).firstOrNull()?.matched
    }
}

/** A request that only draws the highlight. The popup is given the real one, never this. */
internal fun WordCursor.highlightRequest() = SubtitleLookupRequest(
    lookupString = "",
    fullText = text,
    charOffset = word.start,
    tapCharOffset = word.start,
    lineText = "",
    lineIndex = 0,
    lineStartOffset = 0,
    anchorX = 0f,
    anchorY = 0f,
    anchorWidth = 0f,
    anchorHeight = 0f,
    lineLeft = 0f,
    lineTop = 0f,
    lineWidth = 0f,
    lineHeight = 0f,
    matchedCharCount = word.length,
)
