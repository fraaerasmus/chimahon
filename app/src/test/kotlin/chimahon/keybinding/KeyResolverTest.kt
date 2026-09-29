package chimahon.keybinding

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeyResolverTest {

    private val pause = KeyBinding(keyCode = KeyEvent.KEYCODE_SPACE, action = KeyAction.PlayPause.name)
    private val seek = KeyBinding(
        keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
        action = KeyAction.SeekBy.name,
        argument = "5",
    )
    private val ctrlH = KeyBinding(
        keyCode = KeyEvent.KEYCODE_H,
        modifiers = KeyEvent.META_CTRL_ON,
        action = KeyAction.CycleSubtitle.name,
    )
    private val bindings = listOf(pause, seek, ctrlH)

    private val resolver = KeyResolver()

    private fun down(keyCode: Int, meta: Int = 0, repeat: Int = 0, bound: List<KeyBinding> = bindings) =
        resolver.onKey(bound, keyCode, meta, isDown = true, repeatCount = repeat)

    private fun up(keyCode: Int, meta: Int = 0, bound: List<KeyBinding> = bindings) =
        resolver.onKey(bound, keyCode, meta, isDown = false, repeatCount = 0)

    @Test
    fun `an unbound key passes on the way down and up`() {
        assertEquals(KeyResult.Pass, down(KeyEvent.KEYCODE_M))
        assertEquals(KeyResult.Pass, up(KeyEvent.KEYCODE_M))
    }

    @Test
    fun `a modifier key on its own passes`() {
        assertEquals(KeyResult.Pass, down(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON))
        assertEquals(KeyResult.Pass, up(KeyEvent.KEYCODE_CTRL_LEFT))
    }

    @Test
    fun `a bound key fires on the way down and swallows its release`() {
        assertEquals(KeyResult.Consumed(pause), down(KeyEvent.KEYCODE_SPACE))
        assertEquals(KeyResult.Consumed(null), up(KeyEvent.KEYCODE_SPACE))
    }

    @Test
    fun `holding a key repeats only a repeatable action`() {
        assertEquals(KeyResult.Consumed(seek), down(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(KeyResult.Consumed(seek), down(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 1))

        assertEquals(KeyResult.Consumed(pause), down(KeyEvent.KEYCODE_SPACE))
        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_SPACE, repeat = 1))
    }

    @Test
    fun `a release with no press behind it passes`() {
        assertEquals(KeyResult.Pass, up(KeyEvent.KEYCODE_SPACE))
        assertEquals(KeyResult.Pass, down(KeyEvent.KEYCODE_SPACE, repeat = 3))
    }

    @Test
    fun `modifiers have to match exactly`() {
        assertEquals(KeyResult.Pass, down(KeyEvent.KEYCODE_H))
        assertEquals(KeyResult.Pass, down(KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON))
        assertEquals(
            KeyResult.Consumed(ctrlH),
            down(KeyEvent.KEYCODE_H, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
        )
    }

    @Test
    fun `lock keys do not count as modifiers`() {
        assertEquals(
            KeyResult.Consumed(pause),
            down(KeyEvent.KEYCODE_SPACE, KeyEvent.META_NUM_LOCK_ON or KeyEvent.META_CAPS_LOCK_ON),
        )
    }

    @Test
    fun `letting go of the modifier first still swallows the release`() {
        assertEquals(KeyResult.Consumed(ctrlH), down(KeyEvent.KEYCODE_H, KeyEvent.META_CTRL_ON))
        assertEquals(KeyResult.Consumed(null), up(KeyEvent.KEYCODE_H))
    }

    @Test
    fun `the binding caught on the way down governs repeats when the set changes`() {
        val lookupSet = listOf(seek.copy(action = KeyAction.VolumeUp.name, argument = ""))

        assertEquals(KeyResult.Consumed(seek), down(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(KeyResult.Consumed(seek), down(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 1, bound = lookupSet))
        assertEquals(KeyResult.Consumed(null), up(KeyEvent.KEYCODE_DPAD_RIGHT, bound = lookupSet))
    }

    @Test
    fun `a binding with an unknown action is not a binding`() {
        val unknown = listOf(pause.copy(action = "FlyToTheMoon"))

        assertEquals(KeyResult.Pass, down(KeyEvent.KEYCODE_SPACE, bound = unknown))
    }

    @Test
    fun `a reset forgets held keys`() {
        assertEquals(KeyResult.Consumed(pause), down(KeyEvent.KEYCODE_SPACE))
        resolver.reset()

        assertEquals(KeyResult.Pass, up(KeyEvent.KEYCODE_SPACE))
    }
}
