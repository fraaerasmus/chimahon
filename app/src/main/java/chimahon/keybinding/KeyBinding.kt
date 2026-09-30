package chimahon.keybinding

import android.view.KeyEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A screen with its own set of bindings. Each set is stored under its own preference. */
enum class KeyContext(val prefKey: String) {
    Player("player"),

    /** The player while a word of the subtitle is picked out or its dictionary popup is open. */
    PlayerLookup("player_lookup"),
}

/** [context] is the one screen the action works on. Null is every screen. */
enum class KeyAction(
    val context: KeyContext?,
    val repeatable: Boolean = false,
    val hasArgument: Boolean = false,
) {
    PlayPause(KeyContext.Player),
    SeekBy(KeyContext.Player, repeatable = true, hasArgument = true),
    VolumeBy(KeyContext.Player, repeatable = true, hasArgument = true),
    BrightnessBy(KeyContext.Player, repeatable = true, hasArgument = true),
    SubtitleLine(KeyContext.Player, hasArgument = true),
    ReplaySubtitle(KeyContext.Player),
    ToggleSubtitles(KeyContext.Player),
    SubtitleTrack(KeyContext.Player, hasArgument = true),
    SecondarySubtitleTrack(KeyContext.Player, hasArgument = true),
    StartWordCursor(KeyContext.Player),
    Word(KeyContext.PlayerLookup, repeatable = true, hasArgument = true),
    OpenPopup(KeyContext.PlayerLookup),
    Entry(KeyContext.PlayerLookup, repeatable = true, hasArgument = true),
    Scroll(KeyContext.PlayerLookup, repeatable = true, hasArgument = true),
    PlayWordAudio(KeyContext.PlayerLookup),
    MineEntry(KeyContext.PlayerLookup),
    Back(null),
    MpvCommand(null, hasArgument = true),
    ;

    fun isFor(context: KeyContext) = this.context == null || this.context == context

    /** Whether [argument] is one this action can run with. A number is a signed step. */
    fun accepts(argument: String): Boolean = when {
        this == MpvCommand -> argument.isNotBlank()
        hasArgument -> argument.toIntOrNull().let { it != null && it != 0 }
        else -> true
    }

    companion object {
        fun fromName(name: String): KeyAction? = entries.firstOrNull { it.name == name }
    }
}

/**
 * One hardware key, or combination, and what it does.
 *
 * [action] is the name of a [KeyAction]. It is stored as text so bindings restored from a newer
 * version, naming an action this version lacks, still decode. Those are kept and never fire.
 */
@Serializable
data class KeyBinding(
    val keyCode: Int,
    /** Any of [KeyEvent.META_SHIFT_ON], [KeyEvent.META_ALT_ON], [KeyEvent.META_CTRL_ON]. */
    val modifiers: Int = 0,
    /** A key that has to be held while [keyCode] is pressed. */
    val chordKeyCode: Int? = null,
    val longPress: Boolean = false,
    val action: String,
    val argument: String = "",
) {
    fun sameTrigger(other: KeyBinding): Boolean {
        return keyCode == other.keyCode &&
            modifiers == other.modifiers &&
            chordKeyCode == other.chordKeyCode &&
            longPress == other.longPress
    }
}

/**
 * This list with [binding] in the place of [replaced], or on the end when there is none. Another
 * binding on the same trigger gives way, as a trigger can only do one thing.
 */
fun List<KeyBinding>.withBinding(binding: KeyBinding, replaced: KeyBinding?): List<KeyBinding> {
    val index = if (replaced == null) -1 else indexOf(replaced)
    val kept = filterIndexed { i, other -> i != index && !other.sameTrigger(binding) }
    if (index == -1) return kept + binding
    val place = take(index).count { !it.sameTrigger(binding) }
    return kept.toMutableList().apply { add(place, binding) }
}

const val KEY_MODIFIER_MASK = KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON or KeyEvent.META_CTRL_ON

private val json = Json { ignoreUnknownKeys = true }

fun encodeKeyBindings(bindings: List<KeyBinding>): String = json.encodeToString(bindings)

fun decodeKeyBindings(text: String): List<KeyBinding> = json.decodeFromString<List<KeyBinding>>(text).map {
    // Each way of a pair was an action of its own before a press could take a signed step.
    val (action, step) = STEP_ACTIONS_BEFORE[it.action] ?: return@map it
    it.copy(action = action.name, argument = step)
}

private val STEP_ACTIONS_BEFORE = mapOf(
    "VolumeUp" to (KeyAction.VolumeBy to "1"),
    "VolumeDown" to (KeyAction.VolumeBy to "-1"),
    "PreviousSubtitle" to (KeyAction.SubtitleLine to "-1"),
    "NextSubtitle" to (KeyAction.SubtitleLine to "1"),
    "CycleSubtitle" to (KeyAction.SubtitleTrack to "1"),
    "CycleSecondarySubtitle" to (KeyAction.SecondarySubtitleTrack to "1"),
    "CursorPrevious" to (KeyAction.Word to "-1"),
    "CursorNext" to (KeyAction.Word to "1"),
    "PreviousEntry" to (KeyAction.Entry to "-1"),
    "NextEntry" to (KeyAction.Entry to "1"),
    "ScrollUp" to (KeyAction.Scroll to "-1"),
    "ScrollDown" to (KeyAction.Scroll to "1"),
)

// KeyEvent.isModifierKey() is a method on the Android stub, which unit tests cannot call.
internal val MODIFIER_KEYS = setOf(
    KeyEvent.KEYCODE_SHIFT_LEFT,
    KeyEvent.KEYCODE_SHIFT_RIGHT,
    KeyEvent.KEYCODE_ALT_LEFT,
    KeyEvent.KEYCODE_ALT_RIGHT,
    KeyEvent.KEYCODE_CTRL_LEFT,
    KeyEvent.KEYCODE_CTRL_RIGHT,
    KeyEvent.KEYCODE_META_LEFT,
    KeyEvent.KEYCODE_META_RIGHT,
    KeyEvent.KEYCODE_SYM,
    KeyEvent.KEYCODE_NUM,
    KeyEvent.KEYCODE_FUNCTION,
)
