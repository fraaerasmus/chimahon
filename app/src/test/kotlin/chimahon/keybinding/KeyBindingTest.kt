package chimahon.keybinding

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class KeyBindingTest {

    @Test
    fun `bindings survive an encode and decode round trip`() {
        val bindings = listOf(
            KeyBinding(keyCode = KeyEvent.KEYCODE_SPACE, action = KeyAction.PlayPause.name),
            KeyBinding(
                keyCode = KeyEvent.KEYCODE_H,
                modifiers = KeyEvent.META_CTRL_ON,
                chordKeyCode = KeyEvent.KEYCODE_BUTTON_L1,
                longPress = true,
                action = KeyAction.MpvCommand.name,
                argument = "cycle sub",
            ),
        )

        assertEquals(bindings, decodeKeyBindings(encodeKeyBindings(bindings)))
    }

    @Test
    fun `a field from a newer version is ignored`() {
        val decoded = decodeKeyBindings("""[{"keyCode":62,"action":"PlayPause","device":"pad"}]""")

        assertEquals(listOf(KeyBinding(keyCode = 62, action = "PlayPause")), decoded)
    }

    @Test
    fun `keys saved before actions took a step read as a step of one`() {
        val old = listOf(
            "VolumeUp", "VolumeDown", "PreviousSubtitle", "NextSubtitle", "CycleSubtitle",
            "CycleSecondarySubtitle", "CursorPrevious", "CursorNext", "PreviousEntry", "NextEntry",
            "ScrollUp", "ScrollDown",
        )
        val decoded = decodeKeyBindings(
            old.mapIndexed { index, name -> """{"keyCode":$index,"action":"$name"}""" }.joinToString(",", "[", "]"),
        )

        assertEquals(
            listOf(
                "VolumeBy" to "1", "VolumeBy" to "-1", "SubtitleLine" to "-1", "SubtitleLine" to "1",
                "SubtitleTrack" to "1", "SecondarySubtitleTrack" to "1", "Word" to "-1", "Word" to "1",
                "Entry" to "-1", "Entry" to "1", "Scroll" to "-1", "Scroll" to "1",
            ),
            decoded.map { it.action to it.argument },
        )
        decoded.forEach { assertEquals(true, KeyAction.fromName(it.action) != null, it.action) }
    }

    @Test
    fun `an action from a newer version is kept but matches nothing`() {
        val decoded = decodeKeyBindings("""[{"keyCode":62,"action":"FlyToTheMoon"}]""")

        assertEquals("FlyToTheMoon", decoded.single().action)
        assertNull(KeyAction.fromName(decoded.single().action))
    }

    @Test
    fun `every context has defaults that name its own actions and do not collide`() {
        KeyContext.entries.forEach { context ->
            val defaults = defaultKeyBindings(context)

            defaults.forEach { assertEquals(true, KeyAction.fromName(it.action)?.isFor(context), it.action) }
            defaults.forEachIndexed { index, binding ->
                assertEquals(index, defaults.indexOfFirst { it.sameTrigger(binding) }, "$context $binding")
            }
        }
    }

    @Test
    fun `holding a track key by default steps back through the tracks`() {
        val defaults = defaultKeyBindings(KeyContext.Player)
        val l2 = defaults.filter { it.keyCode == KeyEvent.KEYCODE_BUTTON_L2 }

        assertEquals(
            listOf(false to "1", true to "-1"),
            l2.map { it.longPress to it.argument },
        )
        assertEquals(listOf(KeyAction.SubtitleTrack.name), l2.map { it.action }.distinct())
    }

    @Test
    fun `left and right seek the way they point`() {
        val defaults = defaultKeyBindings(KeyContext.Player)

        assertEquals("-5", defaults.single { it.keyCode == KeyEvent.KEYCODE_DPAD_LEFT }.argument)
        assertEquals("5", defaults.single { it.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT }.argument)
    }

    @Test
    fun `each context keeps its bindings under its own key`() {
        val preference = KeyBindingPreferences(InMemoryPreferenceStore()).bindings(KeyContext.Player)

        assertEquals("pref_key_bindings_player", preference.key())
        assertEquals(defaultKeyBindings(KeyContext.Player), preference.defaultValue())
    }

    @Test
    fun `a new binding goes on the end`() {
        val pause = KeyBinding(keyCode = 1, action = "PlayPause")
        val back = KeyBinding(keyCode = 2, action = "Back")

        assertEquals(listOf(pause, back), listOf(pause).withBinding(back, replaced = null))
    }

    @Test
    fun `an edited binding keeps its place`() {
        val pause = KeyBinding(keyCode = 1, action = "PlayPause")
        val back = KeyBinding(keyCode = 2, action = "Back")
        val edited = pause.copy(keyCode = 3)

        assertEquals(listOf(edited, back), listOf(pause, back).withBinding(edited, replaced = pause))
    }

    @Test
    fun `a binding takes over a trigger another one had`() {
        val pause = KeyBinding(keyCode = 1, action = "PlayPause")
        val back = KeyBinding(keyCode = 2, action = "Back")
        val replay = KeyBinding(keyCode = 3, action = "ReplaySubtitle")
        val bindings = listOf(pause, back, replay)

        val added = KeyBinding(keyCode = 1, action = "Back")
        assertEquals(listOf(back, replay, added), bindings.withBinding(added, replaced = null))

        val moved = replay.copy(keyCode = 1)
        assertEquals(listOf(back, moved), bindings.withBinding(moved, replaced = replay))
    }

    @Test
    fun `only an argument the action can use is accepted`() {
        assertEquals(true, KeyAction.SeekBy.accepts("-5"))
        assertEquals(false, KeyAction.SeekBy.accepts("0"))
        assertEquals(false, KeyAction.SeekBy.accepts("soon"))
        assertEquals(true, KeyAction.VolumeBy.accepts("2"))
        assertEquals(false, KeyAction.VolumeBy.accepts("0"))
        assertEquals(true, KeyAction.BrightnessBy.accepts("-1"))
        assertEquals(false, KeyAction.BrightnessBy.accepts(""))
        assertEquals(true, KeyAction.Entry.accepts("3"))
        assertEquals(false, KeyAction.SubtitleTrack.accepts("0"))
        assertEquals(true, KeyAction.MpvCommand.accepts("cycle sub"))
        assertEquals(false, KeyAction.MpvCommand.accepts("  "))
        assertEquals(true, KeyAction.PlayPause.accepts(""))
    }
}
