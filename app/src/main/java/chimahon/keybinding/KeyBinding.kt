package chimahon.keybinding

import android.view.KeyEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A screen with its own set of bindings. Each set is stored under its own preference. */
enum class KeyContext(val prefKey: String) {
    Player("player"),
}

enum class KeyAction(val repeatable: Boolean = false, val hasArgument: Boolean = false) {
    PlayPause,
    SeekBy(repeatable = true, hasArgument = true),
    VolumeUp(repeatable = true),
    VolumeDown(repeatable = true),
    PreviousSubtitle,
    NextSubtitle,
    ReplaySubtitle,
    ToggleSubtitles,
    CycleSubtitle,
    CycleSecondarySubtitle,
    Back,
    MpvCommand(hasArgument = true),
    ;

    /** Whether [argument] is one this action can run with. */
    fun accepts(argument: String): Boolean = when (this) {
        SeekBy -> argument.toIntOrNull().let { it != null && it != 0 }
        MpvCommand -> argument.isNotBlank()
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

fun decodeKeyBindings(text: String): List<KeyBinding> = json.decodeFromString(text)

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
