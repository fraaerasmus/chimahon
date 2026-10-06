package chimahon.novel.opds

import chimahon.custom.core.ServerException
import chimahon.custom.core.TinyHttpServer
import chimahon.novel.kosync.KosyncDocumentId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Base64

/**
 * The acceptance property for cross-device sync: a book fetched from a catalog has to reach disk
 * with the server's exact bytes, because KOReader derives the document id from them.
 */
class OpdsDownloadTest {
    @TempDir
    lateinit var tempDir: File

    private val server = TinyHttpServer { request ->
        val path = request.path
        fun file(disposition: String? = null) = TinyHttpServer.Response(
            200,
            payload,
            disposition?.let { mapOf("Content-Disposition" to it) }.orEmpty(),
        )
        when {
            request.header("Authorization") == null -> TinyHttpServer.Response(401)
            path.startsWith("/get/epub/18") -> file("attachment; filename=\"Same Dream.epub\"")
            path.startsWith("/get/cbz/20") -> file("attachment; filename=\"Berserk, Vol. 3 - Kentaro Miura.cbz\"")
            path.startsWith("/get/cbr/21") -> file()
            else -> TinyHttpServer.Response(404)
        }
    }

    // Large enough that the transfer crosses many read buffers and three partial-MD5 sample windows.
    private val payload = ByteArray(300 * 1024) { ((it * 31 + 7) and 0xff).toByte() }

    @AfterEach
    fun stop() = server.close()

    private val base get() = server.url

    private val catalog = OpdsCatalog(id = "c", name = "calibre", url = "", username = "reader", password = "hunter2")

    @Test
    fun `download keeps the server's exact bytes`() = runBlocking {
        var lastSeen = 0L
        val result = OpdsClient().download(
            catalog = catalog,
            url = "$base/get/epub/18/calibre",
            directory = tempDir,
            fallbackName = "fallback.epub",
            onProgress = { done, _ -> lastSeen = done },
        )

        assertArrayEquals(payload, result.file.readBytes())
        assertEquals(payload.size.toLong(), lastSeen)
        assertEquals("Same Dream.epub", result.fileName)

        // The id KOReader would compute has to survive the transfer unchanged.
        val reference = File(tempDir, "reference.epub").apply { writeBytes(payload) }
        assertEquals(KosyncDocumentId.partialMd5(reference), KosyncDocumentId.partialMd5(result.file))
    }

    @Test
    fun `comic downloads keep the archive bytes and extension`() = runBlocking {
        val named = OpdsClient().download(
            catalog = catalog,
            url = "$base/get/cbz/20/calibre",
            directory = tempDir,
            fallbackName = "Berserk, Vol. 3.cbz",
            format = OpdsFormat.COMIC,
        )
        assertArrayEquals(payload, named.file.readBytes())
        assertEquals("Berserk, Vol. 3 - Kentaro Miura.cbz", named.fileName)
        assertTrue(named.file.name.endsWith(".cbz"))

        // No Content-Disposition and no extension in the URL: the fallback's extension is kept.
        val unnamed = OpdsClient().download(catalog, "$base/get/cbr/21/calibre", tempDir, "Dune, Book 1.cbr", OpdsFormat.COMIC)
        assertEquals("Dune, Book 1.cbr", unnamed.fileName)
        assertEquals(KosyncDocumentId.partialMd5(named.file), KosyncDocumentId.partialMd5(unnamed.file))
    }

    @Test
    fun `download sends basic auth`() = runBlocking {
        OpdsClient().download(catalog, "$base/get/epub/18/calibre", tempDir, "fallback.epub")
        val expected = "Basic " + Base64.getEncoder().encodeToString("reader:hunter2".toByteArray())
        assertEquals(expected, server.requests.last().header("Authorization"))
    }

    @Test
    fun `download surfaces a server error instead of writing a file`() {
        val error = assertThrows(ServerException::class.java) {
            runBlocking { OpdsClient().download(catalog, "$base/get/epub/missing", tempDir, "fallback.epub") }
        }
        assertEquals(404, error.statusCode)
        val leftovers = tempDir.list().orEmpty().filter { it.startsWith("opds-") }
        assertEquals(emptyList<String>(), leftovers)
    }
}
