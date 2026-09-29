package chimahon.keybinding

import android.view.KeyEvent

fun defaultKeyBindings(context: KeyContext): List<KeyBinding> = when (context) {
    KeyContext.Player -> listOf(
        bind(KeyEvent.KEYCODE_SPACE, KeyAction.PlayPause),
        bind(KeyEvent.KEYCODE_BUTTON_A, KeyAction.PlayPause),
        bind(KeyEvent.KEYCODE_DPAD_LEFT, KeyAction.SeekBy, "-5"),
        bind(KeyEvent.KEYCODE_DPAD_RIGHT, KeyAction.SeekBy, "5"),
        bind(KeyEvent.KEYCODE_DPAD_UP, KeyAction.VolumeUp),
        bind(KeyEvent.KEYCODE_DPAD_DOWN, KeyAction.VolumeDown),
        bind(KeyEvent.KEYCODE_BUTTON_L1, KeyAction.PreviousSubtitle),
        bind(KeyEvent.KEYCODE_BUTTON_R1, KeyAction.NextSubtitle),
        bind(KeyEvent.KEYCODE_BUTTON_Y, KeyAction.ReplaySubtitle),
        bind(KeyEvent.KEYCODE_BUTTON_SELECT, KeyAction.ToggleSubtitles),
        bind(KeyEvent.KEYCODE_BUTTON_L2, KeyAction.CycleSubtitle),
        bind(KeyEvent.KEYCODE_BUTTON_R2, KeyAction.CycleSecondarySubtitle),
        bind(KeyEvent.KEYCODE_BUTTON_B, KeyAction.Back),
    )
}

private fun bind(keyCode: Int, action: KeyAction, argument: String = "") =
    KeyBinding(keyCode = keyCode, action = action.name, argument = argument)
