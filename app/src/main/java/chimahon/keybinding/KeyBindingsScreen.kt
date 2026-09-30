package chimahon.keybinding

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

/**
 * The player's key bindings, listed by what can be done. Each row is an action with the keys that
 * do it, and tapping one opens its keys.
 */
object KeyBindingsScreen : Screen() {

    private sealed interface Dialog {
        data class Keys(val slot: KeySlot) : Dialog
        data object Reset : Dialog
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.current
        val preferences = remember {
            val keyBindingPreferences = Injekt.get<KeyBindingPreferences>()
            KeyContext.entries.associateWith { keyBindingPreferences.bindings(it) }
        }
        val bindings = preferences.mapValues { it.value.collectAsState().value }
        var dialog by remember { mutableStateOf<Dialog?>(null) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_player_key_bindings),
                    navigateUp = backPress ?: { if (navigator.canPop) navigator.pop() },
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.key_bindings_reset),
                                    icon = Icons.Outlined.RestartAlt,
                                    onClick = { dialog = Dialog.Reset },
                                    enabled = preferences.any { bindings[it.key] != it.value.defaultValue() },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            LazyColumn(contentPadding = paddingValues) {
                item {
                    Text(
                        text = stringResource(MR.strings.key_bindings_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                    )
                }
                keySlots().forEach { (group, slots) ->
                    item(key = group.name) { GroupHeader(group) }
                    items(slots, key = { "${it.context}-${it.action}" }) { slot ->
                        SlotRow(
                            slot = slot,
                            keys = bindings.getValue(slot.context).keysOf(slot),
                            onClick = { dialog = Dialog.Keys(slot) },
                        )
                    }
                }
            }
        }

        when (val shown = dialog) {
            null -> {}
            is Dialog.Keys -> {
                val preference = preferences.getValue(shown.slot.context)
                KeySlotDialog(
                    slot = shown.slot,
                    bindings = bindings.getValue(shown.slot.context),
                    onDismissRequest = { dialog = null },
                    onSave = preference::set,
                )
            }
            Dialog.Reset -> AlertDialog(
                onDismissRequest = { dialog = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            preferences.values.forEach { it.delete() }
                            dialog = null
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_reset))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
                title = { Text(text = stringResource(MR.strings.key_bindings_reset)) },
                text = { Text(text = stringResource(MR.strings.key_bindings_reset_confirm)) },
            )
        }
    }
}

