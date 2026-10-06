package chimahon.novel.kosync

import chimahon.custom.core.FakePreferenceStore
import chimahon.custom.core.ServerException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KosyncSessionTest {
    private val preferences = KosyncPreferences(FakePreferenceStore())
    private val credentials = KosyncCredentials("http://server", "reader", "key")

    @Test
    fun `a login is saved only once the server has accepted it`() {
        val refusing = object : KosyncApi by FakeKosyncApi() {
            override suspend fun authorize(credentials: KosyncCredentials) =
                throw ServerException("Incorrect username or password.", 401)
        }
        assertThrows(ServerException::class.java) {
            runBlocking { KosyncSession(preferences, refusing).signIn(credentials, register = false) }
        }
        assertNull(preferences.credentials())

        val session = KosyncSession(preferences, FakeKosyncApi())
        runBlocking { session.signIn(credentials, register = true) }
        assertEquals(credentials, preferences.credentials())

        session.signOut()
        assertNull(preferences.credentials())
    }

    @Test
    fun `the switches gate pulling and pushing, and neither works without a login`() {
        val session = KosyncSession(preferences, FakeKosyncApi())
        preferences.enabled().set(true)
        assertFalse(session.canPull || session.canPush)

        preferences.saveLogin(credentials)
        assertTrue(session.canPull && session.canPush)

        preferences.autoSync().set(false)
        preferences.push().set(false)
        assertFalse(session.canPull || session.canPush)
        assertTrue(session.isActive)

        preferences.enabled().set(false)
        assertFalse(session.isActive)
    }

    @Test
    fun `a remote position counts as newer only from another device and after the local change`() {
        val remote = remoteProgress(percentage = 0.5, deviceId = "KOBO", timestamp = 2_000L)
        assertTrue(KosyncSession.isNewer(remote, "PHONE", localSeconds = 1_999L))
        assertFalse(KosyncSession.isNewer(remote, "PHONE", localSeconds = 2_000L))
        assertFalse(KosyncSession.isNewer(remote, "KOBO", localSeconds = 1_999L))
        assertFalse(KosyncSession.isNewer(remote.copy(percentage = null), "PHONE", localSeconds = 1_999L))
        assertFalse(KosyncSession.isNewer(remote.copy(timestamp = null), "PHONE", localSeconds = 1_999L))
        // With no local position at all there is nothing to lose.
        assertTrue(KosyncSession.isNewer(remote.copy(timestamp = null), "PHONE", localSeconds = null))
    }

    @Test
    fun `a server that takes too long counts as having nothing newer`() = runTest {
        preferences.saveLogin(credentials)
        val slow = object : KosyncApi by FakeKosyncApi() {
            override suspend fun getProgress(credentials: KosyncCredentials, document: String): KosyncRemoteProgress? {
                delay(60_000)
                return remoteProgress(percentage = 0.5, timestamp = 2_000L)
            }
        }
        assertNull(KosyncSession(preferences, slow).fetchNewer("document", localSeconds = 0L))
    }
}
