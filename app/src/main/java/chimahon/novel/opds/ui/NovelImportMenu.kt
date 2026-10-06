package chimahon.novel.opds.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import chimahon.novel.opds.OpdsBrowser
import chimahon.novel.opds.OpdsCatalogRepository
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * The novel library's add button. Upstream's button opens the file picker directly; this one
 * offers the file picker and the OPDS catalogs from a menu.
 */
@Composable
fun NovelImportFab(
    onImportFiles: () -> Unit,
    onOpds: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(
            onClick = { showMenu = true },
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(MR.strings.action_add),
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Import files") },
                onClick = {
                    showMenu = false
                    onImportFiles()
                },
            )
            DropdownMenuItem(
                text = { Text("OPDS catalog") },
                onClick = {
                    showMenu = false
                    onOpds()
                },
            )
        }
    }
}

/** The OPDS browser over the novel library. [onImport] owns the downloaded file, including deleting it. */
@Composable
fun NovelOpdsBrowser(
    importBusy: Boolean,
    onClose: () -> Unit,
    onImport: (File) -> Unit,
) {
    val repository = remember { Injekt.get<OpdsCatalogRepository>() }
    OpdsBrowser(
        repository = repository,
        onClose = onClose,
        onImportFile = { file, _, _ -> onImport(file) },
        importBusy = importBusy,
        modifier = Modifier.fillMaxSize(),
    )
}
