package chimahon.keybinding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeySlotsTest {

    private val defaults = KeyContext.entries.associateWith { defaultKeyBindings(it) }

    @Test
    fun `every action is listed once for each screen it works on, keys or not`() {
        val slots = keySlots(emptyMap()).values.flatten()

        KeyAction.entries.filter { !it.hasArgument }.forEach { action ->
            KeyContext.entries.filter { action.isFor(it) }.forEach { context ->
                assertEquals(1, slots.count { it.context == context && it.action == action }, "$context $action")
            }
        }
    }

    @Test
    fun `an action with an argument is listed once for each argument in use`() {
        val slots = keySlots(defaults).getValue(KeyGroup.Playback)

        assertEquals(
            listOf(
                KeySlot(KeyContext.Player, KeyAction.PlayPause),
                KeySlot(KeyContext.Player, KeyAction.SeekBy, "-5"),
                KeySlot(KeyContext.Player, KeyAction.SeekBy, "5"),
                KeySlot(KeyContext.Player, KeyAction.VolumeBy, "-1"),
                KeySlot(KeyContext.Player, KeyAction.VolumeBy, "1"),
            ),
            slots,
        )
    }

    @Test
    fun `seeks are listed from furthest back to furthest forward`() {
        val bindings = listOf("30", "-5", "5", "-60").mapIndexed { index, seconds ->
            KeyBinding(keyCode = index, action = KeyAction.SeekBy.name, argument = seconds)
        }

        val seeks = keySlots(mapOf(KeyContext.Player to bindings)).getValue(KeyGroup.Playback)
            .filter { it.action == KeyAction.SeekBy }

        assertEquals(listOf("-60", "-5", "5", "30"), seeks.map { it.argument })
    }

    @Test
    fun `two keys for one mpv command share a row`() {
        val bindings = listOf(
            KeyBinding(keyCode = 1, action = KeyAction.MpvCommand.name, argument = "cycle mute"),
            KeyBinding(keyCode = 2, action = KeyAction.MpvCommand.name, argument = "cycle mute"),
            KeyBinding(keyCode = 3, action = KeyAction.MpvCommand.name, argument = "screenshot"),
        )

        val other = keySlots(mapOf(KeyContext.Player to bindings)).getValue(KeyGroup.Other)

        assertEquals(
            listOf(
                KeySlot(KeyContext.Player, KeyAction.Back),
                KeySlot(KeyContext.Player, KeyAction.MpvCommand, "cycle mute"),
                KeySlot(KeyContext.Player, KeyAction.MpvCommand, "screenshot"),
            ),
            other,
        )
    }

    @Test
    fun `a row holds the keys of its action and argument only`() {
        val back = KeyBinding(keyCode = 1, action = KeyAction.SeekBy.name, argument = "-5")
        val forward = KeyBinding(keyCode = 2, action = KeyAction.SeekBy.name, argument = "5")
        val pause = KeyBinding(keyCode = 3, action = KeyAction.PlayPause.name)

        val keys = listOf(back, forward, pause).keysOf(KeySlot(KeyContext.Player, KeyAction.SeekBy, "5"))

        assertEquals(listOf(forward), keys)
    }

    @Test
    fun `saving a row with a new argument changes it for its keys and no others`() {
        val one = KeyBinding(keyCode = 1, action = KeyAction.SeekBy.name, argument = "5")
        val two = KeyBinding(keyCode = 2, action = KeyAction.SeekBy.name, argument = "5")
        val back = KeyBinding(keyCode = 3, action = KeyAction.SeekBy.name, argument = "-5")
        val slot = KeySlot(KeyContext.Player, KeyAction.SeekBy, "5")

        val saved = listOf(one, back, two).withSlot(slot, keys = listOf(one, two), argument = "10")

        assertEquals(listOf(back, one.copy(argument = "10"), two.copy(argument = "10")), saved)
    }

    @Test
    fun `saving a row drops the keys taken off it`() {
        val space = KeyBinding(keyCode = 1, action = KeyAction.PlayPause.name)
        val a = KeyBinding(keyCode = 2, action = KeyAction.PlayPause.name)
        val slot = KeySlot(KeyContext.Player, KeyAction.PlayPause)

        assertEquals(listOf(a), listOf(space, a).withSlot(slot, keys = listOf(a), argument = ""))
        assertEquals(emptyList<KeyBinding>(), listOf(space, a).withSlot(slot, keys = emptyList(), argument = ""))
    }

    @Test
    fun `a key given to a row is taken from the row that had it`() {
        val pause = KeyBinding(keyCode = 1, action = KeyAction.PlayPause.name)
        val replay = KeyBinding(keyCode = 2, action = KeyAction.ReplaySubtitle.name)
        val slot = KeySlot(KeyContext.Player, KeyAction.ReplaySubtitle)

        val saved = listOf(pause, replay).withSlot(slot, keys = listOf(replay, pause), argument = "")

        assertEquals(
            listOf(replay, pause.copy(action = KeyAction.ReplaySubtitle.name)),
            saved,
        )
        assertEquals(emptyList<KeyBinding>(), saved.keysOf(KeySlot(KeyContext.Player, KeyAction.PlayPause)))
    }

    @Test
    fun `a long press and a plain press of one key are two keys`() {
        val press = KeyBinding(keyCode = 1, action = KeyAction.PlayPause.name)
        val hold = press.copy(longPress = true)
        val slot = KeySlot(KeyContext.Player, KeyAction.ReplaySubtitle)

        val saved = listOf(press).withSlot(slot, keys = listOf(hold), argument = "")

        assertEquals(listOf(press, hold.copy(action = KeyAction.ReplaySubtitle.name)), saved)
    }
}
