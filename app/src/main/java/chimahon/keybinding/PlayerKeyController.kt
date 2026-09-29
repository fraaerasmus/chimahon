package chimahon.keybinding

import android.view.KeyEvent
import eu.kanade.tachiyomi.ui.player.Dialogs
import eu.kanade.tachiyomi.ui.player.Panels
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.Sheets
import `is`.xyz.mpv.MPVLib
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class PlayerKeyGate { All, BackOnly, None }

/**
 * Which bindings apply right now. Sheets and panels share the player window and hold text fields,
 * so while one is open only back is taken and every other key reaches it.
 */
fun playerKeyGate(
    inPip: Boolean,
    ocrShown: Boolean,
    sheet: Sheets,
    panel: Panels,
    dialog: Dialogs,
): PlayerKeyGate = when {
    inPip || ocrShown -> PlayerKeyGate.None
    sheet != Sheets.None ||
        dialog != Dialogs.None ||
        (panel != Panels.None && panel != Panels.SubtitleSideList) -> PlayerKeyGate.BackOnly
    else -> PlayerKeyGate.All
}

/** Runs the player's key bindings. Keys it does not take are left for the player's own handling. */
class PlayerKeyController(
    private val viewModel: PlayerViewModel,
    private val goBack: () -> Unit,
    preferences: KeyBindingPreferences = Injekt.get(),
) {
    private val playerBindings = preferences.bindings(KeyContext.Player).get()
    private val lookupBindings = preferences.bindings(KeyContext.PlayerLookup).get()
    private val resolver = KeyResolver()

    /** True when the event was taken. */
    fun onKey(event: KeyEvent, inPip: Boolean): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val gate = playerKeyGate(
            inPip = inPip,
            ocrShown = viewModel.ocrScreenshot.value != null,
            sheet = viewModel.sheetShown.value,
            panel = viewModel.panelShown.value,
            dialog = viewModel.dialogShown.value,
        )
        val lookup = viewModel.wordCursor.isActive
        val bindings = if (lookup) lookupBindings else playerBindings
        val active = when (gate) {
            PlayerKeyGate.All -> bindings
            PlayerKeyGate.BackOnly -> bindings.filter { it.action == KeyAction.Back.name }
            PlayerKeyGate.None -> {
                resolver.reset()
                return false
            }
        }
        val result = resolver.onKey(
            bindings = active,
            keyCode = event.keyCode,
            metaState = event.metaState,
            isDown = event.action == KeyEvent.ACTION_DOWN,
            repeatCount = event.repeatCount,
            eventTime = event.eventTime,
        )
        if (result !is KeyResult.Consumed) return false
        result.fire?.let(::run)
        return true
    }

    private fun run(binding: KeyBinding) {
        val wordCursor = viewModel.wordCursor
        when (KeyAction.fromName(binding.action) ?: return) {
            KeyAction.StartWordCursor -> wordCursor.start()
            KeyAction.CursorPrevious -> wordCursor.move(-1)
            KeyAction.CursorNext -> wordCursor.move(1)
            KeyAction.OpenPopup -> wordCursor.openPopup()
            KeyAction.PreviousEntry -> wordCursor.runInPopup(PopupKeyScripts.PREVIOUS_ENTRY)
            KeyAction.NextEntry -> wordCursor.runInPopup(PopupKeyScripts.NEXT_ENTRY)
            KeyAction.ScrollUp -> wordCursor.runInPopup(PopupKeyScripts.SCROLL_UP)
            KeyAction.ScrollDown -> wordCursor.runInPopup(PopupKeyScripts.SCROLL_DOWN)
            KeyAction.PlayWordAudio -> wordCursor.runInPopup(PopupKeyScripts.PLAY_WORD_AUDIO)
            KeyAction.MineEntry -> wordCursor.runInPopup(PopupKeyScripts.MINE_ENTRY)
            KeyAction.PlayPause -> viewModel.pauseUnpause()
            KeyAction.SeekBy -> binding.argument.toIntOrNull()?.let {
                viewModel.seekBy(it, viewModel.gesturePreferences.playerSmoothSeek().get())
            }
            KeyAction.VolumeUp -> changeVolume(1)
            KeyAction.VolumeDown -> changeVolume(-1)
            KeyAction.PreviousSubtitle -> viewModel.seekToAdjacentSubtitle(forward = false)
            KeyAction.NextSubtitle -> viewModel.seekToAdjacentSubtitle(forward = true)
            KeyAction.ReplaySubtitle -> viewModel.replayCurrentSubtitle()
            KeyAction.ToggleSubtitles -> viewModel.setSubtitlesVisible(!viewModel.subtitlesVisible.value)
            KeyAction.CycleSubtitle -> viewModel.cycleSubtitle(secondary = false)
            KeyAction.CycleSecondarySubtitle -> viewModel.cycleSubtitle(secondary = true)
            KeyAction.Back -> goBack()
            KeyAction.MpvCommand -> {
                val command = tokenizeMpvCommand(binding.argument)
                if (command.isNotEmpty()) MPVLib.command(command.toTypedArray())
            }
        }
    }

    private fun changeVolume(by: Int) {
        viewModel.changeVolumeBy(by)
        viewModel.displayVolumeSlider()
    }
}
