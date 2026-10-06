package chimahon.novel.kosync.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

class KosyncScreen : Screen {

    @Composable
    override fun Content() {
        KosyncSettingsScreen()
    }
}
