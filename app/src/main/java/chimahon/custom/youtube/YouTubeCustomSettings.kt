package chimahon.custom.youtube

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import chimahon.dictionary.DictionaryProfileResolver
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.youtube.YouTubeSource
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** The start page choice, shown at the top of upstream's YouTube settings screen. */
@Composable
fun YouTubeStartPageSettings() {
    val context = LocalContext.current
    val preferences = remember { YouTubeCustomPreferences(context) }
    var selectedStartPage by remember { mutableStateOf(preferences.preferredStartPage) }

    Text(
        text = stringResource(AMR.strings.youtube_start_page),
        style = MaterialTheme.typography.titleMedium,
    )

    Spacer(modifier = Modifier.height(8.dp))

    YouTubeCustomPreferences.START_PAGES.forEach { startPage ->
        RadioRow(
            label = when (startPage) {
                YouTubeCustomPreferences.START_PAGE_HISTORY -> stringResource(AMR.strings.youtube_start_page_history)
                else -> stringResource(AMR.strings.youtube_start_page_home)
            },
            selected = selectedStartPage == startPage,
            onSelect = {
                selectedStartPage = startPage
                preferences.preferredStartPage = startPage
            },
        )
    }

    Summary(stringResource(AMR.strings.youtube_start_page_summary))

    Spacer(modifier = Modifier.height(24.dp))
}

/**
 * Reliable audio, watch history sync and the dictionary profile, shown after upstream's own
 * options on the YouTube settings screen.
 */
@Composable
fun YouTubePlaybackAndLookupSettings() {
    val context = LocalContext.current
    val preferences = remember { YouTubeCustomPreferences(context) }
    var preferReliableAudio by remember { mutableStateOf(preferences.preferReliableAudio) }
    var syncWatchHistory by remember { mutableStateOf(preferences.syncWatchHistory) }
    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val dictionaryProfiles = remember { dictionaryPreferences.profileStore.getProfiles() }
    val profileOverride = remember {
        dictionaryPreferences.rawProfileOverride(DictionaryProfileResolver.sourceOverrideKey(YouTubeSource.ID))
    }
    var selectedProfileId by remember { mutableStateOf(profileOverride.get()) }

    Spacer(modifier = Modifier.height(16.dp))

    CheckboxRow(
        title = stringResource(AMR.strings.youtube_prefer_reliable_audio),
        summary = stringResource(AMR.strings.youtube_prefer_reliable_audio_summary),
        checked = preferReliableAudio,
        onCheckedChange = {
            preferReliableAudio = it
            preferences.preferReliableAudio = it
        },
    )

    Spacer(modifier = Modifier.height(8.dp))

    CheckboxRow(
        title = stringResource(AMR.strings.youtube_sync_watch_history),
        summary = stringResource(AMR.strings.youtube_sync_watch_history_summary),
        checked = syncWatchHistory,
        onCheckedChange = {
            syncWatchHistory = it
            preferences.syncWatchHistory = it
        },
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(AMR.strings.youtube_dictionary_profile),
        style = MaterialTheme.typography.titleMedium,
    )

    Spacer(modifier = Modifier.height(8.dp))

    // "" clears the override: the resolver then falls back to the active profile.
    val profileOptions = listOf(
        "" to stringResource(
            AMR.strings.youtube_dictionary_profile_auto,
            dictionaryPreferences.profileStore.getActiveProfile().name,
        ),
    ) + dictionaryProfiles.map { it.id to it.name }
    profileOptions.forEach { (profileId, label) ->
        RadioRow(
            label = label,
            selected = selectedProfileId == profileId,
            onSelect = {
                selectedProfileId = profileId
                if (profileId.isEmpty()) profileOverride.delete() else profileOverride.set(profileId)
            },
        )
    }

    Summary(stringResource(AMR.strings.youtube_dictionary_profile_summary))
}

// The rows below match the hand-built ones on upstream's YouTube settings screen, which uses no
// preference widgets.

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CheckboxRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Summary(summary)
        }
    }
}

@Composable
private fun Summary(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}
