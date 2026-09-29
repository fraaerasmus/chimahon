package chimahon.keybinding

import eu.kanade.tachiyomi.ui.player.Dialogs
import eu.kanade.tachiyomi.ui.player.Panels
import eu.kanade.tachiyomi.ui.player.Sheets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerKeyGateTest {

    private fun gate(
        inPip: Boolean = false,
        ocrShown: Boolean = false,
        sheet: Sheets = Sheets.None,
        panel: Panels = Panels.None,
        dialog: Dialogs = Dialogs.None,
    ) = playerKeyGate(inPip, ocrShown, sheet, panel, dialog)

    @Test
    fun `a bare player takes every binding`() {
        assertEquals(PlayerKeyGate.All, gate())
    }

    @Test
    fun `the subtitle list beside the video leaves bindings on`() {
        assertEquals(PlayerKeyGate.All, gate(panel = Panels.SubtitleSideList))
    }

    @Test
    fun `anything opened over the video keeps only back`() {
        assertEquals(PlayerKeyGate.BackOnly, gate(sheet = Sheets.SubtitleTracks))
        assertEquals(PlayerKeyGate.BackOnly, gate(panel = Panels.SubtitleRegex))
        assertEquals(PlayerKeyGate.BackOnly, gate(dialog = Dialogs.EpisodeList))
    }

    @Test
    fun `picture in picture and video text recognition take no bindings`() {
        assertEquals(PlayerKeyGate.None, gate(inPip = true))
        assertEquals(PlayerKeyGate.None, gate(ocrShown = true))
        assertEquals(PlayerKeyGate.None, gate(inPip = true, sheet = Sheets.More))
    }
}
