package chimahon.keybinding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeySlotsTest {

    @Test
    fun `every action has one row for each screen it works on`() {
        val slots = keySlots().values.flatten()

        KeyAction.entries.forEach { action ->
            KeyContext.entries.filter { action.isFor(it) }.forEach { context ->
                assertEquals(1, slots.count { it.context == context && it.action == action }, "$context $action")
            }
        }
        assertEquals(slots.size, slots.toSet().size)
    }

    @Test
    fun `a row holds the keys of its action, whatever their arguments`() {
        val back = KeyBinding(keyCode = 1, action = KeyAction.SeekBy.name, argument = "-5")
        val forward = KeyBinding(keyCode = 2, action = KeyAction.SeekBy.name, argument = "5")
        val pause = KeyBinding(keyCode = 3, action = KeyAction.PlayPause.name)

        val keys = listOf(back, forward, pause).keysOf(KeySlot(KeyContext.Player, KeyAction.SeekBy))

        assertEquals(listOf(back, forward), keys)
    }

    @Test
    fun `saving a row keeps each key's own argument`() {
        val one = KeyBinding(keyCode = 1, action = KeyAction.SeekBy.name, argument = "5")
        val two = KeyBinding(keyCode = 2, action = KeyAction.SeekBy.name, argument = "-5")
        val pause = KeyBinding(keyCode = 3, action = KeyAction.PlayPause.name)
        val slot = KeySlot(KeyContext.Player, KeyAction.SeekBy)

        val saved = listOf(one, pause, two).withSlot(slot, keys = listOf(one.copy(argument = "10"), two))

        assertEquals(listOf(pause, one.copy(argument = "10"), two), saved)
    }

    @Test
    fun `saving a row drops the keys taken off it`() {
        val space = KeyBinding(keyCode = 1, action = KeyAction.PlayPause.name)
        val a = KeyBinding(keyCode = 2, action = KeyAction.PlayPause.name)
        val slot = KeySlot(KeyContext.Player, KeyAction.PlayPause)

        assertEquals(listOf(a), listOf(space, a).withSlot(slot, keys = listOf(a)))
        assertEquals(emptyList<KeyBinding>(), listOf(space, a).withSlot(slot, keys = emptyList()))
    }

    @Test
    fun `a key given to a row is taken from the row that had it`() {
        val pause = KeyBinding(keyCode = 1, action = KeyAction.PlayPause.name)
        val replay = KeyBinding(keyCode = 2, action = KeyAction.ReplaySubtitle.name)
        val slot = KeySlot(KeyContext.Player, KeyAction.ReplaySubtitle)

        val saved = listOf(pause, replay).withSlot(slot, keys = listOf(replay, pause))

        assertEquals(listOf(replay, pause.copy(action = KeyAction.ReplaySubtitle.name)), saved)
        assertEquals(emptyList<KeyBinding>(), saved.keysOf(KeySlot(KeyContext.Player, KeyAction.PlayPause)))
    }

    @Test
    fun `a long press and a plain press of one key are two keys`() {
        val press = KeyBinding(keyCode = 1, action = KeyAction.PlayPause.name)
        val hold = press.copy(longPress = true)
        val slot = KeySlot(KeyContext.Player, KeyAction.ReplaySubtitle)

        val saved = listOf(press).withSlot(slot, keys = listOf(hold))

        assertEquals(listOf(press, hold.copy(action = KeyAction.ReplaySubtitle.name)), saved)
    }

    @Test
    fun `a key's argument reads as a signed amount on its cap`() {
        assertEquals("-5 s", argumentLabel(KeyAction.SeekBy, "-5"))
        assertEquals("+30 s", argumentLabel(KeyAction.SeekBy, "30"))
        assertEquals("+1", argumentLabel(KeyAction.VolumeBy, "1"))
        assertEquals("-2", argumentLabel(KeyAction.BrightnessBy, "-2"))
        assertEquals("cycle mute", argumentLabel(KeyAction.MpvCommand, "cycle mute"))
        assertEquals("show-text \"a lon…", argumentLabel(KeyAction.MpvCommand, "show-text \"a long message\" 3000"))
        assertEquals("", argumentLabel(KeyAction.SeekBy, "soon"))
        assertEquals(null, argumentLabel(KeyAction.PlayPause, ""))
    }
}
