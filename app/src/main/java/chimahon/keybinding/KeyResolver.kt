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
 *
 * A key with nothing but a plain binding runs it on the way down. A key that also has a long press,
 * or that other bindings hold as their chord key, waits for its release to learn which was meant.
 */
class KeyResolver(private val longPressMillis: Long = 500) {

    /** A press that was consumed, with the bindings it matched on the way down. */
    private class Press(
        val short: KeyBinding?,
        val long: KeyBinding?,
        val waitsForRelease: Boolean,
        val downTime: Long,
        /** Set once the press ran its long press or served as a chord key. Its release does nothing. */
        var spent: Boolean = false,
    )

    private val held = mutableMapOf<Int, Press>()

    fun onKey(
        bindings: List<KeyBinding>,
        keyCode: Int,
        metaState: Int,
        isDown: Boolean,
        repeatCount: Int,
        eventTime: Long,
    ): KeyResult {
        if (keyCode in MODIFIER_KEYS) return KeyResult.Pass

        if (!isDown) {
            val press = held.remove(keyCode) ?: return KeyResult.Pass
            if (!press.waitsForRelease || press.spent) return KeyResult.Consumed(null)
            return KeyResult.Consumed(if (press.isLong(eventTime)) press.long else press.short)
        }

        if (repeatCount > 0) {
            // The bindings caught by the press govern, even if the set changed since.
            val press = held[keyCode] ?: return KeyResult.Pass
            if (!press.waitsForRelease) {
                val repeats = press.short?.let { KeyAction.fromName(it.action)?.repeatable } == true
                return KeyResult.Consumed(press.short.takeIf { repeats })
            }
            if (press.spent || !press.isLong(eventTime)) return KeyResult.Consumed(null)
            press.spent = true
            return KeyResult.Consumed(press.long)
        }

        val modifiers = metaState and KEY_MODIFIER_MASK
        val matches = bindings.filter {
            it.keyCode == keyCode && it.modifiers == modifiers && KeyAction.fromName(it.action) != null
        }

        val chord = matches.firstOrNull { it.chordKeyCode != null && it.chordKeyCode in held }
        if (chord != null) {
            held.getValue(chord.chordKeyCode!!).spent = true
            held[keyCode] = Press(short = chord, long = null, waitsForRelease = false, downTime = eventTime)
            return KeyResult.Consumed(chord)
        }

        val short = matches.firstOrNull { it.chordKeyCode == null && !it.longPress }
        val long = matches.firstOrNull { it.chordKeyCode == null && it.longPress }
        val isChordKey = bindings.any { it.chordKeyCode == keyCode && KeyAction.fromName(it.action) != null }
        if (short == null && long == null && !isChordKey) return KeyResult.Pass

        val waitsForRelease = long != null || isChordKey
        held[keyCode] = Press(short, long, waitsForRelease, downTime = eventTime)
        return KeyResult.Consumed(short.takeIf { !waitsForRelease })
    }

    fun reset() {
        held.clear()
    }

    private fun Press.isLong(eventTime: Long) = long != null && eventTime - downTime >= longPressMillis

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
