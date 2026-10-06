package chimahon.custom.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class AtomicWriteTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `writes a new file, creating its directory`() {
        val file = File(dir, "nested/state.json")

        file.writeTextAtomic("first")

        assertEquals("first", file.readText())
        assertEquals(listOf("state.json"), file.parentFile.list()?.toList())
    }

    @Test
    fun `replaces existing content`() {
        val file = File(dir, "state.json").apply { writeText("old") }

        file.writeTextAtomic("new")

        assertEquals("new", file.readText())
        assertEquals(listOf("state.json"), dir.list()?.toList())
    }

    @Test
    fun `a write that fails part-way keeps the old content and leaves nothing behind`() {
        val file = File(dir, "state.json").apply { writeText("old") }

        assertThrows(IOException::class.java) {
            file.writeAtomic { temp ->
                temp.writeText("half of the new cont")
                throw IOException("disk full")
            }
        }

        assertEquals("old", file.readText())
        assertEquals(listOf("state.json"), dir.list()?.toList())
    }
}