@Composable
private fun GroupHeader(group: KeyGroup) {
    Column(
        modifier = Modifier.padding(
            start = MaterialTheme.padding.medium,
            end = MaterialTheme.padding.medium,
            top = MaterialTheme.padding.medium,
            bottom = MaterialTheme.padding.extraSmall,
        ),
    ) {
        Text(
            text = stringResource(group.titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        // The arrows are listed under playback and again here. This says why that is no clash.
        if (group == KeyGroup.WordLookup) {
            Text(
                text = stringResource(MR.strings.key_group_lookup_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SlotRow(
    slot: KeySlot,
    keys: List<KeyBinding>,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = slotLabel(slot)) },
        trailingContent = {
            if (keys.isEmpty()) {
                Text(
                    text = stringResource(MR.strings.key_slot_not_set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    modifier = Modifier.widthIn(max = 200.dp),
                ) {
                    keys.forEach { KeyCap(it.trigger, it.longPress, argumentLabel(slot.action, it.argument)) }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * A key drawn as the cap of a key, with what it passes its action after it. A gamepad button
 * carries a gamepad, to tell A from the A key.
 */
@Composable
private fun KeyCap(trigger: KeyTrigger, longPress: Boolean, argument: String? = null) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.small, vertical = 2.dp),
        ) {
            val onGamepad = listOfNotNull(trigger.keyCode, trigger.chordKeyCode)
                .any { isGamepadButton(KeyEvent.keyCodeToString(it)) }
            if (onGamepad) {
                Icon(
                    imageVector = Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(text = keysLabel(trigger, longPress), style = MaterialTheme.typography.labelLarge)
            if (!argument.isNullOrEmpty()) {
                Text(
                    text = argument,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * The keys of one row. Nothing is saved until Save, so a key taken from another row by mistake is
 * undone with Cancel.
 */
@Composable
private fun KeySlotDialog(
    slot: KeySlot,
    bindings: List<KeyBinding>,
    onDismissRequest: () -> Unit,
    onSave: (List<KeyBinding>) -> Unit,
) {
    var keys by remember { mutableStateOf(bindings.keysOf(slot)) }
    val keysUsable = keys.all { slot.action.accepts(it.argument.trim()) }

    // A row with no key yet starts out waiting for one.
    var listening by remember { mutableStateOf(keys.isEmpty()) }
    var capture by remember { mutableStateOf(TriggerCapture()) }
    val focusRequester = remember { FocusRequester() }
    val otherRows = remember(bindings, slot) { bindings.filterNot(slot::holds) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = !listening && keysUsable,
                onClick = {
                    onSave(bindings.withSlot(slot, keys.map { it.copy(argument = it.argument.trim()) }))
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Column {
                Text(text = slotLabel(slot))
                Text(
                    text = stringResource(slot.context.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(MR.strings.key_slot_keys),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Each key carries its own argument, so the hint for it is given once, up here.
                if (slot.action.hasArgument) {
                    Text(
                        text = stringResource(slot.action.argumentSummaryRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (keys.isEmpty() && !listening) {
                    Text(
                        text = stringResource(MR.strings.key_slot_no_keys),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                keys.forEach { key ->
                    val takenFrom = otherRows.firstOrNull { it.sameTrigger(key) }
                    KeyRow(
                        key = key,
                        action = slot.action,
                        takenFrom = takenFrom?.let { bindingLabel(it, slot.context) },
                        onLongPressChange = { longPress ->
                            val changed = key.copy(longPress = longPress)
                            keys = keys.filterNot { it.sameTrigger(changed) }.map { if (it == key) changed else it }
                        },
                        onArgumentChange = { argument ->
                            keys = keys.map { if (it == key) it.copy(argument = argument) else it }
                        },
                        onRemove = { keys = keys - key },
                    )
                }

                if (listening) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                val pressed = event.nativeKeyEvent
                                // Back has to keep closing the dialog.
                                if (pressed.keyCode == KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                                val isDown = pressed.action == KeyEvent.ACTION_DOWN
                                if (isDown || pressed.action == KeyEvent.ACTION_UP) {
                                    val caught = capture.onKey(pressed.keyCode, pressed.metaState, isDown)
                                    val trigger = capture.trigger
                                    if (caught && trigger != null) {
                                        val key = KeyBinding(
                                            keyCode = trigger.keyCode,
                                            modifiers = trigger.modifiers,
                                            chordKeyCode = trigger.chordKeyCode,
                                            action = slot.action.name,
                                        )
                                        if (keys.none { it.sameTrigger(key) }) keys = keys + key
                                        listening = false
                                    }
                                }
                                true
                            }
                            .focusable(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = MaterialTheme.padding.medium),
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = MaterialTheme.padding.medium),
                            ) {
                                Text(
                                    text = stringResource(MR.strings.key_binding_key_listening),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(MR.strings.key_binding_key_listening_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { listening = false }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(MR.strings.action_cancel),
                                )
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            capture = TriggerCapture()
                            listening = true
                        },
                    ) {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                        Text(text = stringResource(MR.strings.key_slot_add_key))
                    }
                }
            }
        },
    )

    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
private fun KeyRow(
    key: KeyBinding,
    action: KeyAction,
    /** What the key does now, when that is something other than this row. */
    takenFrom: String?,
    onLongPressChange: (Boolean) -> Unit,
    onArgumentChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeyCap(key.trigger, longPress = false)
            Spacer(modifier = Modifier.weight(1f))
            // A combination fires as its second key goes down, so it has no long press to offer.
            if (key.chordKeyCode == null) {
                FilterChip(
                    selected = key.longPress,
                    onClick = { onLongPressChange(!key.longPress) },
                    label = { Text(text = stringResource(MR.strings.key_binding_long_press)) },
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(
                        MR.strings.key_slot_remove_key,
                        keysLabel(key.trigger, longPress = false),
                    ),
                )
            }
        }
        if (action.hasArgument) {
            val usable = action.accepts(key.argument.trim())
            OutlinedTextField(
                value = key.argument,
                onValueChange = onArgumentChange,
                label = { Text(text = stringResource(action.argumentLabelRes)) },
                supportingText = if (key.argument.isBlank()) {
                    { Text(text = stringResource(MR.strings.information_required_plain)) }
                } else {
                    null
                },
                isError = key.argument.isNotBlank() && !usable,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (takenFrom != null) {
            Text(
                text = stringResource(MR.strings.key_binding_key_taken, takenFrom),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun keysLabel(trigger: KeyTrigger, longPress: Boolean): String {
    val keys = triggerLabel(trigger) { keyLabel(KeyEvent.keyCodeToString(it)) }
    return if (longPress) stringResource(MR.strings.key_binding_hold, keys) else keys
}

@Composable
private fun slotLabel(slot: KeySlot): String = when {
    // Back closes whatever is open, which while looking a word up is the lookup.
    slot.action == KeyAction.Back && slot.context == KeyContext.PlayerLookup ->
        stringResource(MR.strings.key_action_close_lookup)
    else -> stringResource(slot.action.titleRes)
}

/** What [binding] does, for saying which row a key is taken from. */
@Composable
private fun bindingLabel(binding: KeyBinding, context: KeyContext): String {
    // An action from a newer version has no name here, so its raw one is shown.
    val action = KeyAction.fromName(binding.action) ?: return binding.action
    val label = slotLabel(KeySlot(context, action))
    val argument = argumentLabel(action, binding.argument)
    return if (argument.isNullOrEmpty()) label else "$label $argument"
}

private val KeyAction.argumentLabelRes: StringResource
    get() = when (this) {
        KeyAction.SeekBy -> MR.strings.key_binding_seconds
        KeyAction.VolumeBy -> MR.strings.key_binding_volume_steps
        KeyAction.BrightnessBy -> MR.strings.key_binding_brightness_steps
        KeyAction.MpvCommand -> MR.strings.key_binding_mpv_command
        else -> MR.strings.key_binding_step
    }

private val KeyAction.argumentSummaryRes: StringResource
    get() = when (this) {
        KeyAction.SeekBy -> MR.strings.key_binding_seconds_summary
        KeyAction.VolumeBy -> MR.strings.key_binding_volume_steps_summary
        KeyAction.BrightnessBy -> MR.strings.key_binding_brightness_steps_summary
        KeyAction.MpvCommand -> MR.strings.key_binding_mpv_command_summary
        else -> MR.strings.key_binding_step_summary
    }

private val KeyAction.titleRes: StringResource
    get() = when (this) {
        KeyAction.PlayPause -> MR.strings.key_action_play_pause
        KeyAction.SeekBy -> MR.strings.key_action_seek_by
        KeyAction.VolumeBy -> MR.strings.key_action_volume
        KeyAction.BrightnessBy -> MR.strings.key_action_brightness
        KeyAction.SubtitleLine -> MR.strings.key_action_subtitle_line
        KeyAction.ReplaySubtitle -> MR.strings.key_action_replay_subtitle
        KeyAction.ToggleSubtitles -> MR.strings.key_action_toggle_subtitles
        KeyAction.SubtitleTrack -> MR.strings.key_action_subtitle_track
        KeyAction.SecondarySubtitleTrack -> MR.strings.key_action_secondary_subtitle_track
        KeyAction.StartWordCursor -> MR.strings.key_action_start_word_cursor
        KeyAction.Word -> MR.strings.key_action_word
        KeyAction.OpenPopup -> MR.strings.key_action_open_popup
        KeyAction.Entry -> MR.strings.key_action_entry
        KeyAction.Scroll -> MR.strings.key_action_scroll
        KeyAction.PlayWordAudio -> MR.strings.key_action_play_word_audio
        KeyAction.MineEntry -> MR.strings.key_action_mine_entry
        KeyAction.Back -> MR.strings.key_action_back
        KeyAction.MpvCommand -> MR.strings.key_action_mpv_command
    }

private val KeyGroup.titleRes: StringResource
    get() = when (this) {
        KeyGroup.Playback -> MR.strings.key_group_playback
        KeyGroup.Subtitles -> MR.strings.key_group_subtitles
        KeyGroup.WordLookup -> MR.strings.key_group_word_lookup
        KeyGroup.Popup -> MR.strings.key_group_popup
        KeyGroup.Other -> MR.strings.key_group_other
    }

private val KeyContext.titleRes: StringResource
    get() = when (this) {
        KeyContext.Player -> MR.strings.key_context_player
        KeyContext.PlayerLookup -> MR.strings.key_context_player_lookup
    }
