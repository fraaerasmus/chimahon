package eu.kanade.tachiyomi.data.dictionary

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DictionaryBackupManagerTest {

    private fun makeTempDir(): File {
        val dir = java.nio.file.Files.createTempDirectory("dict-test").toFile()
        dir.deleteOnExit()
        return dir
    }

    private fun dictFolder(dir: File, type: String, name: String, revision: String?) {
        val real = File(dir, "$type/$name")
        real.mkdirs()
        revision?.let { File(real, "index.json").writeText("""{"title":"$name","revision":"$it"}""") }
        File(real, "hash.table").writeText("blob-content")
    }

    @Test
    fun `export then import round-trips a dictionary folder`() {
        val source = makeTempDir()
        dictFolder(source, "term", "jmdict", "5")

        val bytes = ByteArrayOutputStream().also { out ->
            DictionaryBackupManager.export(source, out)
        }.toByteArray()

        val target = makeTempDir()
        val result = DictionaryBackupManager.import(
            input = ByteArrayInputStream(bytes),
            tempZip = File(target.parentFile, "tmp.zip"),
            dictionariesDir = target,
        )

        result shouldBe DictionaryBackupManager.ImportResult.Success(
            DictionaryBackupManager.ImportReport(imported = listOf("jmdict")),
        )
        File(target, "term/jmdict/hash.table").readText() shouldBe "blob-content"
        File(target, "term/jmdict/index.json").readText() shouldContain "\"revision\":\"5\""
    }

    @Test
    fun `skips dictionaries with the same revision and imports others`() {
        val source = makeTempDir()
        dictFolder(source, "term", "jmdict", "5")
        dictFolder(source, "term", "niojisho", "7")

        val bytes = ByteArrayOutputStream().also { output ->
            DictionaryBackupManager.export(source, output)
        }.toByteArray()

        val target = makeTempDir()
        dictFolder(target, "term", "jmdict", "5")
        File(target, "term/jmdict/hash.table").writeText("old-content")

        val result = DictionaryBackupManager.import(
            input = ByteArrayInputStream(bytes),
            tempZip = File(target.parentFile, "tmp.zip"),
            dictionariesDir = target,
        )

        val success = (result as DictionaryBackupManager.ImportResult.Success).report
        success.skipped shouldBe listOf("jmdict")
        success.imported shouldBe listOf("niojisho")

        // A duplicate with the same revision must not overwrite existing files.
        File(target, "term/jmdict/hash.table").readText() shouldBe "old-content"
    }

    @Test
    fun `rejects backups with an unsupported format version`() {
        val plainZip = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(DictionaryBackupManager.MANIFEST_NAME))
                zip.write("""{"format":99,"app":"test","exportedAt":0,"dictionaries":[]}""".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val result = DictionaryBackupManager.import(
            input = ByteArrayInputStream(plainZip),
            tempZip = File(makeTempDir().parentFile, "tmp.zip"),
            dictionariesDir = makeTempDir(),
        )

        (result as DictionaryBackupManager.ImportResult.Failure).message shouldBe
            "Unsupported backup format (99). Expected 1."
    }

    @Test
    fun `rejects a zip without a manifest`() {
        val plainZip = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("term/jmdict/hash.table"))
                zip.write("data".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val result = DictionaryBackupManager.import(
            input = ByteArrayInputStream(plainZip),
            tempZip = File(makeTempDir().parentFile, "tmp.zip"),
            dictionariesDir = makeTempDir(),
        )

        (result as DictionaryBackupManager.ImportResult.Failure).message shouldBe
            "No manifest.json found - not a dictionary backup"
    }
}
