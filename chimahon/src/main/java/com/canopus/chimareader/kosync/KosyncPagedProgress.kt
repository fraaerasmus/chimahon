package com.canopus.chimareader.kosync

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * KOReader's progress encoding for documents with pages (CBZ, PDF): `progress` is the 1-based page
 * number and `percentage` is that page over the page count. On pull KOReader applies
 * `GotoPage(tonumber(progress))`, so the page number is what has to round-trip.
 */
object KosyncPagedProgress {
    fun progress(pageIndex: Int, pageCount: Int): String =
        (pageIndex + 1).coerceIn(1, maxOf(pageCount, 1)).toString()

    fun percentage(pageIndex: Int, pageCount: Int): Double =
        if (pageCount <= 0) 0.0 else ((pageIndex + 1).toDouble() / pageCount).coerceIn(0.0, 1.0)

    /** Page index for a remote position; the page number wins over the percentage when it parses. */
    fun pageIndex(progress: String?, percentage: Double?, pageCount: Int): Int? {
        if (pageCount <= 0) return null
        val page = progress?.trim()?.toDoubleOrNull()?.let { floor(it).toInt() }
            ?: percentage?.let { (it * pageCount).roundToInt() }
            ?: return null
        return (page - 1).coerceIn(0, pageCount - 1)
    }
}
