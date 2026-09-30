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

    /** Where in the subtitle the open popup looks up, or null with no popup open. */
    private var popupOffset: Int? = null

    /** Whether the player was paused before a tap opened the popup. */
    private var popupWasPaused = false

    init {
        // A cursor is for a line that is standing still. Playback started by a touch drops it, or
        // its keys would go on moving a highlight instead of seeking. With the popup open the
        // cursor stays, as adding a card can run the player for a moment to record it.
        viewModel.viewModelScope.launch {
            viewModel.paused.collect { paused ->
                if (!paused && popupOffset == null) _cursor.value = null
            }
        }
    }

    /** True while keys belong to the lookup and not to the player. */
    val isActive get() = _cursor.value != null || popupOffset != null

    /** Pauses and puts the cursor on the first word of the subtitle on screen. */
    fun start() {
        val text = subtitleText
        if (text.isBlank() || isActive) return
        val wasPaused = viewModel.paused.value
        viewModel.pause()
        viewModel.viewModelScope.launch {
            val started = WordCursor.start(text, words(text), wasPaused).takeIf { subtitleText == text }
            if (started == null && !wasPaused) viewModel.unpause()
            _cursor.value = started
        }
    }

    fun move(by: Int) {
        val cursor = _cursor.value
        if (cursor != null) {
            move(cursor, by)
            return
        }
        // The popup was opened by a tap, so there is no cursor yet. It starts on the tapped word.
        val offset = popupOffset ?: return
        val text = subtitleText
        val wasPaused = popupWasPaused
        viewModel.viewModelScope.launch {
            val words = words(text)
            if (subtitleText != text || popupOffset == null || _cursor.value != null) return@launch
            WordCursor.at(text, words, offset, wasPaused)?.let { move(it, by) }
        }
    }

    private fun move(from: WordCursor, by: Int) {
        val moved = from.moved(by)
        _cursor.value = moved
        // At either end the word stays the same, and asking for it again would close the popup.
        if (popupOffset != null && moved.index != from.index) openAt.value = moved.word.start
    }

    private suspend fun words(text: String) = withContext(Dispatchers.IO) {
        cursorWords(text, profile().languageCode, ::measure)
    }

    fun openPopup() {
        // Asking for the word the popup already shows would close it, as a second tap does.
        if (popupOffset != null) return
        openAt.value = _cursor.value?.word?.start
    }

    /** Takes the cursor away and lets the player carry on, unless it was paused to begin with. */
    fun end() {
        val ended = _cursor.value ?: return
        _cursor.value = null
        if (!ended.wasPaused) viewModel.unpause()
    }

    fun runInPopup(script: String) {
        if (popupOffset != null) _popupScripts.tryEmit(script)
    }

    /** Called by the subtitle layer with the line it shows. A cursor on another line is dropped. */
    fun onSubtitleText(text: String) {
        subtitleText = text
        if (_cursor.value?.text != text) _cursor.value = null
    }

    /**
     * Called by the player controls with where the popup looks up, or null once it is closed, and
     * whether the player was paused before it opened. However it was closed, the cursor goes too.
     */
    fun onPopup(offset: Int?, wasPaused: Boolean) {
        val closed = popupOffset != null && offset == null
        popupOffset = offset
        // With a cursor the player is paused by the cursor, which says nothing about before.
        if (_cursor.value == null) popupWasPaused = wasPaused
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
