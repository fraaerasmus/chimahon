package chimahon.custom.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import eu.kanade.tachiyomi.ui.player.PlayerViewModel.SubtitleCue
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the subtitle side list on the active line. Upstream scrolled on every change of the
 * active position and fell back to the last line; this follows the cue itself and stays put
 * between cues.
 *
 * [scrollTo] is the list's own centred scroll, which lives in the upstream file.
 */
@Composable
fun FollowActiveCue(
    cues: List<SubtitleCue>,
    activeCueIndex: Int?,
    positionSeconds: () -> Double,
    scrollTo: suspend (Int) -> Unit,
) {
    val activePosition = cues.indexOfFirst { it.index == activeCueIndex }
    var hasFollowed by remember { mutableStateOf(false) }

    // Keyed on the cue itself: once the history is capped, a new line no longer moves the last position.
    LaunchedEffect(activeCueIndex, activePosition, cues.isEmpty()) {
        if (cues.isEmpty()) return@LaunchedEffect
        val target = when {
            activePosition >= 0 -> activePosition
            // No line is active between cues. Stay put rather than jumping away and back.
            hasFollowed -> return@LaunchedEffect
            else -> fallbackPosition(cues, positionSeconds())
        }
        hasFollowed = true
        scrollTo(target)
    }
}

/**
 * How far to scroll so an item sits in the middle of the viewport.
 *
 * Item offsets start where the top content padding ends, so the centre has to come from the viewport
 * offsets, which share that origin. The viewport height does not, and put the item on the bottom edge.
 */
fun centeredScrollDelta(
    itemOffset: Int,
    itemSize: Int,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
): Int {
    val viewportCenter = (viewportStartOffset + viewportEndOffset) / 2
    val itemCenter = itemOffset + itemSize / 2
    return itemCenter - viewportCenter
}

/**
 * Where to open the list while no line is active: the last line that started by [positionSeconds].
 */
fun fallbackPosition(cues: List<SubtitleCue>, positionSeconds: Double): Int {
    return cues.indexOfLast { it.positionSeconds <= positionSeconds }.coerceAtLeast(0)
}

/**
 * A tap that leaves long presses alone. `clickable` claims the touch and also fires when a long
 * hold is released, so selecting text in a row would seek. This leaves the touch unconsumed and
 * ignores long holds.
 */
@Composable
fun tapLeavingLongPress(onClick: () -> Unit): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    return Modifier
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation()
                }
                if (up != null) currentOnClick()
            }
        }
        .semantics {
            role = Role.Button
            onClick {
                currentOnClick()
                true
            }
        }
}
