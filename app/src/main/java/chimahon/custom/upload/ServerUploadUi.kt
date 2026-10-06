package chimahon.custom.upload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.upload.ServerUploadManager
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Overflow-menu entries the fork adds to the series screen. Upstream's `MangaToolbar` appends
 * whatever is here, so a new entry needs no parameter threaded through upstream's composables.
 */
val LocalCustomMangaActions = compositionLocalOf<List<AppBar.OverflowAction>> { emptyList() }

/**
 * Puts "Upload to server" into the overflow menu of the series screen shown by [content].
 * [available] is false where uploading makes no sense: stub sources and merged series.
 */
@Composable
fun ProvideServerUploadAction(
    screenModel: MangaScreenModel,
    manga: Manga,
    available: Boolean,
    content: @Composable () -> Unit,
) {
    val manager = remember { Injekt.get<ServerUploadManager>() }
    val enabled by remember(manga.id) { manager.enabledChanges(manga.id) }.collectAsState(initial = false)
    val actions = if (available) {
        listOf(
            AppBar.OverflowAction(
                title = if (enabled) "Stop uploading to server" else "Upload to server",
                onClick = { toggleServerUpload(screenModel, manager, manga) },
            ),
        )
    } else {
        emptyList()
    }
    CompositionLocalProvider(LocalCustomMangaActions provides actions, content = content)
}

/** Runs in the screen model's scope so that leaving the screen does not abandon the server check. */
private fun toggleServerUpload(screenModel: MangaScreenModel, manager: ServerUploadManager, manga: Manga) {
    val snackbarHostState = screenModel.snackbarHostState
    screenModel.screenModelScope.launch {
        val checking = if (!manager.isEnabled(manga.id)) {
            launch { snackbarHostState.showSnackbar(message = "Checking the server…") }
        } else {
            null
        }
        val message = withIOContext { manager.toggle(manga) }
        checking?.cancel()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message = message)
    }
}

/** The "Server upload" group on Settings > Data and storage. */
@Composable
fun serverUploadPreferences(syncPreferences: SyncPreferences): List<Preference> {
    val scope = rememberCoroutineScope()
    return listOf(
        Preference.PreferenceGroup(
            title = "Server upload",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.EditTextPreference(
                    preference = syncPreferences.webDavUploadFolder(),
                    title = "Upload folder",
                    subtitle = "Series marked \"Upload to server\" send their downloaded CBZ chapters to " +
                        "<WebDAV URL>/<this folder>/<series>/<chapter>.cbz and fetch the OCR sidecar the " +
                        "server writes back. Uses the WebDAV sync connection; select WebDAV as the sync " +
                        "service to edit its URL, username and password.",
                    onValueChanged = { newValue ->
                        scope.launch {
                            syncPreferences.webDavUploadFolder().set(newValue.trim().trim('/'))
                        }
                        true
                    },
                ),
            ),
        ),
    )
}
