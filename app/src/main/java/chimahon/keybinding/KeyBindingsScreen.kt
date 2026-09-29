package chimahon.keybinding

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.player.components.ExposedTextDropDownMenu
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

/** The player's key bindings: which key or gamepad button does what. */
object KeyBindingsScreen : Screen() {

    private sealed interface Dialog {
        /** [binding] is the one being edited, or null for a new one. */
        data class Edit(val context: KeyContext, val binding: KeyBinding?) : Dialog
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
                KeyContext.entries.forEach { context ->
                    val contextBindings = bindings.getValue(context)
                    item(key = context.prefKey) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.small),
                        ) {
                            Text(
                                text = stringResource(context.titleRes),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { dialog = Dialog.Edit(context, null) }) {
                                Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                                Text(text = stringResource(MR.strings.action_add))
                            }
                        }
                    }
                    if (contextBindings.isEmpty()) {
                        item(key = "${context.prefKey}-empty") {
                            Text(
                                text = stringResource(MR.strings.key_bindings_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MaterialTheme.padding.medium),
                            )
                        }
                    }
                    items(
                        items = contextBindings,
                        key = { "${context.prefKey}-${it.trigger}-${it.longPress}" },
                    ) { binding ->
                        val keys = bindingKeysLabel(binding.trigger, binding.longPress)
                        ListItem(
                            headlineContent = { Text(text = keys) },
                            supportingContent = { Text(text = actionLabel(binding)) },
                            trailingContent = {
                                IconButton(
                                    onClick = { preferences.getValue(context).set(contextBindings - binding) },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(MR.strings.key_binding_delete, keys),
                                    )
                                }
                            },
                            modifier = Modifier
                                .animateItem()
                                .clickable { dialog = Dialog.Edit(context, binding) },
                        )
                    }
                }
            }
        }

        when (val shown = dialog) {
            null -> {}
            is Dialog.Edit -> KeyBindingDialog(
                context = shown.context,
                initial = shown.binding,
                bindings = bindings.getValue(shown.context),
                onDismissRequest = { dialog = null },
                onSave = {
                    val edited = bindings.getValue(shown.context).withBinding(it, replaced = shown.binding)
                    preferences.getValue(shown.context).set(edited)
                },
            )
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
private fun KeyBindingDialog(
    context: KeyContext,
    initial: KeyBinding?,
    bindings: List<KeyBinding>,
    onDismissRequest: () -> Unit,
    onSave: (KeyBinding) -> Unit,
) {
    var trigger by remember { mutableStateOf(initial?.trigger) }
    var longPress by remember { mutableStateOf(initial?.longPress ?: false) }
    val actions = remember(context) { KeyAction.entries.filter { it.isFor(context) } }
    var action by remember { mutableStateOf(initial?.let { KeyAction.fromName(it.action) } ?: actions.first()) }
    var argument by remember { mutableStateOf(initial?.argument.orEmpty()) }

    // A new binding starts out waiting for its key.
    var listening by remember { mutableStateOf(initial == null) }
    var capture by remember { mutableStateOf(TriggerCapture()) }
    val focusRequester = remember { FocusRequester() }

    val isCombination = trigger?.chordKeyCode != null
    val candidate = trigger?.let {
        KeyBinding(
            keyCode = it.keyCode,
            modifiers = it.modifiers,
            chordKeyCode = it.chordKeyCode,
            longPress = longPress && !isCombination,
            action = action.name,
            argument = if (action.hasArgument) argument.trim() else "",
        )
    }
    val taken = candidate?.let { new -> bindings.firstOrNull { it != initial && it.sameTrigger(new) } }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = candidate != null && !listening && action.accepts(candidate.argument),
                onClick = {
                    onSave(candidate!!)
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
                Text(
                    text = stringResource(
                        if (initial == null) MR.strings.key_binding_add else MR.strings.key_binding_edit,
                    ),
                )
                Text(
                    text = stringResource(context.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(
                        width = if (listening) 2.dp else 1.dp,
                        color = if (listening) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            // Back has to keep closing the dialog.
                            if (!listening || key.keyCode == KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                            if (key.action == KeyEvent.ACTION_DOWN || key.action == KeyEvent.ACTION_UP) {
                                val done = capture.onKey(key.keyCode, key.metaState, key.action == KeyEvent.ACTION_DOWN)
                                capture.trigger?.let { trigger = it }
                                if (done) listening = false
                            }
                            true
                        }
                        .focusable()
                        .clickable {
                            capture = TriggerCapture()
                            listening = true
                            focusRequester.requestFocus()
                        },
                ) {
                    Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
                        Text(
                            text = stringResource(MR.strings.key_binding_key),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = trigger?.let { bindingKeysLabel(it, longPress = false) }
                                ?: stringResource(MR.strings.key_binding_key_listening),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = when {
                                listening && trigger != null -> stringResource(MR.strings.key_binding_key_listening)
                                listening -> stringResource(MR.strings.key_binding_key_listening_summary)
                                taken != null -> stringResource(MR.strings.key_binding_key_taken, actionLabel(taken))
                                else -> stringResource(MR.strings.key_binding_key_change)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!listening && taken != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = longPress && !isCombination,
                            enabled = !isCombination,
                            role = Role.Checkbox,
                            onValueChange = { longPress = it },
                        ),
                ) {
                    Checkbox(
                        checked = longPress && !isCombination,
                        onCheckedChange = null,
                        enabled = !isCombination,
                    )
                    Column {
                        Text(text = stringResource(MR.strings.key_binding_long_press))
                        if (isCombination) {
                            Text(
                                text = stringResource(MR.strings.key_binding_long_press_combination),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val actionNames = actions.map { stringResource(it.titleRes) }
                ExposedTextDropDownMenu(
                    selectedValue = stringResource(action.titleRes),
                    options = actionNames.toImmutableList(),
                    label = stringResource(MR.strings.key_binding_action),
                    onValueChangedEvent = { action = actions[actionNames.indexOf(it)] },
                )

                if (action.hasArgument) {
                    val isSeek = action == KeyAction.SeekBy
                    OutlinedTextField(
                        value = argument,
                        onValueChange = { argument = it },
                        label = {
                            Text(
                                text = stringResource(
                                    if (isSeek) MR.strings.key_binding_seconds else MR.strings.key_binding_mpv_command,
                                ),
                            )
                        },
                        supportingText = {
                            Text(
                                text = stringResource(
                                    if (isSeek) {
                                        MR.strings.key_binding_seconds_summary
                                    } else {
                                        MR.strings.key_binding_mpv_command_summary
                                    },
                                ),
                            )
                        },
                        isError = argument.isNotEmpty() && !action.accepts(argument.trim()),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )

    LaunchedEffect(focusRequester) {
        if (!listening) return@LaunchedEffect
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
private fun bindingKeysLabel(trigger: KeyTrigger, longPress: Boolean): String {
    val keys = triggerLabel(trigger) { keyLabel(KeyEvent.keyCodeToString(it)) }
    return if (longPress) stringResource(MR.strings.key_binding_hold, keys) else keys
}

@Composable
private fun actionLabel(binding: KeyBinding): String {
    // An action from a newer version has no name here, so its raw one is shown.
    val action = KeyAction.fromName(binding.action) ?: return binding.action
    val seconds = binding.argument.toIntOrNull()
    return when {
        action == KeyAction.SeekBy && seconds != null && seconds < 0 ->
            stringResource(MR.strings.key_action_seek_back, -seconds)
        action == KeyAction.SeekBy && seconds != null ->
            stringResource(MR.strings.key_action_seek_forward, seconds)
        action == KeyAction.MpvCommand ->
            stringResource(MR.strings.key_action_mpv_command_value, binding.argument)
        else -> stringResource(action.titleRes)
    }
}

private val KeyAction.titleRes: StringResource
    get() = when (this) {
        KeyAction.PlayPause -> MR.strings.key_action_play_pause
        KeyAction.SeekBy -> MR.strings.key_action_seek_by
        KeyAction.VolumeUp -> MR.strings.key_action_volume_up
        KeyAction.VolumeDown -> MR.strings.key_action_volume_down
        KeyAction.PreviousSubtitle -> MR.strings.key_action_previous_subtitle
        KeyAction.NextSubtitle -> MR.strings.key_action_next_subtitle
        KeyAction.ReplaySubtitle -> MR.strings.key_action_replay_subtitle
        KeyAction.ToggleSubtitles -> MR.strings.key_action_toggle_subtitles
        KeyAction.CycleSubtitle -> MR.strings.key_action_cycle_subtitle
        KeyAction.CycleSecondarySubtitle -> MR.strings.key_action_cycle_secondary_subtitle
        KeyAction.StartWordCursor -> MR.strings.key_action_start_word_cursor
        KeyAction.CursorPrevious -> MR.strings.key_action_cursor_previous
        KeyAction.CursorNext -> MR.strings.key_action_cursor_next
        KeyAction.OpenPopup -> MR.strings.key_action_open_popup
        KeyAction.PreviousEntry -> MR.strings.key_action_previous_entry
        KeyAction.NextEntry -> MR.strings.key_action_next_entry
        KeyAction.ScrollUp -> MR.strings.key_action_scroll_up
        KeyAction.ScrollDown -> MR.strings.key_action_scroll_down
        KeyAction.PlayWordAudio -> MR.strings.key_action_play_word_audio
        KeyAction.MineEntry -> MR.strings.key_action_mine_entry
        KeyAction.Back -> MR.strings.key_action_back
        KeyAction.MpvCommand -> MR.strings.key_action_mpv_command
    }

private val KeyContext.titleRes: StringResource
    get() = when (this) {
        KeyContext.Player -> MR.strings.key_context_player
        KeyContext.PlayerLookup -> MR.strings.key_context_player_lookup
    }
