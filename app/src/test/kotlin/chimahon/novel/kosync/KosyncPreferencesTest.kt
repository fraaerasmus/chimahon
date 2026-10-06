package chimahon.novel.kosync

import chimahon.custom.core.FakePreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

class KosyncPreferencesTest {
    private val store = FakePreferenceStore()
    private val preferences = KosyncPreferences(store)

    @Test
    fun `the settings file from before the move is taken over whole`() {
        val migrated = preferences.migrateLegacy(
            mapOf(
                "kosyncEnabled" to true,
                "kosyncServerUrl" to "http://server:7200",
                "kosyncUsername" to "reader",
                "kosyncAutoSyncEnabled" to false,
                "kosyncPushEnabled" to false,
                "kosyncMangaEnabled" to false,
                "kosyncUserKey" to "0123456789abcdef0123456789abcdef",
                "kosyncDeviceId" to "ABCDEF0123456789",
            ),
        )

        assertTrue(migrated)
        assertTrue(preferences.enabled().get())
        assertFalse(preferences.autoSync().get())
        assertFalse(preferences.push().get())
        assertFalse(preferences.mangaEnabled().get())
        assertEquals(
            KosyncCredentials("http://server:7200", "reader", "0123456789abcdef0123456789abcdef"),
            preferences.credentials(),
        )
        // The server tells devices apart by this id, so it has to survive the move.
        assertEquals("ABCDEF0123456789", preferences.deviceId)
    }

    @Test
    fun `a partial old file keeps the defaults for what it lacks, and an empty one changes nothing`() {
        assertFalse(preferences.migrateLegacy(emptyMap<String, Any>()))
        assertTrue(store.getAll().isEmpty())

        preferences.migrateLegacy(mapOf("kosyncEnabled" to true))
        assertTrue(preferences.enabled().get())
        assertTrue(preferences.autoSync().get())
        assertTrue(preferences.push().get())
        assertTrue(preferences.mangaEnabled().get())
        assertNull(preferences.credentials())
    }

    @Test
    fun `the login is the md5 of the password and stays out of backups`() {
        val credentials = KosyncPreferences.credentialsFor(" http://server:7200 ", " reader ", "hunter2")
        assertEquals(KosyncCredentials("http://server:7200", "reader", "2ab96390c7dbe3439de74d0c9b0b1767"), credentials)

        preferences.saveLogin(credentials)
        assertEquals(credentials, preferences.credentials())
        val userKeyEntry = store.getAll().entries.single { it.value == credentials.userKey }
        assertTrue(Preference.isPrivate(userKeyEntry.key))

        preferences.clearLogin()
        assertNull(preferences.credentials())
    }

    @Test
    fun `the device id is made once and is not a setting`() {
        val first = preferences.deviceId
        assertEquals(32, first.length)
        assertEquals(first, KosyncPreferences(store).deviceId)
        assertTrue(Preference.isAppState(store.getAll().keys.single()))
    }
}
