package chimahon.custom.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.NumberFormat

/**
 * The rows the fork adds to the player's settings, and what they change about upstream's rows.
 * Upstream's screens name one of these where a row goes, so a new option is added here.
 */
object CustomGestureSettings {
    // The seek sensitivity slider moves in steps of 10%, from 10% to 300%.
    private const val SEEK_SENSITIVITY_STEP = 10

    @Composable
    private fun preferences() = remember { Injekt.get<CustomGesturePreferences>() }

    @Composable
    fun longPressAction(): Preference.PreferenceItem.ListPreference<LongPressGesture> =
        Preference.PreferenceItem.ListPreference(
            preference = preferences().longPressGesture(),
            title = stringResource(MR.strings.pref_long_press_gesture),
            entries = LongPressGesture.entries.associateWith { stringResource(it.stringRes) }.toPersistentMap(),
        )

    /** Upstream's "Disable long-press screenshot" switch is only read while this is true. */
    @Composable
    fun longPressTakesScreenshots(): Boolean {
        val action by preferences().longPressGesture().collectAsState()
        return action == LongPressGesture.Screenshot
    }

    @Composable
    fun verticalSwipe(enabled: Boolean): Preference.PreferenceItem.ListPreference<VerticalSwipeGesture> =
        Preference.PreferenceItem.ListPreference(
            preference = preferences().subtitleSwipeVertical(),
            title = stringResource(MR.strings.pref_player_gesture_vertical_swipe),
            entries = VerticalSwipeGesture.entries.associateWith { stringResource(it.stringRes) }.toPersistentMap(),
            enabled = enabled,
        )

    /** Whether vertical swipes reach the volume and brightness sliders at all. */
    @Composable
    fun slidersAvailable(subtitleSwipeControls: Boolean): Boolean {
        val verticalSwipe by preferences().subtitleSwipeVertical().collectAsState()
        return !subtitleSwipeControls || verticalSwipe == VerticalSwipeGesture.VolumeBrightness
    }

    @Composable
    fun seekSensitivity(enabled: Boolean): Preference.PreferenceItem.SliderPreference {
        val preference = preferences().horizontalSeekSensitivity()
        val sensitivity by preference.collectAsState()
        val percentFormat = remember { NumberFormat.getPercentInstance() }
        return Preference.PreferenceItem.SliderPreference(
            value = sensitivity / SEEK_SENSITIVITY_STEP,
            title = stringResource(MR.strings.pref_player_gesture_seek_sensitivity),
            subtitle = stringResource(
                MR.strings.pref_player_gesture_seek_sensitivity_summary,
                percentFormat.format(sensitivity / 100f),
            ),
            valueRange = 1..30,
            enabled = enabled,
            onValueChanged = { preference.set(it * SEEK_SENSITIVITY_STEP) },
        )
    }

    @Composable
    fun allowGesturesWhenLocked(): Preference.PreferenceItem.SwitchPreference =
        Preference.PreferenceItem.SwitchPreference(
            preference = preferences().allowGesturesWhenLocked(),
            title = stringResource(MR.strings.pref_controls_allow_gestures_when_locked),
        )
}
