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
    private val replay = KeyBinding(keyCode = KeyEvent.KEYCODE_BUTTON_Y, action = KeyAction.ReplaySubtitle.name)
    private val holdReplay = replay.copy(longPress = true, action = KeyAction.ToggleSubtitles.name)
    private val holdOnly = KeyBinding(
        keyCode = KeyEvent.KEYCODE_BUTTON_X,
        longPress = true,
        action = KeyAction.CycleSubtitle.name,
    )
    private val shift = KeyBinding(keyCode = KeyEvent.KEYCODE_BUTTON_L1, action = KeyAction.PreviousSubtitle.name)
    private val shiftedSeek = seek.copy(chordKeyCode = KeyEvent.KEYCODE_BUTTON_L1, argument = "60")
    private val shiftedMute = KeyBinding(
        keyCode = KeyEvent.KEYCODE_BUTTON_A,
        chordKeyCode = KeyEvent.KEYCODE_BUTTON_R1,
        action = KeyAction.MpvCommand.name,
        argument = "cycle mute",
    )
    private val bindings = listOf(pause, seek, ctrlH, replay, holdReplay, holdOnly, shift, shiftedSeek, shiftedMute)

    private val resolver = KeyResolver(longPressMillis = 500)

    private fun down(
        keyCode: Int,
        meta: Int = 0,
        repeat: Int = 0,
        bound: List<KeyBinding> = bindings,
        at: Long = 0,
    ) = resolver.onKey(bound, keyCode, meta, isDown = true, repeatCount = repeat, eventTime = at)

    private fun up(keyCode: Int, meta: Int = 0, bound: List<KeyBinding> = bindings, at: Long = 0) =
        resolver.onKey(bound, keyCode, meta, isDown = false, repeatCount = 0, eventTime = at)

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

    @Test
    fun `a short press of a key with a long press runs the short one on release`() {
        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_Y, at = 1000))
        assertEquals(KeyResult.Consumed(replay), up(KeyEvent.KEYCODE_BUTTON_Y, at = 1200))
    }

    @Test
    fun `a short press of a key with only a long press does nothing`() {
        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_X, at = 1000))
        assertEquals(KeyResult.Consumed(null), up(KeyEvent.KEYCODE_BUTTON_X, at = 1200))
    }

    @Test
    fun `a long press fires once while the key is still held`() {
        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_Y, at = 1000))
        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_Y, repeat = 1, at = 1400))
        assertEquals(KeyResult.Consumed(holdReplay), down(KeyEvent.KEYCODE_BUTTON_Y, repeat = 2, at = 1500))
        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_Y, repeat = 3, at = 1550))
        assertEquals(KeyResult.Consumed(null), up(KeyEvent.KEYCODE_BUTTON_Y, at = 1600))
    }

    @Test
    fun `a long press from a device that sends no repeats fires on release`() {
        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_Y, at = 1000))
        assertEquals(KeyResult.Consumed(holdReplay), up(KeyEvent.KEYCODE_BUTTON_Y, at = 1500))
    }

    @Test
    fun `a key pressed while its chord key is held runs the chord`() {
        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(KeyResult.Consumed(shiftedSeek), down(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(KeyResult.Consumed(shiftedSeek), down(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 1))
        assertEquals(KeyResult.Consumed(null), up(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(KeyResult.Consumed(null), up(KeyEvent.KEYCODE_BUTTON_L1))
    }

    @Test
    fun `a chord key let go on its own runs its own binding`() {
        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(KeyResult.Consumed(shift), up(KeyEvent.KEYCODE_BUTTON_L1))
    }

    @Test
    fun `a chord key with no binding of its own is still swallowed`() {
        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(KeyResult.Consumed(shiftedMute), down(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(KeyResult.Consumed(null), up(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(KeyResult.Consumed(null), up(KeyEvent.KEYCODE_BUTTON_R1))

        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(KeyResult.Consumed(null), up(KeyEvent.KEYCODE_BUTTON_R1))
    }

    @Test
    fun `without the chord key the plain binding runs`() {
        assertEquals(KeyResult.Consumed(seek), down(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(KeyResult.Pass, down(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test
    fun `a key held for a chord never counts as a long press`() {
        val bound = bindings + shift.copy(longPress = true, action = KeyAction.Back.name)

        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_L1, bound = bound, at = 1000))
        assertEquals(KeyResult.Consumed(shiftedSeek), down(KeyEvent.KEYCODE_DPAD_RIGHT, bound = bound, at = 1100))
        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_L1, repeat = 1, bound = bound, at = 1600))
        assertEquals(KeyResult.Consumed(null), up(KeyEvent.KEYCODE_BUTTON_L1, bound = bound, at = 1700))
    }

    @Test
    fun `a release runs the binding caught on the way down when the set changes`() {
        assertEquals(KeyResult.Consumed(null), down(KeyEvent.KEYCODE_BUTTON_Y, at = 1000))
        assertEquals(KeyResult.Consumed(replay), up(KeyEvent.KEYCODE_BUTTON_Y, bound = emptyList(), at = 1100))
    }
}
