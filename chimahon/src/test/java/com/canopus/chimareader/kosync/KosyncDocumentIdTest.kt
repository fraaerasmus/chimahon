package com.canopus.chimareader.kosync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KosyncDocumentIdTest {
    @TempDir
    lateinit var tempDir: File

    // Expected digests come from a Python port of KOReader's util.partialMD5 over the same data.
    @Test
    fun `partial md5 matches KOReader sampling`() {
        assertEquals("0fb3683d664e8486ed6dd0302b45574c", KosyncDocumentId.partialMd5(file(500, 1)))
        assertEquals("cfbde19bab78b2390852a21fa57a1f8b", KosyncDocumentId.partialMd5(file(1024, 2)))
        assertEquals("7a2b0a5a4e8fb9fbfbfc2647805113b8", KosyncDocumentId.partialMd5(file(5000, 3)))
        assertEquals("63ecb39f176e22b361918258f1263547", KosyncDocumentId.partialMd5(file(70000, 4)))
    }

    @Test
    fun `only the sampled windows affect the id`() {
        // In a 5000 byte file the samples cover 0-1024, 1024-2048 and 4096-5000, so a byte at 3000
        // is never read. Editing it must leave the id alone; editing a sampled byte must change it.
        val baseline = KosyncDocumentId.partialMd5(file(5000, 3))
        assertEquals(baseline, KosyncDocumentId.partialMd5(file(5000, 3, flipAt = 3000)))
        assertNotEquals(baseline, KosyncDocumentId.partialMd5(file(5000, 3, flipAt = 100)))
    }

    private fun file(size: Int, seed: Long, flipAt: Int? = null) = File(tempDir, "f-$size-$flipAt").apply {
        var x = seed
        val bytes = ByteArray(size) {
            x = (x * 1103515245L + 12345L) and 0x7fffffffL
            ((x shr 16) and 0xff).toByte()
        }
        flipAt?.let { bytes[it] = (bytes[it].toInt() xor 0xff).toByte() }
        writeBytes(bytes)
    }
}
