package com.canopus.chimareader.opds

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Saved OPDS catalogs with their Basic-auth credentials, as a JSON file in app-private storage. */
class OpdsCatalogRepository(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, "opds_catalogs.json"))

    private val state = MutableStateFlow(load())
    val catalogs: StateFlow<List<OpdsCatalog>> = state.asStateFlow()

    suspend fun save(catalog: OpdsCatalog) {
        val id = catalog.id.ifBlank { UUID.randomUUID().toString() }
        val next = catalog.copy(
            id = id,
            name = catalog.name.trim(),
            url = catalog.url.trim(),
            username = catalog.username.trim(),
        )
        update { current -> current.filterNot { it.id == id } + next }
    }

    suspend fun delete(id: String) {
        update { current -> current.filterNot { it.id == id } }
    }

    private suspend fun update(transform: (List<OpdsCatalog>) -> List<OpdsCatalog>) = withContext(ioDispatcher) {
        val next = transform(state.value).sortedBy { it.name.lowercase() }
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(serializer, next))
        state.value = next
    }

    private fun load(): List<OpdsCatalog> =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(OpdsCatalog.serializer())
    }
}
