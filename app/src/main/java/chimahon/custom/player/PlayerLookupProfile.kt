package chimahon.custom.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import chimahon.anki.AnkiProfile
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** The dictionary profile that lookups on the playing video use. */
@Composable
fun rememberPlayerLookupProfile(viewModel: PlayerViewModel): AnkiProfile {
    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val anime by viewModel.currentAnime.collectAsState()
    val source by viewModel.currentSource.collectAsState()
    return remember(anime?.id, source?.id, source?.lang) {
        dictionaryPreferences.profileResolver.resolve(
            animeId = anime?.id ?: 0L,
            sourceId = source?.id ?: 0L,
            sourceLang = source?.lang.orEmpty(),
        )
    }
}
