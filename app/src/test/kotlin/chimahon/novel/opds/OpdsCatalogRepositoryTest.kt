package chimahon.novel.opds

import chimahon.custom.core.FakePreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.Preference
import java.io.File

class OpdsCatalogRepositoryTest {
    @TempDir
    lateinit var filesDir: File

    private val store = FakePreferenceStore()
    private val repository = OpdsCatalogRepository(store)

    @Test
    fun `catalogs are saved trimmed, sorted by name and replaced by id`() {
        repository.save(OpdsCatalog(id = "", name = " calibre ", url = " http://server:8083/opds ", username = " reader ", password = " pw "))
        repository.save(OpdsCatalog(id = "", name = "Archive", url = "http://other/opds"))

        val (archive, calibre) = repository.current()
        assertEquals("Archive", archive.name)
        assertEquals(OpdsCatalog(calibre.id, "calibre", "http://server:8083/opds", "reader", " pw "), calibre)
        assertTrue(calibre.id.isNotBlank())

        repository.save(calibre.copy(name = "A first"))
        assertEquals(listOf("A first", "Archive"), repository.current().map { it.name })

        repository.delete(archive.id)
        assertEquals(listOf("A first"), OpdsCatalogRepository(store).current().map { it.name })
        assertTrue(Preference.isPrivate(store.getAll().keys.single()))
    }

    @Test
    fun `the catalog file from before the move is taken over and removed`() {
        val legacy = File(filesDir, "opds_catalogs.json")
        legacy.writeText("""[{"id":"c1","name":"calibre","url":"http://server:8083/opds","username":"reader","password":"pw"}]""")

        repository.migrateLegacy(legacy)

        assertEquals(listOf(OpdsCatalog("c1", "calibre", "http://server:8083/opds", "reader", "pw")), repository.current())
        assertFalse(legacy.exists())
    }

    @Test
    fun `an unreadable old file is left alone and a missing one is fine`() {
        repository.migrateLegacy(File(filesDir, "missing.json"))
        val broken = File(filesDir, "opds_catalogs.json").apply { writeText("{not json") }

        repository.migrateLegacy(broken)

        assertEquals(emptyList<OpdsCatalog>(), repository.current())
        assertTrue(broken.exists())
    }
}
