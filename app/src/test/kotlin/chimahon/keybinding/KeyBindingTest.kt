package chimahon.keybinding

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun `an action from a newer version is kept but matches nothing`() {
        val decoded = decodeKeyBindings("""[{"keyCode":62,"action":"FlyToTheMoon"}]""")

        assertEquals("FlyToTheMoon", decoded.single().action)
        assertNull(KeyAction.fromName(decoded.single().action))
    }

    @Test
    fun `every context has defaults that name real actions and do not collide`() {
        KeyContext.entries.forEach { context ->
            val defaults = defaultKeyBindings(context)

            defaults.forEach { assertNotNull(KeyAction.fromName(it.action), it.action) }
            defaults.forEachIndexed { index, binding ->
                assertEquals(index, defaults.indexOfFirst { it.sameTrigger(binding) }, "$context $binding")
            }
        }
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
}
