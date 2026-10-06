package chimahon.custom.kosync

import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import kotlinx.coroutines.CoroutineScope
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * What the manga reader's view model needs from KOReader sync, so the view model itself only
 * carries one-line calls.
 *
 * [ownerOf] resolves the series a chapter belongs to (a merged series holds several) and
 * [incognito] is checked after the enabled switch, as the view model's incognito lookup is lazy.
 */
class MangaKosyncReaderHook(
    private val ownerOf: (ReaderChapter) -> Manga?,
    private val incognito: () -> Boolean,
    private val manager: MangaKosyncManager = Injekt.get(),
) {
    fun notePageTurn(chapter: ReaderChapter) {
        chapter.chapter.id?.let(manager::notePageTurn)
    }

    /**
     * The chapter being left is pushed before [next] is loaded so the server holds the page the
     * reader was on, matching the novel reader's push on close.
     */
    fun pushOnLeave(scope: CoroutineScope, previous: ReaderChapter?, next: ReaderChapter) {
        val leaving = previous?.takeIf { it != next } ?: return
        scope.launchNonCancellable { push(leaving) }
    }

    /**
     * Moves [chapter] to a newer position from the server before it is shown, and returns that
     * page index for the caller's saved-state index, which would otherwise win over the pulled page.
     */
    suspend fun pullInto(chapter: ReaderChapter): Int? {
        val index = pull(chapter) ?: return null
        chapter.requestedPage = index
        chapter.chapter.last_page_read = index
        return index
    }

    /** A newer page index from the server for [chapter], or null when the local position stands. */
    suspend fun pull(chapter: ReaderChapter?): Int? {
        chapter ?: return null
        if (!manager.isEnabled || incognito()) return null
        val owner = ownerOf(chapter) ?: return null
        val pageCount = chapter.pages?.size ?: return null
        val domainChapter = chapter.chapter.toDomainChapter() ?: return null
        return runCatching { manager.pull(owner, domainChapter, pageCount) }
            .onFailure { logcat(LogPriority.WARN, it) { "kosync: pull failed for ${chapter.chapter.name}" } }
            .getOrNull()
    }

    /** Pushes the page [chapter] is on. */
    suspend fun push(chapter: ReaderChapter?) {
        chapter ?: return
        if (!manager.isEnabled || incognito()) return
        val owner = ownerOf(chapter) ?: return
        val pageCount = chapter.pages?.size ?: return
        val domainChapter = chapter.chapter.toDomainChapter() ?: return
        runCatching { manager.push(owner, domainChapter, chapter.chapter.last_page_read, pageCount) }
            .onFailure { logcat(LogPriority.WARN, it) { "kosync: push failed for ${chapter.chapter.name}" } }
    }
}
