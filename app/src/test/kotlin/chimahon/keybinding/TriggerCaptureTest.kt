package chimahon.keybinding

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TriggerCaptureTest {

    private val capture = TriggerCapture()

    private fun down(keyCode: Int, meta: Int = 0) = capture.onKey(keyCode, meta, isDown = true)

    private fun up(keyCode: Int) = capture.onKey(keyCode, 0, isDown = false)

    @Test
    fun `one key pressed and let go is the trigger`() {
        assertFalse(down(KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(up(KeyEvent.KEYCODE_BUTTON_A))

        assertEquals(KeyTrigger(KeyEvent.KEYCODE_BUTTON_A), capture.trigger)
    }

    @Test
    fun `a key pressed with modifiers keeps them`() {
        assertFalse(down(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON))
        assertNull(capture.trigger)
        down(KeyEvent.KEYCODE_H, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_NUM_LOCK_ON)
        assertTrue(up(KeyEvent.KEYCODE_H))

        assertEquals(KeyTrigger(KeyEvent.KEYCODE_H, modifiers = KeyEvent.META_CTRL_ON), capture.trigger)
    }

    @Test
    fun `a second key pressed while the first is held makes a chord`() {
        down(KeyEvent.KEYCODE_BUTTON_L1)
        down(KeyEvent.KEYCODE_BUTTON_A)
        assertFalse(up(KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(up(KeyEvent.KEYCODE_BUTTON_L1))

        assertEquals(
            KeyTrigger(KeyEvent.KEYCODE_BUTTON_A, chordKeyCode = KeyEvent.KEYCODE_BUTTON_L1),
            capture.trigger,
        )
    }

    @Test
    fun `a held key repeating changes nothing`() {
        down(KeyEvent.KEYCODE_BUTTON_L1)
        down(KeyEvent.KEYCODE_BUTTON_L1)

        assertEquals(KeyTrigger(KeyEvent.KEYCODE_BUTTON_L1), capture.trigger)
    }

    @Test
    fun `a release with nothing caught does not finish`() {
        assertFalse(up(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test
    fun `key names read like the key`() {
        assertEquals("Button A", keyLabel("KEYCODE_BUTTON_A"))
        assertEquals("Button L1", keyLabel("KEYCODE_BUTTON_L1"))
        assertEquals("D-pad Left", keyLabel("KEYCODE_DPAD_LEFT"))
        assertEquals("Space", keyLabel("KEYCODE_SPACE"))
        assertEquals("1001", keyLabel("1001"))
    }

    @Test
    fun `a trigger reads as the keys pressed in order`() {
        assertEquals(
            "Ctrl + Shift + H",
            triggerLabel(KeyTrigger(1, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)) { "H" },
        )
        assertEquals("L1 + A", triggerLabel(KeyTrigger(1, chordKeyCode = 2)) { if (it == 1) "A" else "L1" })
    }
}
