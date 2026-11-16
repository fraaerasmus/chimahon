package chimahon.custom.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import chimahon.custom.lookup.extractOcrLookupSelection
import chimahon.keybinding.PlayerWordCursor
import chimahon.keybinding.WordCursor
import eu.kanade.tachiyomi.ui.player.controls.NativeSubtitleEvent
import eu.kanade.tachiyomi.ui.player.controls.NativeSubtitleGeometry
import eu.kanade.tachiyomi.ui.player.controls.NativeSubtitleRectangle
import eu.kanade.tachiyomi.ui.player.controls.SubtitleLookupRequest
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay

/**
 * The fork's part of looking up the subtitles mpv draws: the lookup starts at the beginning of the
 * tapped word, the key cursor walks the words of the line on screen, and the secondary line goes
 * with a mined card. Upstream's controls call these by their full name.
 */
internal object NativeSubtitleLookup {
    private const val PRIMARY = "sub-text-geometry"
    private const val SECONDARY = "secondary-sub-text-geometry"

    /**
     * In place of upstream's lookup string, which runs from the tapped character: the selection
     * starts where the scanner says the tapped word starts, as the fork's lookups on OCR text do.
     */
    fun startingAtWord(request: SubtitleLookupRequest, languageCode: String): SubtitleLookupRequest? {
        val selection = extractOcrLookupSelection(request.fullText, request.tapCharOffset, languageCode)
            ?: return null
        if (selection.text.isBlank()) return null
        return request.copy(lookupString = selection.text, charOffset = selection.startOffset)
    }

    /** The primary line on screen, the one the key cursor walks. Empty with none. */
    fun primaryText(): String = geometry(PRIMARY)?.primaryLine()?.text.orEmpty()

    /** The secondary subtitle on screen, sent with a mined card. */
    fun secondaryText(): String = MPVLib.getPropertyString("secondary-sub-text").orEmpty().trim()

    /** Whether a line reading [text] is still drawn, on either track. */
    fun isOnScreen(text: String): Boolean = drawn().any { (_, event) -> event.text == text }

    /** The middle of the character at [offset] of the line reading [text], in a [width] x [height] view. */
    fun pointAt(text: String, offset: Int, width: Int, height: Int): Offset? = drawn()
        .firstOrNull { (_, event) -> event.text == text }
        ?.let { (geometry, event) -> geometry.pointAt(event, offset, width, height) }

    /** The cursor's word as a request that only draws the highlight. The popup never gets this one. */
    fun highlight(cursor: WordCursor, width: Int, height: Int): SubtitleLookupRequest? = drawn()
        .firstOrNull { (_, event) -> event.text == cursor.text }
        ?.let { (geometry, event) ->
            geometry.highlight(event, cursor.word.start, cursor.word.length, width, height)
        }

    private fun geometry(property: String) = NativeSubtitleGeometry.parse(MPVLib.getPropertyString(property))

    private fun drawn(): List<Pair<NativeSubtitleGeometry, NativeSubtitleEvent>> =
        listOf(PRIMARY, SECONDARY).flatMap { property ->
            geometry(property)?.let { geometry -> geometry.events.map { geometry to it } }.orEmpty()
        }
}

/** The line a cursor starts on: the one mpv drew last, as a tap tries that one first. */
internal fun NativeSubtitleGeometry.primaryLine(): NativeSubtitleEvent? =
    events.lastOrNull { it.text.isNotBlank() && it.spans.isNotEmpty() }

internal fun NativeSubtitleGeometry.pointAt(event: NativeSubtitleEvent, offset: Int, width: Int, height: Int): Offset? {
    if (w <= 0 || h <= 0 || width <= 0 || height <= 0) return null
    val span = event.spans.firstOrNull { offset >= it.start && offset < it.end && it.w > 0 && it.h > 0 }
        ?: return null
    return span.rect(width.toFloat() / w, height.toFloat() / h).center
}

internal fun NativeSubtitleGeometry.highlight(
    event: NativeSubtitleEvent,
    start: Int,
    length: Int,
    width: Int,
    height: Int,
): SubtitleLookupRequest? {
    if (w <= 0 || h <= 0 || width <= 0 || height <= 0) return null
    val scaleX = width.toFloat() / w
    val scaleY = height.toFloat() / h
    val rectangles = event.spans
        .filter { it.start >= 0 && it.end in (it.start + 1)..event.text.length && it.w > 0 && it.h > 0 }
        .map { NativeSubtitleRectangle(it.start, it.end, it.rect(scaleX, scaleY)) }
    if (rectangles.isEmpty()) return null
    return SubtitleLookupRequest(
        lookupString = "",
        fullText = event.text,
        charOffset = start,
        tapCharOffset = start,
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
        matchedCharCount = length,
        nativeRectangles = rectangles,
    )
}

/**
 * Connects the key cursor to the subtitles mpv draws. A key asking for the popup looks the word up
 * as a tap on its first character would, through [tap], upstream's tap handler. mpv says nothing
 * when its line changes, so while a cursor is up the line is looked for, and a cursor whose line
 * has gone is dropped.
 */
@Composable
internal fun NativeWordCursorEffects(
    wordCursor: PlayerWordCursor,
    cursor: WordCursor?,
    tap: (Float, Float, Int, Int) -> Boolean,
) {
    val view = LocalView.current
    val currentTap by rememberUpdatedState(tap)
    val openAt by wordCursor.openAt.collectAsState()
    LaunchedEffect(openAt) {
        val offset = openAt ?: return@LaunchedEffect
        wordCursor.openAt.value = null
        val point = NativeSubtitleLookup.pointAt(wordCursor.lineText, offset, view.width, view.height)
            ?: return@LaunchedEffect
        currentTap(point.x, point.y, view.width, view.height)
    }
    LaunchedEffect(cursor?.text) {
        val text = cursor?.text ?: return@LaunchedEffect
        while (NativeSubtitleLookup.isOnScreen(text)) delay(LINE_CHECK_MS)
        wordCursor.onSubtitleText(NativeSubtitleLookup.primaryText())
    }
}

private const val LINE_CHECK_MS = 250L
