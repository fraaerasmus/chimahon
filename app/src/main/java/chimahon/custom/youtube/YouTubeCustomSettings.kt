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
        val label = when (startPage) {
            YouTubeCustomPreferences.START_PAGE_HISTORY -> {
                stringResource(AMR.strings.youtube_start_page_history)
            }
            else -> stringResource(AMR.strings.youtube_start_page_home)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    selectedStartPage = startPage
                    preferences.preferredStartPage = startPage
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selectedStartPage == startPage,
                onClick = {
                    selectedStartPage = startPage
                    preferences.preferredStartPage = startPage
                },
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    Text(
        text = stringResource(AMR.strings.youtube_start_page_summary),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )

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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = preferReliableAudio,
                onValueChange = {
                    preferReliableAudio = it
                    preferences.preferReliableAudio = it
                },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = preferReliableAudio,
            onCheckedChange = null,
        )

        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = stringResource(AMR.strings.youtube_prefer_reliable_audio),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(AMR.strings.youtube_prefer_reliable_audio_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = syncWatchHistory,
                onValueChange = {
                    syncWatchHistory = it
                    preferences.syncWatchHistory = it
                },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = syncWatchHistory,
            onCheckedChange = null,
        )

        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = stringResource(AMR.strings.youtube_sync_watch_history),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(AMR.strings.youtube_sync_watch_history_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

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
        val select = {
            selectedProfileId = profileId
            if (profileId.isEmpty()) profileOverride.delete() else profileOverride.set(profileId)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = select)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selectedProfileId == profileId,
                onClick = select,
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    Text(
        text = stringResource(AMR.strings.youtube_dictionary_profile_summary),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}
