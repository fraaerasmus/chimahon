package chimahon.keybinding

import android.view.KeyEvent

fun defaultKeyBindings(context: KeyContext): List<KeyBinding> = when (context) {
    KeyContext.Player -> listOf(
        bind(KeyEvent.KEYCODE_SPACE, KeyAction.PlayPause),
        bind(KeyEvent.KEYCODE_BUTTON_A, KeyAction.PlayPause),
        bind(KeyEvent.KEYCODE_DPAD_LEFT, KeyAction.SeekBy, "-5"),
        bind(KeyEvent.KEYCODE_DPAD_RIGHT, KeyAction.SeekBy, "5"),
        bind(KeyEvent.KEYCODE_DPAD_UP, KeyAction.VolumeBy, "1"),
        bind(KeyEvent.KEYCODE_DPAD_DOWN, KeyAction.VolumeBy, "-1"),
        bind(KeyEvent.KEYCODE_BUTTON_L1, KeyAction.SubtitleLine, "-1"),
        bind(KeyEvent.KEYCODE_BUTTON_R1, KeyAction.SubtitleLine, "1"),
        bind(KeyEvent.KEYCODE_BUTTON_Y, KeyAction.ReplaySubtitle),
        bind(KeyEvent.KEYCODE_BUTTON_X, KeyAction.StartWordCursor),
        bind(KeyEvent.KEYCODE_ENTER, KeyAction.StartWordCursor),
        bind(KeyEvent.KEYCODE_BUTTON_SELECT, KeyAction.ToggleSubtitles),
        bind(KeyEvent.KEYCODE_BUTTON_L2, KeyAction.SubtitleTrack, "1"),
        bind(KeyEvent.KEYCODE_BUTTON_L2, KeyAction.SubtitleTrack, "-1", longPress = true),
        bind(KeyEvent.KEYCODE_BUTTON_R2, KeyAction.SecondarySubtitleTrack, "1"),
        bind(KeyEvent.KEYCODE_BUTTON_R2, KeyAction.SecondarySubtitleTrack, "-1", longPress = true),
        bind(KeyEvent.KEYCODE_BUTTON_B, KeyAction.Back),
    )
    KeyContext.PlayerLookup -> listOf(
        bind(KeyEvent.KEYCODE_DPAD_LEFT, KeyAction.Word, "-1"),
        bind(KeyEvent.KEYCODE_DPAD_RIGHT, KeyAction.Word, "1"),
        bind(KeyEvent.KEYCODE_BUTTON_A, KeyAction.OpenPopup),
        bind(KeyEvent.KEYCODE_ENTER, KeyAction.OpenPopup),
        bind(KeyEvent.KEYCODE_DPAD_CENTER, KeyAction.OpenPopup),
        bind(KeyEvent.KEYCODE_DPAD_UP, KeyAction.Entry, "-1"),
        bind(KeyEvent.KEYCODE_DPAD_DOWN, KeyAction.Entry, "1"),
        bind(KeyEvent.KEYCODE_BUTTON_L1, KeyAction.Scroll, "-1"),
        bind(KeyEvent.KEYCODE_BUTTON_R1, KeyAction.Scroll, "1"),
        bind(KeyEvent.KEYCODE_BUTTON_X, KeyAction.MineEntry),
        bind(KeyEvent.KEYCODE_BUTTON_Y, KeyAction.PlayWordAudio),
        bind(KeyEvent.KEYCODE_BUTTON_B, KeyAction.Back),
        bind(KeyEvent.KEYCODE_ESCAPE, KeyAction.Back),
    )
}

private fun bind(keyCode: Int, action: KeyAction, argument: String = "", longPress: Boolean = false) =
    KeyBinding(keyCode = keyCode, longPress = longPress, action = action.name, argument = argument)
