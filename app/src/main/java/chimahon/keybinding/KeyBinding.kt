package chimahon.keybinding

import android.view.KeyEvent
import dev.icerock.moko.resources.StringResource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR

/** A screen with its own set of bindings. Each set is stored under its own preference. */
enum class KeyContext(val prefKey: String, val titleRes: StringResource) {
    Player("player", MR.strings.key_context_player),

    /** The player while a word of the subtitle is picked out or its dictionary popup is open. */
    PlayerLookup("player_lookup", MR.strings.key_context_player_lookup),
}

/** The headings the settings screen lists its rows under, in this order. */
enum class KeyGroup(val titleRes: StringResource) {
    Playback(MR.strings.key_group_playback),
    Subtitles(MR.strings.key_group_subtitles),
    WordLookup(MR.strings.key_group_word_lookup),
    Popup(MR.strings.key_group_popup),
    Other(MR.strings.key_group_other),
}

/** What a key has to say besides which action it runs: how that is asked for, checked and shown. */
enum class KeyArgument(val labelRes: StringResource, val summaryRes: StringResource) {
    Step(MR.strings.key_binding_step, MR.strings.key_binding_step_summary),
    Seconds(MR.strings.key_binding_seconds, MR.strings.key_binding_seconds_summary),
    VolumeSteps(MR.strings.key_binding_volume_steps, MR.strings.key_binding_volume_steps_summary),
    BrightnessSteps(MR.strings.key_binding_brightness_steps, MR.strings.key_binding_brightness_steps_summary),
    Command(MR.strings.key_binding_mpv_command, MR.strings.key_binding_mpv_command_summary),
    ;

    /** A command is any text. Everything else is a signed whole number other than zero. */
    fun accepts(argument: String): Boolean =
        if (this == Command) argument.isNotBlank() else argument.toIntOrNull().let { it != null && it != 0 }

    /** How [argument] reads on a key cap. */
    fun capLabel(argument: String): String {
        if (this == Command) {
            return if (argument.length > COMMAND_LABEL_LENGTH) argument.take(COMMAND_LABEL_LENGTH) + "…" else argument
        }
        val amount = argument.toIntOrNull() ?: return ""
        val signed = if (amount > 0) "+$amount" else "$amount"
        return if (this == Seconds) "$signed s" else signed
    }

    private companion object {
        const val COMMAND_LABEL_LENGTH = 16
    }
}

/**
 * Everything a key can do, and all there is to say about each action apart from running it, which
 * is `PlayerKeyController.run`. A new action is an entry here, a branch there and its strings.
 *
 * [context] is the one screen the action works on and [group] the heading it is listed under.
 * Both are null for an action every screen has; it is listed last on each of them.
 */
enum class KeyAction(
    val context: KeyContext?,
    val group: KeyGroup?,
    val titleRes: StringResource,
    val argument: KeyArgument? = null,
    val repeatable: Boolean = false,
) {
    PlayPause(KeyContext.Player, KeyGroup.Playback, MR.strings.key_action_play_pause),
    SeekBy(KeyContext.Player, KeyGroup.Playback, MR.strings.key_action_seek_by, KeyArgument.Seconds, repeatable = true),
    VolumeBy(KeyContext.Player, KeyGroup.Playback, MR.strings.key_action_volume, KeyArgument.VolumeSteps, repeatable = true),
    BrightnessBy(
        KeyContext.Player,
        KeyGroup.Playback,
        MR.strings.key_action_brightness,
        KeyArgument.BrightnessSteps,
        repeatable = true,
    ),
    SubtitleLine(KeyContext.Player, KeyGroup.Subtitles, MR.strings.key_action_subtitle_line, KeyArgument.Step),
    ReplaySubtitle(KeyContext.Player, KeyGroup.Subtitles, MR.strings.key_action_replay_subtitle),
    ToggleSubtitles(KeyContext.Player, KeyGroup.Subtitles, MR.strings.key_action_toggle_subtitles),
    SubtitleTrack(KeyContext.Player, KeyGroup.Subtitles, MR.strings.key_action_subtitle_track, KeyArgument.Step),
    SecondarySubtitleTrack(
        KeyContext.Player,
        KeyGroup.Subtitles,
        MR.strings.key_action_secondary_subtitle_track,
        KeyArgument.Step,
    ),
    StartWordCursor(KeyContext.Player, KeyGroup.WordLookup, MR.strings.key_action_start_word_cursor),
    Word(KeyContext.PlayerLookup, KeyGroup.WordLookup, MR.strings.key_action_word, KeyArgument.Step, repeatable = true),
    OpenPopup(KeyContext.PlayerLookup, KeyGroup.WordLookup, MR.strings.key_action_open_popup),
    Entry(KeyContext.PlayerLookup, KeyGroup.Popup, MR.strings.key_action_entry, KeyArgument.Step, repeatable = true),
    Scroll(KeyContext.PlayerLookup, KeyGroup.Popup, MR.strings.key_action_scroll, KeyArgument.Step, repeatable = true),
    PlayWordAudio(KeyContext.PlayerLookup, KeyGroup.Popup, MR.strings.key_action_play_word_audio),
    MineEntry(KeyContext.PlayerLookup, KeyGroup.Popup, MR.strings.key_action_mine_entry),
    Back(null, null, MR.strings.key_action_back),
    MpvCommand(null, null, MR.strings.key_action_mpv_command, KeyArgument.Command),
    ;

    val hasArgument: Boolean get() = argument != null

    fun isFor(context: KeyContext) = this.context == null || this.context == context

    /** Whether [argument] is one this action can run with. */
    fun accepts(argument: String): Boolean = this.argument?.accepts(argument) ?: true

    companion object {
        fun fromName(name: String): KeyAction? = entries.firstOrNull { it.name == name }
    }
}

