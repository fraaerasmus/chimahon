package chimahon.custom.core

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JsonFileStoreTest {
    @TempDir
    lateinit var dir: File

    private fun store(name: String) =
        JsonFileStore(File(dir, name), MapSerializer(String.serializer(), Int.serializer())) { emptyMap() }

    @Test
    fun `a saved value is read back, and a missing or broken file reads as the default`() {
        assertEquals(emptyMap<String, Int>(), store("state.json").load())

        store("state.json").save(mapOf("page" to 4))
        assertEquals(mapOf("page" to 4), store("state.json").load())

        File(dir, "broken.json").writeText("{\"page\":")
        assertEquals(emptyMap<String, Int>(), store("broken.json").load())
    }
}
