package chimahon.keybinding

import android.view.KeyEvent

sealed interface KeyResult {
    /** Not ours. The event carries on to whoever handled it before. */
    data object Pass : KeyResult

    /** Ours. [fire] is the binding to run now, or null when the event is only swallowed. */
    data class Consumed(val fire: KeyBinding?) : KeyResult
}

/**
 * Turns raw key events into bindings to run. It takes plain values instead of a [KeyEvent] so it
 * can be tested on the JVM.
 */
class KeyResolver {

    /** Keys whose press was consumed, with the binding that press matched. */
    private val held = mutableMapOf<Int, KeyBinding>()

    fun onKey(
        bindings: List<KeyBinding>,
        keyCode: Int,
        metaState: Int,
        isDown: Boolean,
        repeatCount: Int,
    ): KeyResult {
        if (keyCode in MODIFIER_KEYS) return KeyResult.Pass

        if (!isDown) {
            return if (held.remove(keyCode) != null) KeyResult.Consumed(null) else KeyResult.Pass
        }

        if (repeatCount > 0) {
            // The binding caught by the press governs, even if the bindings changed since.
            val binding = held[keyCode] ?: return KeyResult.Pass
            val repeats = KeyAction.fromName(binding.action)?.repeatable == true
            return KeyResult.Consumed(binding.takeIf { repeats })
        }

        val modifiers = metaState and KEY_MODIFIER_MASK
        val binding = bindings.firstOrNull {
            it.keyCode == keyCode &&
                it.modifiers == modifiers &&
                it.chordKeyCode == null &&
                !it.longPress &&
                KeyAction.fromName(it.action) != null
        } ?: return KeyResult.Pass
        held[keyCode] = binding
        return KeyResult.Consumed(binding)
    }

    fun reset() {
        held.clear()
    }

    private companion object {
        // KeyEvent.isModifierKey() is a method on the Android stub, which unit tests cannot call.
        val MODIFIER_KEYS = setOf(
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
    }
}
