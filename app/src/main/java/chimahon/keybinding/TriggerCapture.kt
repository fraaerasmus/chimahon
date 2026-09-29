package chimahon.keybinding

import android.view.KeyEvent

/** The keys of a binding, without what it does. */
data class KeyTrigger(
    val keyCode: Int,
    val modifiers: Int = 0,
    val chordKeyCode: Int? = null,
)

val KeyBinding.trigger get() = KeyTrigger(keyCode, modifiers, chordKeyCode)

/**
 * Builds a trigger out of the keys pressed while a binding is being set. A key pressed while
 * another is still held makes the held one its chord key.
 */
class TriggerCapture {

    private val held = mutableListOf<Int>()

    var trigger: KeyTrigger? = null
        private set

    /** True once a trigger is caught and every key is let go. */
    fun onKey(keyCode: Int, metaState: Int, isDown: Boolean): Boolean {
        if (keyCode in MODIFIER_KEYS) return false
        if (!isDown) {
            held -= keyCode
            return held.isEmpty() && trigger != null
        }
        if (keyCode in held) return false
        trigger = when (val chordKey = held.firstOrNull()) {
            null -> KeyTrigger(keyCode, modifiers = metaState and KEY_MODIFIER_MASK)
            else -> KeyTrigger(keyCode, chordKeyCode = chordKey)
        }
        held += keyCode
        return false
    }
}

/** A readable name out of what [KeyEvent.keyCodeToString] gives, such as KEYCODE_DPAD_LEFT. */
fun keyLabel(keyCodeName: String): String {
    return keyCodeName.removePrefix("KEYCODE_").split('_').joinToString(" ") { word ->
        if (word == "DPAD") "D-pad" else word.lowercase().replaceFirstChar(Char::uppercase)
    }
}

fun triggerLabel(trigger: KeyTrigger, keyName: (Int) -> String): String {
    return listOfNotNull(
        trigger.chordKeyCode?.let(keyName),
        "Ctrl".takeIf { trigger.modifiers and KeyEvent.META_CTRL_ON != 0 },
        "Alt".takeIf { trigger.modifiers and KeyEvent.META_ALT_ON != 0 },
        "Shift".takeIf { trigger.modifiers and KeyEvent.META_SHIFT_ON != 0 },
        keyName(trigger.keyCode),
    ).joinToString(" + ")
}
