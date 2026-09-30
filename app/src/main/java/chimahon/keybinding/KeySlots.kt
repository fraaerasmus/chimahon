package chimahon.keybinding

/** The headings the settings screen lists its rows under. */
enum class KeyGroup { Playback, Subtitles, WordLookup, Popup, Other }

/** A row of the settings screen: one thing that can be done on one screen, and the keys that do it. */
data class KeySlot(
    val context: KeyContext,
    val action: KeyAction,
    val argument: String = "",
) {
    fun holds(binding: KeyBinding) = binding.action == action.name && binding.argument == argument
}

fun List<KeyBinding>.keysOf(slot: KeySlot) = filter(slot::holds)

/**
 * This list once [slot]'s row is saved with [keys] and [argument]. A key that did something else
 * before now does this, as a key can only do one thing.
 */
fun List<KeyBinding>.withSlot(slot: KeySlot, keys: List<KeyBinding>, argument: String): List<KeyBinding> {
    val saved = keys.map { it.copy(action = slot.action.name, argument = argument) }
    return saved.fold(filterNot(slot::holds)) { bindings, key -> bindings.withBinding(key, replaced = null) }
}

/**
 * The rows to show for [bindings]. An action without an argument always has its row, so one with
 * no key yet can be given one. A seek, a volume step or an mpv command has a row for each
 * argument in use.
 */
fun keySlots(bindings: Map<KeyContext, List<KeyBinding>>): Map<KeyGroup, List<KeySlot>> {
    fun rows(context: KeyContext, vararg actions: KeyAction) = actions.map { KeySlot(context, it) }

    fun rowsInUse(context: KeyContext, action: KeyAction) = bindings[context].orEmpty()
        .filter { it.action == action.name }
        .map { it.argument }
        .distinct()
        .map { KeySlot(context, action, it) }

    val player = KeyContext.Player
    val lookup = KeyContext.PlayerLookup
    return mapOf(
        KeyGroup.Playback to rows(player, KeyAction.PlayPause) +
            rowsInUse(player, KeyAction.SeekBy).sortedBy { it.argument.toIntOrNull() ?: 0 } +
            rowsInUse(player, KeyAction.VolumeBy).sortedBy { it.argument.toIntOrNull() ?: 0 },
        KeyGroup.Subtitles to rows(
            player,
            KeyAction.PreviousSubtitle,
            KeyAction.NextSubtitle,
            KeyAction.ReplaySubtitle,
            KeyAction.ToggleSubtitles,
            KeyAction.CycleSubtitle,
            KeyAction.CycleSecondarySubtitle,
        ),
        KeyGroup.WordLookup to rows(player, KeyAction.StartWordCursor) +
            rows(lookup, KeyAction.CursorPrevious, KeyAction.CursorNext, KeyAction.OpenPopup),
        KeyGroup.Popup to rows(
            lookup,
            KeyAction.PreviousEntry,
            KeyAction.NextEntry,
            KeyAction.ScrollUp,
            KeyAction.ScrollDown,
            KeyAction.PlayWordAudio,
            KeyAction.MineEntry,
            KeyAction.Back,
        ) + rowsInUse(lookup, KeyAction.MpvCommand),
        KeyGroup.Other to rows(player, KeyAction.Back) + rowsInUse(player, KeyAction.MpvCommand),
    )
}
