package chimahon.novel.kosync

import chimahon.novel.data.Bookmark
import kotlinx.serialization.Serializable

data class KosyncCredentials(
    val serverUrl: String,
    val username: String,
    val userKey: String,
)

data class KosyncRemoteProgress(
    val progress: String?,
    val percentage: Double?,
    val device: String?,
    val deviceId: String?,
    /** Server-assigned, unix seconds. */
    val timestamp: Long?,
)

sealed interface KosyncResult {
    data class Pulled(val title: String, val percentage: Double, val bookmark: Bookmark? = null) : KosyncResult
    data class Pushed(val title: String, val percentage: Double) : KosyncResult
    data class UpToDate(val title: String) : KosyncResult
    data object Skipped : KosyncResult

    /** The book has no stored source EPUB, so it cannot be identified the way KOReader does. */
    data class NoDocumentId(val title: String) : KosyncResult
}

/** Per-book kosync bookkeeping, stored as `kosync.json` beside `bookmark.json`. */
@Serializable
data class KosyncBookState(
    val lastSyncedCharacterCount: Int? = null,
    val lastServerTimestamp: Long? = null,
)
