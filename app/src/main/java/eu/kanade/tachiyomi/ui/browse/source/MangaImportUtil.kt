package eu.kanade.tachiyomi.ui.browse.source

import tachiyomi.core.common.storage.nameWithoutExtension

object MangaImportUtil {
    // Illegal characters for filenames on most systems (Android is Linux based but SD cards might be FAT32/exFAT)
    private val illegalCharacters = Regex("""[\\/:*?"<>|]""")

    /**
     * Extracts the base series title by removing volume/chapter markers.
     * Example: "One Piece Vol. 1" -> "One Piece"
     */
    fun getBaseTitle(fileName: String): String = getSeriesTitle(fileName.substringBeforeLast("."))

    // Chimahon -->
    // Trailing volume/chapter markers as catalogs write them: ", Vol. 3", " Volume 3", " Book 1", " Ch. 12",
    // " 第3巻", or a bare trailing number.
    private val trailingMarker = Regex(
        """[\s,:\-–_]*(?:\b(?:v|vol|volume|ch|chapter|book|part|tome|no)\.?\s*|第\s*|\s)[0-9]+(?:\.[0-9]+)?\s*(?:巻|話|冊)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Series title for a catalog entry title that has no series metadata, so that every volume of
     * "Berserk, Vol. 3" style titles lands in one "Berserk" folder. Unlike [getBaseTitle] this takes
     * a title, not a file name, so a dot in the title is left alone.
     */
    fun getSeriesTitle(title: String): String {
        val stripped = trailingMarker.replace(title.trim(), "")
            .trim()
            .trimEnd(',', '-', '–', '_', ':')
            .trim()
        return stripped.ifBlank { title.trim() }
    }
    // Chimahon <--

    /**
     * Replaces illegal characters with underscores and trims the result.
     */
    fun getSafeFolderName(title: String): String {
        return illegalCharacters.replace(title, "_").trim()
    }
}
