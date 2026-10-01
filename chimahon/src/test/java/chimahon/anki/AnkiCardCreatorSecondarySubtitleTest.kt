package chimahon.anki

import android.content.ContextWrapper
import chimahon.LookupResult
import chimahon.MediaInfo
import chimahon.TermResult
import chimahon.TransformGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class AnkiCardCreatorSecondarySubtitleTest {

    @AfterEach
    fun tearDown() {
        AnkiCardCreator.resetBridgeFactoryForTests()
        AnkiCardCreator.resetFieldMapParserForTests()
    }

    @Test
    fun `secondary subtitle marker renders the line escaped with line breaks`() = runTest {
        val fields = addCard(MediaInfo("title", "episode", secondarySubtitle = "Tom & Jerry\nsecond line"))

        assertEquals("Tom &amp; Jerry<br>second line", fields["Extra"])
    }

    private suspend fun addCard(media: MediaInfo?): Map<String, String> {
        val bridge = FakeBridge()
        AnkiCardCreator.bridgeFactory = { bridge }
        AnkiCardCreator.fieldMapParser = { mapOf("Front" to "{expression}", "Extra" to "{secondary-subtitle}") }

        val result = AnkiCardCreator.addToAnki(
            context = TestContext,
            result = lookupResult(),
            deck = "deck",
            model = "model",
            fieldMapJson = "{}",
            tags = "",
            dupCheck = false,
            dupScope = "collection",
            dupAction = "prevent",
            media = media,
        )

        assertEquals(AnkiResult.Success(7), result)
        return bridge.addedFields.single()
    }

    private fun lookupResult(): LookupResult = LookupResult(
        matched = "word",
        deinflected = "word",
        process = emptyArray<TransformGroup>(),
        term = TermResult(
            expression = "word",
            reading = "word",
            rules = "",
            glossaries = emptyArray(),
            frequencies = emptyArray(),
            pitches = emptyArray(),
        ),
        preprocessorSteps = 0,
    )

    private class FakeBridge : AnkiCardBridge {
        val addedFields = mutableListOf<Map<String, String>>()

        override fun hasPermission(): Boolean = true
        override suspend fun ensureDefaultDeckName(): String = "deck"
        override suspend fun ensureLapisModelName(): String = "model"
        override suspend fun getDeckId(deckName: String): Long = 1L
        override suspend fun findNotes(expression: String, modelName: String?, deckId: Long?): List<Long> = emptyList()
        override suspend fun storeMedia(filename: String, data: ByteArray): String = filename
        override suspend fun storeMedia(source: AnkiSentenceAudioSource): String =
            "${source.preferredBaseName}.${source.extension}"
        override suspend fun addNote(deckName: String, modelName: String, fields: Map<String, String>, tags: List<String>): Long {
            addedFields += fields
            return 7L
        }
        override suspend fun updateNoteFields(noteId: Long, fields: Map<String, String>) = Unit
        override fun triggerSync() = Unit
    }

    private object TestContext : ContextWrapper(null) {
        private val files = Files.createTempDirectory("anki-card-creator-secondary-subtitle-test").toFile()
        override fun getFilesDir(): File = files
    }
}
