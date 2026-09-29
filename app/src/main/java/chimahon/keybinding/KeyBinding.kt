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

const val KEY_MODIFIER_MASK = KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON or KeyEvent.META_CTRL_ON

private val json = Json { ignoreUnknownKeys = true }

fun encodeKeyBindings(bindings: List<KeyBinding>): String = json.encodeToString(bindings)

fun decodeKeyBindings(text: String): List<KeyBinding> = json.decodeFromString(text)
