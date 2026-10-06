package chimahon.novel.kosync

import chimahon.novel.data.Bookmark
import chimahon.novel.data.FileNames
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The decisions the novel sync makes: when a remote position replaces the local one, and when the
 * local one is sent. The book here has two chapters of 1000 characters and no readable EPUB, so
 * positions convert through the character index alone.
 */
class KosyncManagerTest {
    @TempDir
    lateinit var bookDir: File

    private val api = FakeKosyncApi()
    private val positions = object : KosyncPositionStore {
        var bookmark: Bookmark? = null
        override suspend fun load(bookDir: File): Bookmark? = bookmark
        override suspend fun save(bookDir: File, bookmark: Bookmark) {
            this.bookmark = bookmark
        }
    }

    private var settings = KosyncSettings(enabled = true, serverUrl = "http://server", username = "reader")
    private var signedIn = true

    private fun manager(): KosyncManager {
        val repository = mockk<KosyncSettingsRepository>()
        every { repository.currentSettings() } answers { settings }
        every { repository.hasUserKey() } answers { signedIn }
        every { repository.credentials() } answers {
            KosyncCredentials(settings.serverUrl, settings.username, "key").takeIf { signedIn }
        }
        every { repository.deviceId } returns THIS_DEVICE
        return KosyncManager(mockk(), repository, api, positionStore = positions)
    }

    @BeforeEach
    fun book() {
        File(bookDir, "book.epub").writeBytes(ByteArray(4096) { it.toByte() })
        val index = BookInfo(
            characterCount = 2000,
            chapterInfo = mapOf(
                "0" to ChapterInfo(spineIndex = 0, currentTotal = 0, chapterCount = 1000),
                "1" to ChapterInfo(spineIndex = 1, currentTotal = 1000, chapterCount = 1000),
            ),
        )
        File(bookDir, FileNames.bookinfo).writeText(Json.encodeToString(BookInfo.serializer(), index))
    }

    private fun pull() = runBlocking { manager().pull(bookDir, "Book") }
    private fun push() = runBlocking { manager().push(bookDir, "Book") }

    @Test
    fun `a newer position from another device replaces the local one`() {
        positions.bookmark = Bookmark(chapterIndex = 0, progress = 0.1, characterCount = 100, lastModified = 1_000_000L)
        api.remote = remoteProgress(percentage = 0.75, timestamp = 2_000L)

        pull()

        assertEquals(
            Bookmark(chapterIndex = 1, progress = 0.5, characterCount = 1500, lastModified = 2_000_000L),
            positions.bookmark,
        )
        assertEquals(listOf(KosyncDocumentId.partialMd5(File(bookDir, "book.epub"))), api.pulls)
    }

    @Test
    fun `a pointer that cannot be resolved falls back to the percentage`() {
        api.remote = remoteProgress(progress = "/body/DocFragment[2]/body/p[40]/text().0", percentage = 0.25, timestamp = 2_000L)

        pull()

        assertEquals(Bookmark(chapterIndex = 0, progress = 0.5, characterCount = 500, lastModified = 2_000_000L), positions.bookmark)
    }

    @Test
    fun `the local position stands against an older, an undated or an own remote position`() {
        val local = Bookmark(chapterIndex = 0, progress = 0.1, characterCount = 100, lastModified = 3_000_000L)
        positions.bookmark = local

        api.remote = remoteProgress(percentage = 0.75, timestamp = 2_000L)
        pull()
        api.remote = remoteProgress(percentage = 0.75, timestamp = 3_000L)
        pull()
        api.remote = remoteProgress(percentage = 0.75, timestamp = null)
        pull()
        api.remote = remoteProgress(percentage = 0.75, timestamp = 9_000L, deviceId = THIS_DEVICE)
        pull()
        api.remote = remoteProgress(percentage = null, timestamp = 9_000L)
        pull()

        assertEquals(local, positions.bookmark)
    }

    @Test
    fun `a local position with no date is never replaced`() {
        val local = Bookmark(chapterIndex = 0, progress = 0.1, characterCount = 100, lastModified = null)
        positions.bookmark = local
        api.remote = remoteProgress(percentage = 0.75, timestamp = 9_000L)

        pull()

        assertEquals(local, positions.bookmark)
    }

    @Test
    fun `a book never opened here takes the remote position, dated or not`() {
        api.remote = remoteProgress(percentage = 0.75, timestamp = null)

        pull()

        assertEquals(1500, positions.bookmark?.characterCount)
    }

    @Test
    fun `nothing is asked of the server when sync is off, signed out or the book has no source file`() {
        api.remote = remoteProgress(percentage = 0.75, timestamp = 2_000L)
        positions.bookmark = Bookmark(chapterIndex = 1, progress = 0.5, characterCount = 1500, lastModified = 1L)

        settings = settings.copy(enabled = false)
        pull()
        push()
        settings = settings.copy(enabled = true)
        signedIn = false
        pull()
        push()
        signedIn = true
        File(bookDir, "book.epub").delete()
        pull()
        push()

        assertEquals(emptyList<String>(), api.pulls)
        assertEquals(emptyList<FakeKosyncApi.Put>(), api.puts)
    }

    @Test
    fun `a push sends the character percentage and a pointer KOReader can resolve`() {
        positions.bookmark = Bookmark(chapterIndex = 1, progress = 0.5, characterCount = 1500, lastModified = 1L)

        push()

        val put = api.puts.single()
        assertEquals(0.75, put.percentage)
        // The chapter cannot be read here, so the pointer is the start of its DocFragment.
        assertEquals("/body/DocFragment[2]/body", put.progress)
        assertEquals(KosyncManager.DEVICE_NAME, put.device)
        assertEquals(THIS_DEVICE, put.deviceId)
        assertEquals(false, put.numericProgress)
    }

    @Test
    fun `a position is pushed once, and not at all when pushing is off`() {
        positions.bookmark = Bookmark(chapterIndex = 1, progress = 0.5, characterCount = 1500, lastModified = 1L)
        settings = settings.copy(pushEnabled = false)
        push()
        assertEquals(0, api.puts.size)

        settings = settings.copy(pushEnabled = true)
        push()
        push()
        assertEquals(1, api.puts.size)

        positions.bookmark = Bookmark(chapterIndex = 1, progress = 0.6, characterCount = 1600, lastModified = 2L)
        push()
        assertEquals(2, api.puts.size)
    }

    @Test
    fun `a pulled position is not pushed back`() {
        api.remote = remoteProgress(percentage = 0.75, timestamp = 2_000L)

        pull()
        push()

        assertEquals(0, api.puts.size)
    }

    @Test
    fun `nothing is pushed for a book with no position`() {
        push()
        assertNull(positions.bookmark)
        assertEquals(0, api.puts.size)
    }

    private companion object {
        const val THIS_DEVICE = "THISDEVICE"
    }
}
