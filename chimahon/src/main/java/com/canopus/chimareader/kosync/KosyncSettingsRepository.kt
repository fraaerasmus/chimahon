package com.canopus.chimareader.kosync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

/**
 * kosync toggles, the server login and this install's device id, in their own SharedPreferences
 * file alongside the ッツ sync settings.
 *
 * The password is never stored. KOReader authenticates with `userkey`, the md5 of the password, and
 * that hash is what both the server and this repository keep.
 */
class KosyncSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadFromPrefs())

    val settings: Flow<KosyncSettings> = _settings.asStateFlow()

    fun currentSettings(): KosyncSettings = _settings.value

    fun update(transform: (KosyncSettings) -> KosyncSettings) {
        val updated = transform(_settings.value)
        saveToPrefs(updated)
        _settings.value = updated
    }

    /** Stores the login. [password] is hashed here and discarded. */
    fun saveLogin(serverUrl: String, username: String, password: String) {
        prefs.edit().putString(KEY_USER_KEY, md5(password)).apply()
        update { it.copy(serverUrl = serverUrl.trim(), username = username.trim()) }
    }

    fun clearLogin() {
        prefs.edit().remove(KEY_USER_KEY).apply()
        update { it.copy(serverUrl = "", username = "") }
    }

    fun hasUserKey(): Boolean = !prefs.getString(KEY_USER_KEY, null).isNullOrBlank()

    fun credentials(): KosyncCredentials? {
        val current = _settings.value
        if (!current.isConfigured) return null
        val userKey = prefs.getString(KEY_USER_KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        return KosyncCredentials(serverUrl = current.serverUrl, username = current.username, userKey = userKey)
    }

    /** Credentials for a login that has not been saved yet, so the UI can test before committing. */
    fun draftCredentials(serverUrl: String, username: String, password: String): KosyncCredentials =
        KosyncCredentials(serverUrl = serverUrl.trim(), username = username.trim(), userKey = md5(password))

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().replace("-", "").uppercase().also { generated ->
                prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            }

    private fun loadFromPrefs(): KosyncSettings = KosyncSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        serverUrl = prefs.getString(KEY_SERVER_URL, null).orEmpty(),
        username = prefs.getString(KEY_USERNAME, null).orEmpty(),
        autoSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC, true),
        pushEnabled = prefs.getBoolean(KEY_PUSH, true),
    )

    private fun saveToPrefs(settings: KosyncSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_SERVER_URL, settings.serverUrl)
            .putString(KEY_USERNAME, settings.username)
            .putBoolean(KEY_AUTO_SYNC, settings.autoSyncEnabled)
            .putBoolean(KEY_PUSH, settings.pushEnabled)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "kosync-settings"
        private const val KEY_ENABLED = "kosyncEnabled"
        private const val KEY_SERVER_URL = "kosyncServerUrl"
        private const val KEY_USERNAME = "kosyncUsername"
        private const val KEY_AUTO_SYNC = "kosyncAutoSyncEnabled"
        private const val KEY_PUSH = "kosyncPushEnabled"
        private const val KEY_USER_KEY = "kosyncUserKey"
        private const val KEY_DEVICE_ID = "kosyncDeviceId"

        fun md5(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
