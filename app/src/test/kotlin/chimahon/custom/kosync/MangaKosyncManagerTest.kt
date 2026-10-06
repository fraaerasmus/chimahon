package chimahon.custom.kosync

import android.content.Context
import chimahon.custom.core.FakePreferenceStore
import chimahon.novel.kosync.FakeKosyncApi
import chimahon.novel.kosync.KosyncCredentials
import chimahon.novel.kosync.KosyncDocumentId
import chimahon.novel.kosync.KosyncPreferences
import chimahon.novel.kosync.KosyncSession
import chimahon.novel.kosync.remoteProgress
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.io.LocalSourceFileSystem
import java.io.File

/** The decisions the manga sync makes for a ten-page CBZ in the local source. */
class MangaKosyncManagerTest {
    @TempDir
    lateinit var filesDir: File

    private val api = FakeKosyncApi()
    private val preferences = KosyncPreferences(FakePreferenceStore()).apply {
        enabled().set(true)
        saveLogin(KosyncCredentials("http://server", "reader", "key"))
    }
    private lateinit var archive: File
    private lateinit var archiveFile: UniFile

    private val manga = Manga.create().copy(id = 1L, source = LocalSource.ID)
    private val chapter = Chapter.create().copy(
        id = 7L,
        mangaId = 1L,
        url = "Berserk/vol1.cbz",
        name = "Vol. 1",
        lastPageRead = 0L,
        lastModifiedAt = 1_000L,
    )

    @BeforeEach
    fun archive() {
        archive = File(filesDir, "vol1.cbz").apply { writeBytes(ByteArray(8192) { it.toByte() }) }
        archiveFile = mockk()
        every { archiveFile.name } returns "vol1.cbz"
        every { archiveFile.isFile } returns true
        every { archiveFile.length() } returns archive.length()
        every { archiveFile.lastModified() } returns 42L
        every { archiveFile.filePath } returns archive.absolutePath
    }

    private fun manager(): MangaKosyncManager {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
        val source = mockk<Source>()
        every { source.id } returns LocalSource.ID
        val sources = mockk<SourceManager>()
        every { sources.getOrStub(any()) } returns source
        val localFiles = mockk<LocalSourceFileSystem>()
        every { localFiles.getFilesInMangaDirectory("Berserk") } returns listOf(archiveFile)
        return MangaKosyncManager(context, KosyncSession(preferences, api), mockk(), sources, localFiles)
    }

    private fun MangaKosyncManager.pullPage(pageCount: Int = 10) = runBlocking { pull(manga, chapter, pageCount) }
    private fun MangaKosyncManager.pushPage(index: Int) = runBlocking { push(manga, chapter, index, 10) }

    @Test
    fun `a newer page from another device is returned as a page index`() {
        api.remote = remoteProgress(progress = "5", percentage = 0.5, timestamp = 2_000L)

        assertEquals(4, manager().pullPage())
        assertEquals(listOf(KosyncDocumentId.partialMd5(archive)), api.pulls)
    }

    @Test
    fun `the percentage places the page when the server sends no page number`() {
        api.remote = remoteProgress(progress = null, percentage = 0.8, timestamp = 2_000L)

        assertEquals(7, manager().pullPage())
    }

    @Test
    fun `the local page stands against an older, an undated or an own remote position`() {
        val manager = manager()

        api.remote = remoteProgress(progress = "5", percentage = 0.5, timestamp = 1_000L)
        assertNull(manager.pullPage())
        api.remote = remoteProgress(progress = "5", percentage = 0.5, timestamp = null)
        assertNull(manager.pullPage())
        api.remote = remoteProgress(progress = "5", percentage = 0.5, timestamp = 9_000L, deviceId = preferences.deviceId)
        assertNull(manager.pullPage())
        api.remote = remoteProgress(progress = "5", percentage = null, timestamp = 9_000L)
        assertNull(manager.pullPage())
    }

    @Test
    fun `a page turned here after the remote position keeps the local page`() {
        val manager = manager()
        manager.notePageTurn(chapter.id)
        api.remote = remoteProgress(progress = "5", percentage = 0.5, timestamp = 2_000L)

        assertNull(manager.pullPage())
    }

    @Test
    fun `a remote position is applied once`() {
        val manager = manager()
        api.remote = remoteProgress(progress = "5", percentage = 0.5, timestamp = 2_000L)

        assertEquals(4, manager.pullPage())
        assertNull(manager.pullPage())
    }

    @Test
    fun `a remote position on the page already shown moves nothing`() {
        api.remote = remoteProgress(progress = "1", percentage = 0.1, timestamp = 2_000L)

        assertNull(manager().pullPage())
    }

    @Test
    fun `nothing is asked of the server when manga sync or sync on open is off`() {
        api.remote = remoteProgress(progress = "5", percentage = 0.5, timestamp = 2_000L)

        preferences.mangaEnabled().set(false)
        assertNull(manager().pullPage())
        manager().pushPage(3)
        preferences.mangaEnabled().set(true)
        preferences.autoSync().set(false)
        assertNull(manager().pullPage())
        preferences.autoSync().set(true)
        assertNull(manager().pullPage(pageCount = 0))

        assertEquals(emptyList<String>(), api.pulls)
        assertEquals(emptyList<FakeKosyncApi.Put>(), api.puts)
    }

    @Test
    fun `a push sends the page as a number, once, and not at all when pushing is off`() {
        preferences.push().set(false)
        manager().pushPage(3)
        assertEquals(0, api.puts.size)

        preferences.push().set(true)
        manager().pushPage(3)
        // A new manager reads what the last one saved.
        manager().pushPage(3)

        val put = api.puts.single()
        assertEquals("4", put.progress)
        assertEquals(0.4, put.percentage)
        assertEquals(true, put.numericProgress)
        assertEquals(KosyncSession.DEVICE_NAME, put.device)
        assertEquals(preferences.deviceId, put.deviceId)
    }

    @Test
    fun `a pulled page is not pushed back`() {
        val manager = manager()
        api.remote = remoteProgress(progress = "5", percentage = 0.5, timestamp = 2_000L)

        manager.pullPage()
        manager.pushPage(4)

        assertEquals(0, api.puts.size)
    }

    @Test
    fun `the archive is hashed once while it does not change`() {
        val manager = manager()
        manager.pushPage(3)
        manager.pushPage(4)
        manager().pushPage(5)

        verify(exactly = 1) { archiveFile.filePath }
        assertEquals(3, api.puts.size)
    }
}