/**
 * One hardware key, or combination, and what it does.
 *
 * [action] is the name of a [KeyAction]. It is stored as text so bindings restored from a newer
 * version, naming an action this version lacks, still decode. Those are kept and never fire.
 */
@Serializable
data class KeyBinding(
    val keyCode: Int,
    /** Any of [KeyEvent.META_SHIFT_ON], [KeyEvent.META_ALT_ON], [KeyEvent.META_CTRL_ON]. */
    val modifiers: Int = 0,
    /** A key that has to be held while [keyCode] is pressed. */
    val chordKeyCode: Int? = null,
    val longPress: Boolean = false,
    val action: String,
    val argument: String = "",
) {
    fun sameTrigger(other: KeyBinding): Boolean = trigger == other.trigger && longPress == other.longPress
}

/**
 * This list with [binding] in the place of [replaced], or on the end when there is none. Another
 * binding on the same trigger gives way, as a trigger can only do one thing.
 */
fun List<KeyBinding>.withBinding(binding: KeyBinding, replaced: KeyBinding?): List<KeyBinding> {
    val index = if (replaced == null) -1 else indexOf(replaced)
    val kept = filterIndexed { i, other -> i != index && !other.sameTrigger(binding) }
    if (index == -1) return kept + binding
    val place = take(index).count { !it.sameTrigger(binding) }
    return kept.toMutableList().apply { add(place, binding) }
}

const val KEY_MODIFIER_MASK = KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON or KeyEvent.META_CTRL_ON

private val json = Json { ignoreUnknownKeys = true }

fun encodeKeyBindings(bindings: List<KeyBinding>): String = json.encodeToString(bindings)

fun decodeKeyBindings(text: String): List<KeyBinding> = json.decodeFromString<List<KeyBinding>>(text).map {
    // Each way of a pair was an action of its own before a press could take a signed step.
    val (action, step) = STEP_ACTIONS_BEFORE[it.action] ?: return@map it
    it.copy(action = action.name, argument = step)
}

private val STEP_ACTIONS_BEFORE = mapOf(
    "VolumeUp" to (KeyAction.VolumeBy to "1"),
    "VolumeDown" to (KeyAction.VolumeBy to "-1"),
    "PreviousSubtitle" to (KeyAction.SubtitleLine to "-1"),
    "NextSubtitle" to (KeyAction.SubtitleLine to "1"),
    "CycleSubtitle" to (KeyAction.SubtitleTrack to "1"),
    "CycleSecondarySubtitle" to (KeyAction.SecondarySubtitleTrack to "1"),
    "CursorPrevious" to (KeyAction.Word to "-1"),
    "CursorNext" to (KeyAction.Word to "1"),
    "PreviousEntry" to (KeyAction.Entry to "-1"),
    "NextEntry" to (KeyAction.Entry to "1"),
    "ScrollUp" to (KeyAction.Scroll to "-1"),
    "ScrollDown" to (KeyAction.Scroll to "1"),
)

// KeyEvent.isModifierKey() is a method on the Android stub, which unit tests cannot call.
internal val MODIFIER_KEYS = setOf(
    KeyEvent.KEYCODE_SHIFT_LEFT,
    KeyEvent.KEYCODE_SHIFT_RIGHT,
    KeyEvent.KEYCODE_ALT_LEFT,
    KeyEvent.KEYCODE_ALT_RIGHT,
    KeyEvent.KEYCODE_CTRL_LEFT,
    KeyEvent.KEYCODE_CTRL_RIGHT,
    KeyEvent.KEYCODE_META_LEFT,
    KeyEvent.KEYCODE_META_RIGHT,
    KeyEvent.KEYCODE_SYM,
    KeyEvent.KEYCODE_NUM,
    KeyEvent.KEYCODE_FUNCTION,
)
