package chimahon.custom.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import chimahon.anki.AnkiProfile
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import kotlinx.coroutines.withTimeoutOrNull
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** The dictionary profile that lookups on the playing video use. */
@Composable
fun rememberPlayerLookupProfile(viewModel: PlayerViewModel): AnkiProfile {
    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val anime by viewModel.currentAnime.collectAsState()
    val source by viewModel.currentSource.collectAsState()
    return remember(anime?.id, source?.id, source?.lang) {
        dictionaryPreferences.profileResolver.resolve(
            animeId = anime?.id ?: 0L,
            sourceId = source?.id ?: 0L,
            sourceLang = source?.lang.orEmpty(),
        )
    }
}

/**
 * Pointer input for a line of text where only some places can be activated, such as the words of
 * a subtitle line.
 *
 * The touch is claimed before the gesture handler underneath sees it, but only when [hit] finds
 * something at that position. Anything else on the line is left unconsumed for the handler
 * (double tap, swipes). A release and a long press both call [onActivate]; a touch that turns
 * into a swipe is consumed by the gesture handler and cancels instead.
 */
fun <T : Any> claimTapWhereHit(
    vararg keys: Any?,
    hit: (Offset) -> T?,
    onActivate: (T) -> Unit,
): Modifier = Modifier.pointerInput(*keys) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val target = hit(down.position) ?: return@awaitEachGesture
        down.consume()
        // Let the down finish its passes, or our own consumption reads as a cancel.
        awaitPointerEvent(PointerEventPass.Final)

        var cancelled = false
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation().also { cancelled = it == null }
        }
        if (cancelled) return@awaitEachGesture
        up?.consume()
        onActivate(target)
    }
}
