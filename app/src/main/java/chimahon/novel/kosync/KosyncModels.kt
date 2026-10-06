package chimahon.novel.kosync

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

/** Per-book kosync bookkeeping, stored as `kosync.json` beside `bookmark.json`. */
@Serializable
data class KosyncBookState(
    val lastSyncedCharacterCount: Int? = null,
    val lastServerTimestamp: Long? = null,
)
