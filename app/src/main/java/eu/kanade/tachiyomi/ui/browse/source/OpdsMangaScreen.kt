package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.canopus.chimareader.opds.OpdsBrowser
import com.canopus.chimareader.opds.OpdsCatalogRepository
import com.canopus.chimareader.opds.OpdsFormat
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** The OPDS browser in comic mode: downloaded CBZ/CBR files go to the local source. */
class OpdsMangaScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val repository = remember { Injekt.get<OpdsCatalogRepository>() }
        var importing by remember { mutableStateOf(false) }

        OpdsBrowser(
            repository = repository,
            onClose = navigator::pop,
            onImportFile = { file, displayName, entry ->
                importing = true
                scope.launch {
                    runCatching { ImportHandler.importComicFromOpds(context, file, displayName, entry) }
                        .onSuccess { context.toast("Added ${it.chapterName} to ${it.seriesFolder}") }
                        .onFailure { context.toast("Import failed: ${it.message ?: it::class.java.simpleName}") }
                    importing = false
                }
            },
            importBusy = importing,
            modifier = Modifier.fillMaxSize(),
            format = OpdsFormat.COMIC,
        )
    }
}
