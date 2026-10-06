package chimahon.keybinding

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.ui.player.Dialogs
import eu.kanade.tachiyomi.ui.player.Panels
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.Sheets
import `is`.xyz.mpv.MPVLib
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.absoluteValue
import kotlin.math.sign

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
    /** The bindings as they are now, so an edit made while the player is open applies. */
    private val bindings: (KeyContext) -> List<KeyBinding>,
) {
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
        val bindings = bindings(if (viewModel.wordCursor.isActive) KeyContext.PlayerLookup else KeyContext.Player)
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
        val action = KeyAction.fromName(binding.action)?.takeIf { it.accepts(binding.argument) } ?: return
        // Zero only for an action whose argument is not a number, and those do not read it.
        val step = binding.argument.toIntOrNull() ?: 0
        when (action) {
            KeyAction.StartWordCursor -> wordCursor.start()
            KeyAction.Word -> wordCursor.move(step)
            KeyAction.OpenPopup -> wordCursor.openPopup()
            KeyAction.Entry -> wordCursor.runInPopup(PopupKeyScripts.entry(step))
            KeyAction.Scroll -> wordCursor.runInPopup(PopupKeyScripts.scroll(step))
            KeyAction.PlayWordAudio -> wordCursor.runInPopup(PopupKeyScripts.PLAY_WORD_AUDIO)
            KeyAction.MineEntry -> wordCursor.runInPopup(PopupKeyScripts.MINE_ENTRY)
            KeyAction.PlayPause -> viewModel.pauseUnpause()
            KeyAction.SeekBy -> viewModel.seekBy(step, viewModel.gesturePreferences.playerSmoothSeek().get())
            KeyAction.VolumeBy -> changeVolume(step)
            KeyAction.BrightnessBy -> {
                // The same scale as the swipe: 0 to 1 for the window, in steps of 5%.
                viewModel.changeBrightnessTo(viewModel.currentBrightness.value + step * 0.05f)
                viewModel.displayBrightnessSlider()
            }
            KeyAction.SubtitleLine -> repeat(step.absoluteValue) { viewModel.seekToAdjacentSubtitle(step > 0) }
            KeyAction.ReplaySubtitle -> viewModel.replayCurrentSubtitle()
            KeyAction.ToggleSubtitles -> viewModel.setSubtitlesVisible(!viewModel.subtitlesVisible.value)
            KeyAction.SubtitleTrack -> viewModel.cycleSubtitle(secondary = false, by = step)
            KeyAction.SecondarySubtitleTrack -> viewModel.cycleSubtitle(secondary = true, by = step)
            KeyAction.Back -> goBack()
            KeyAction.MpvCommand -> {
                val command = tokenizeMpvCommand(binding.argument)
                if (command.isNotEmpty()) MPVLib.command(command.toTypedArray())
            }
        }
    }

    companion object {
        /** The controller for the player activity, with back going where the system back key goes. */
        fun forActivity(
            activity: ComponentActivity,
            viewModel: PlayerViewModel,
            preferences: KeyBindingPreferences = Injekt.get(),
        ): PlayerKeyController {
            val current = KeyContext.entries.associateWith { preferences.bindings(it).stateIn(activity.lifecycleScope) }
            return PlayerKeyController(
                viewModel = viewModel,
                bindings = { current.getValue(it).value },
                goBack = {
                    // Sheets and popups close through the dispatcher. With none open its own fallback
                    // would skip onBackPressed, which is where leaving into picture in picture lives.
                    if (activity.onBackPressedDispatcher.hasEnabledCallbacks()) {
                        activity.onBackPressedDispatcher.onBackPressed()
                    } else {
                        @Suppress("DEPRECATION")
                        activity.onBackPressed()
                    }
                },
            )
        }
    }

    private fun changeVolume(steps: Int) {
        // The player moves one step at a time, keeping its boost past full volume in order.
        repeat(steps.absoluteValue) { viewModel.changeVolumeBy(steps.sign) }
        viewModel.displayVolumeSlider()
    }
}
