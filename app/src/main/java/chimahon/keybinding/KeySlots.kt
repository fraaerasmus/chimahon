package chimahon.keybinding

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
fun argumentLabel(action: KeyAction, argument: String): String? = action.argument?.capLabel(argument)

/**
 * The rows to show: every action once for each screen it works on, under its heading. An action
 * every screen has goes under the heading that closes that screen's rows.
 */
fun keySlots(): Map<KeyGroup, List<KeySlot>> =
    KeyContext.entries
        .flatMap { context -> KeyAction.entries.filter { it.isFor(context) }.map { KeySlot(context, it) } }
        .groupBy { slot ->
            slot.action.group ?: when (slot.context) {
                KeyContext.Player -> KeyGroup.Other
                KeyContext.PlayerLookup -> KeyGroup.Popup
            }
        }
        .toSortedMap()
