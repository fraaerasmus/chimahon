package eu.kanade.tachiyomi.ui.player.controls.components.panels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.PlayerViewModel.SubtitleCue
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.presentation.core.components.material.padding

@Composable
fun SubtitleListPanel(
    cues: ImmutableList<SubtitleCue>,
    activeCueIndex: Int?,
    onSelectCue: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    // Chimahon -->
    positionSeconds: () -> Double = { 0.0 },
    // Chimahon <--
) {
    BackHandler(onBack = onDismissRequest)

    Box(modifier = modifier.fillMaxSize()) {
        SubtitleSideList(
            cues = cues,
            activeCueIndex = activeCueIndex,
            onSelectCue = onSelectCue,
            positionSeconds = positionSeconds,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun SubtitleSideList(
    cues: ImmutableList<SubtitleCue>,
    activeCueIndex: Int?,
    onSelectCue: (Int) -> Unit,
    positionSeconds: () -> Double,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val width = (configuration.screenWidthDp.dp * 0.34f)
        .coerceAtLeast(280.dp).coerceAtMost(minOf(430.dp, configuration.screenWidthDp.dp - 24.dp))

    SubtitleCueLazyList(
        cues = cues,
        activeCueIndex = activeCueIndex,
        onSelectCue = onSelectCue,
        positionSeconds = positionSeconds,
        modifier = modifier
            .padding(end = 8.dp, top = 36.dp, bottom = 36.dp)
            .width(width)
            .fillMaxHeight(),
    )
}

@Composable
private fun SubtitleCueLazyList(
    cues: ImmutableList<SubtitleCue>,
    activeCueIndex: Int?,
    onSelectCue: (Int) -> Unit,
    positionSeconds: () -> Double,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Chimahon -->
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
        listState.animateScrollToCenteredItem(target)
    }
    // Chimahon <--

    if (cues.isEmpty()) {
        EmptySubtitleListMessage(modifier)
        return
    }

    BoxWithConstraints(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(vertical = maxHeight * 0.5f),
        ) {
            items(cues, key = { it.index }) { cue ->
                SubtitleCueSideRow(
                    cue = cue,
                    selected = cue.index == activeCueIndex,
                    onClick = { onSelectCue(cue.index) },
                )
            }
        }
    }
}

private suspend fun LazyListState.animateScrollToCenteredItem(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        scrollToItem(index)
        withFrameNanos { }
    }

    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    // Chimahon -->
    val scrollDelta = centeredScrollDelta(
        itemOffset = item.offset,
        itemSize = item.size,
        viewportStartOffset = layoutInfo.viewportStartOffset,
        viewportEndOffset = layoutInfo.viewportEndOffset,
    )
    // Chimahon <--

    if (scrollDelta != 0) {
        animateScrollBy(scrollDelta.toFloat())
    }
}

// Chimahon -->
/**
 * How far to scroll so an item sits in the middle of the viewport.
 *
 * Item offsets start where the top content padding ends, so the centre has to come from the viewport
 * offsets, which share that origin. The viewport height does not, and put the item on the bottom edge.
 */
internal fun centeredScrollDelta(
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
internal fun fallbackPosition(cues: List<SubtitleCue>, positionSeconds: Double): Int {
    return cues.indexOfLast { it.positionSeconds <= positionSeconds }.coerceAtLeast(0)
}
// Chimahon <--

@Composable
private fun SubtitleCueSideRow(
    cue: SubtitleCue,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = cue.text,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(activeLineColor(selected), RoundedCornerShape(2.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        style = subtitleLogTextStyle(),
        color = Color.White,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptySubtitleListMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Subtitle lines will appear here",
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            style = subtitleLogTextStyle().copy(textAlign = TextAlign.Center),
            color = Color.White.copy(alpha = 0.74f),
        )
    }
}

@Composable
private fun subtitleLogTextStyle(): TextStyle {
    return MaterialTheme.typography.bodyLarge.copy(
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.95f),
            blurRadius = 8f,
        ),
    )
}

@Composable
private fun activeLineColor(selected: Boolean): Color {
    return if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
    } else {
        Color.Transparent
    }
}
