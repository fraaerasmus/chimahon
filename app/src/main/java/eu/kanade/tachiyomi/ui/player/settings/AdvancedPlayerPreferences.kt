package eu.kanade.tachiyomi.ui.player.settings

import tachiyomi.core.common.preference.PreferenceStore

class AdvancedPlayerPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun mpvScripts() = preferenceStore.getBoolean("mpv_scripts", false)
    fun mpvConf() = preferenceStore.getString("pref_mpv_conf", "")
    fun mpvInput() = preferenceStore.getString("pref_mpv_input", "")

    // Custom -->
    fun mpvLogFile() = preferenceStore.getBoolean("pref_mpv_log_file", false)
    // Custom <--

    // Non-preference

    fun playerStatisticsPage() = preferenceStore.getInt("pref_player_statistics_page", 0)
}
