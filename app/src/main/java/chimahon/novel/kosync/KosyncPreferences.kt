package chimahon.novel.kosync

import android.content.Context
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.security.MessageDigest
import java.util.UUID

/**
 * The kosync switches, the server login and this install's device id, in the app's preference
 * store.
 *
 * The password is never stored. KOReader authenticates with the md5 of the password, and that
 * hash is what both the server and [userKey] hold. It sits under a private key, so a backup leaves
 * it out unless the user includes private settings. The device id is app state and never goes
 * into a backup, so restoring one on a second device does not make two devices look like one.
 */
class KosyncPreferences(private val store: PreferenceStore) {

    fun enabled() = store.getBoolean("kosync_enabled", false)

    /** Also sync manga chapters that are single archive files (local source and downloads). */
    fun mangaEnabled() = store.getBoolean("kosync_manga_enabled", true)

    fun autoSync() = store.getBoolean("kosync_auto_sync", true)

    fun push() = store.getBoolean("kosync_push", true)

    fun serverUrl() = store.getString("kosync_server_url", "")

    fun username() = store.getString("kosync_username", "")

    fun userKey() = store.getString(Preference.privateKey("kosync_user_key"), "")

    private fun storedDeviceId() = store.getString(Preference.appStateKey("kosync_device_id"), "")

    /** The saved login, or null until server, username and password have all been given. */
    fun credentials(): KosyncCredentials? =
        KosyncCredentials(serverUrl().get(), username().get(), userKey().get())
            .takeIf { it.serverUrl.isNotBlank() && it.username.isNotBlank() && it.userKey.isNotBlank() }

    fun saveLogin(credentials: KosyncCredentials) {
        serverUrl().set(credentials.serverUrl)
        username().set(credentials.username)
        userKey().set(credentials.userKey)
    }

    fun clearLogin() {
        serverUrl().delete()
        username().delete()
        userKey().delete()
    }

    val deviceId: String
        get() = storedDeviceId().get().ifBlank {
            UUID.randomUUID().toString().replace("-", "").uppercase().also(storedDeviceId()::set)
        }

    /**
     * Takes over the values of the SharedPreferences file these settings lived in before, given
     * as its key-value map. Returns false when there was nothing to take over.
     */
    fun migrateLegacy(legacy: Map<String, *>): Boolean {
        if (legacy.isEmpty()) return false
        (legacy["kosyncEnabled"] as? Boolean)?.let(enabled()::set)
        (legacy["kosyncMangaEnabled"] as? Boolean)?.let(mangaEnabled()::set)
        (legacy["kosyncAutoSyncEnabled"] as? Boolean)?.let(autoSync()::set)
        (legacy["kosyncPushEnabled"] as? Boolean)?.let(push()::set)
        (legacy["kosyncServerUrl"] as? String)?.let(serverUrl()::set)
        (legacy["kosyncUsername"] as? String)?.let(username()::set)
        (legacy["kosyncUserKey"] as? String)?.let(userKey()::set)
        (legacy["kosyncDeviceId"] as? String)?.let(storedDeviceId()::set)
        return true
    }

    companion object {
        /** The preferences, after moving anything still in the old settings file into them. */
        fun create(store: PreferenceStore, context: Context): KosyncPreferences {
            val preferences = KosyncPreferences(store)
            val legacy = context.getSharedPreferences("kosync-settings", Context.MODE_PRIVATE)
            if (preferences.migrateLegacy(legacy.all)) legacy.edit().clear().apply()
            return preferences
        }

        /** A login as typed. [password] is hashed here and goes no further. */
        fun credentialsFor(serverUrl: String, username: String, password: String) = KosyncCredentials(
            serverUrl = serverUrl.trim(),
            username = username.trim(),
            userKey = MessageDigest.getInstance("MD5").digest(password.toByteArray(Charsets.UTF_8)).toHexString(),
        )
    }
}
