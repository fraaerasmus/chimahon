package chimahon.custom.youtube

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The YouTube settings the fork adds. They live in the same preferences file as upstream's
 * `YouTubePreferences`, under their own keys, so that class stays identical to upstream.
 */
class YouTubeCustomPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("youtube_prefs", Context.MODE_PRIVATE)

    var preferredStartPage: String
        get() = prefs.getString(KEY_START_PAGE, DEFAULT_START_PAGE)
            ?.takeIf { it in START_PAGES }
            ?: DEFAULT_START_PAGE
        set(value) = prefs.edit().putString(KEY_START_PAGE, value).apply()

    var preferReliableAudio: Boolean
        get() = prefs.getBoolean(KEY_PREFER_RELIABLE_AUDIO, true)
        set(value) = prefs.edit().putBoolean(KEY_PREFER_RELIABLE_AUDIO, value).apply()

    var syncWatchHistory: Boolean
        get() = prefs.getBoolean(KEY_SYNC_WATCH_HISTORY, false)
        set(value) = prefs.edit().putBoolean(KEY_SYNC_WATCH_HISTORY, value).apply()

    companion object {
        const val KEY_START_PAGE = "preferred_start_page"
        const val START_PAGE_HOME = "home"
        const val START_PAGE_HISTORY = "history"
        const val DEFAULT_START_PAGE = START_PAGE_HISTORY
        const val KEY_PREFER_RELIABLE_AUDIO = "prefer_reliable_audio"
        const val KEY_SYNC_WATCH_HISTORY = "sync_watch_history"

        val START_PAGES = listOf(
            START_PAGE_HOME,
            START_PAGE_HISTORY,
        )

        /** For callers with no context at hand. */
        fun get(): YouTubeCustomPreferences = YouTubeCustomPreferences(Injekt.get<Application>())
    }
}
