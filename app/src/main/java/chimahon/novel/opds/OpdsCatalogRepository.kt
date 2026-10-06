package chimahon.novel.opds

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.io.File
import java.util.UUID

/**
 * Saved OPDS catalogs with their Basic-auth credentials, as one entry in the app's preference
 * store. The key is private, so a backup leaves the catalogs and their passwords out unless the
 * user includes private settings.
 */
class OpdsCatalogRepository(store: PreferenceStore) {

    private val preference = store.getObjectFromString(
        key = Preference.privateKey("opds_catalogs"),
        defaultValue = emptyList(),
        serializer = { json.encodeToString(serializer, it) },
        deserializer = { runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList()) },
    )

    val catalogs: Flow<List<OpdsCatalog>> get() = preference.changes()

    fun current(): List<OpdsCatalog> = preference.get()

    fun save(catalog: OpdsCatalog) {
        val id = catalog.id.ifBlank { UUID.randomUUID().toString() }
        val next = catalog.copy(
            id = id,
            name = catalog.name.trim(),
            url = catalog.url.trim(),
            username = catalog.username.trim(),
        )
        update { current -> current.filterNot { it.id == id } + next }
    }

    fun delete(id: String) {
        update { current -> current.filterNot { it.id == id } }
    }

    private fun update(transform: (List<OpdsCatalog>) -> List<OpdsCatalog>) {
        preference.set(transform(preference.get()).sortedBy { it.name.lowercase() })
    }

    /**
     * Takes over the catalogs of the JSON file they were kept in before and removes it. A file
     * that cannot be read is left alone.
     */
    fun migrateLegacy(file: File) {
        if (!file.isFile) return
        val legacy = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull() ?: return
        if (!preference.isSet()) preference.set(legacy)
        file.delete()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(OpdsCatalog.serializer())

        /** The catalogs, after moving anything still in the old file into the preference store. */
        fun create(store: PreferenceStore, context: Context): OpdsCatalogRepository =
            OpdsCatalogRepository(store).apply { migrateLegacy(File(context.filesDir, "opds_catalogs.json")) }
    }
}
