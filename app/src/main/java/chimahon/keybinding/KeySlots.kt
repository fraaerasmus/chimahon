package chimahon.keybinding

/** The headings the settings screen lists its rows under. */
enum class KeyGroup { Playback, Subtitles, WordLookup, Popup, Other }

/** A row of the settings screen: one thing that can be done on one screen, and the keys that do it. */
data class KeySlot(val context: KeyContext, val action: KeyAction) {
    fun holds(binding: KeyBinding) = binding.action == action.name
}

fun List<KeyBinding>.keysOf(slot: KeySlot) = filter(slot::holds)

/**
 * This list once [slot]'s row is saved with [keys], each carrying its own argument. A key that did
 * something else before now does this, as a key can only do one thing.
 */
fun List<KeyBinding>.withSlot(slot: KeySlot, keys: List<KeyBinding>): List<KeyBinding> {
    val saved = keys.map { it.copy(action = slot.action.name) }
    return saved.fold(filterNot(slot::holds)) { bindings, key -> bindings.withBinding(key, replaced = null) }
}

/** How a key's argument reads on its cap, or null for an action that takes none. */
fun argumentLabel(action: KeyAction, argument: String): String? {
    if (!action.hasArgument) return null
    if (action == KeyAction.MpvCommand) {
        return if (argument.length > MPV_LABEL_LENGTH) argument.take(MPV_LABEL_LENGTH) + "…" else argument
    }
    val amount = argument.toIntOrNull() ?: return ""
    val signed = if (amount > 0) "+$amount" else "$amount"
    return if (action == KeyAction.SeekBy) "$signed s" else signed
}

private const val MPV_LABEL_LENGTH = 16

/** The rows to show, every action once for each screen it works on. */
fun keySlots(): Map<KeyGroup, List<KeySlot>> {
    fun rows(context: KeyContext, vararg actions: KeyAction) = actions.map { KeySlot(context, it) }

    val player = KeyContext.Player
    val lookup = KeyContext.PlayerLookup
    return mapOf(
        KeyGroup.Playback to rows(
            player,
            KeyAction.PlayPause,
            KeyAction.SeekBy,
            KeyAction.VolumeBy,
            KeyAction.BrightnessBy,
        ),
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
            KeyAction.MpvCommand,
        ),
        KeyGroup.Other to rows(player, KeyAction.Back, KeyAction.MpvCommand),
    )
}
