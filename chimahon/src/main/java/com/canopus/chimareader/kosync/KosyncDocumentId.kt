package com.canopus.chimareader.kosync

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Document identity as KOReader computes it (`plugins/kosync.koplugin`). */
object KosyncDocumentId {
    /**
     * KOReader's `util.partialMD5`: twelve 1 KiB samples fed into a single streaming MD5, stopping
     * at the first sample that reads short or past EOF, lowercase hex.
     *
     * KOReader derives the offsets as `lshift(1024, -2)` upward. That first step overflows to 0 in
     * LuaJIT rather than producing a fraction, so the offsets are listed literally here; computing
     * them from the shift expression would not reproduce the leading zero.
     */
    fun partialMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(SAMPLE_SIZE)
        RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            for (offset in SAMPLE_OFFSETS) {
                if (offset >= length) break
                input.seek(offset)
                val read = input.read(buffer, 0, SAMPLE_SIZE)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private const val SAMPLE_SIZE = 1024

    private val SAMPLE_OFFSETS: List<Long> = listOf(
        0L,
        1024L,
        4096L,
        16384L,
        65536L,
        262144L,
        1048576L,
        4194304L,
        16777216L,
        67108864L,
        268435456L,
        1073741824L,
    )

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
