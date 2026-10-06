package chimahon.custom.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** One value kept as a JSON file: read leniently, replaced atomically. */
class JsonFileStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
) {
    /** The stored value, or the default when the file is missing or cannot be read. */
    fun load(): T =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrElse { default() }

    /** Throws when the file cannot be written; the previous content is then still in place. */
    fun save(value: T) = file.writeTextAtomic(json.encodeToString(serializer, value))

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
