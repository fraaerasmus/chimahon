package com.canopus.chimareader.opds

import kotlinx.serialization.Serializable

@Serializable
data class OpdsCatalog(
    val id: String,
    val name: String,
    val url: String,
    val username: String = "",
    val password: String = "",
)

/** The acquisition formats the browser can hand to an importer. */
enum class OpdsFormat(
    val label: String,
    val extensions: List<String>,
    private val mimeTypes: List<String>,
) {
    EPUB("EPUB", listOf("epub"), listOf("application/epub+zip")),

    /** Comic archives as calibre serves them (`application/x-cbz` / `x-cbr`) plus the registered vnd types. */
    COMIC(
        "CBZ",
        listOf("cbz", "cbr"),
        listOf(
            "application/x-cbz",
            "application/x-cbr",
            "application/vnd.comicbook+zip",
            "application/vnd.comicbook-rar",
        ),
    ),
    ;

    fun accepts(type: String?): Boolean =
        type != null && mimeTypes.any { type.startsWith(it, ignoreCase = true) }

    fun hasExtension(name: String): Boolean =
        extensions.any { name.endsWith(".$it", ignoreCase = true) }

    /** File extension for a link of [type]; RAR-based comics get `cbr`, everything else the first extension. */
    fun extensionFor(type: String?): String =
        extensions.firstOrNull { type?.contains(it, ignoreCase = true) == true }
            ?: if (this == COMIC && type?.contains("rar", ignoreCase = true) == true) "cbr" else extensions.first()
}

data class OpdsLink(
    val href: String,
    val rel: String?,
    val type: String?,
    val title: String? = null,
) {
    val isAtom: Boolean get() = type?.startsWith("application/atom+xml", ignoreCase = true) == true
    val isEpub: Boolean get() = OpdsFormat.EPUB.accepts(type)
    val isAcquisition: Boolean get() = rel?.startsWith("http://opds-spec.org/acquisition") == true
}

data class OpdsEntry(
    val id: String,
    val title: String,
    val authors: List<String>,
    val summary: String?,
    val links: List<OpdsLink>,
    /** Series name when the feed carries one (calibre writes `SERIES: name [index]` into the content). */
    val series: String? = null,
    val seriesIndex: Double? = null,
) {
    val navigationHref: String? get() = links.firstOrNull { it.isAtom && !it.isAcquisition }?.href
    val epubHref: String? get() = acquisitionHref(OpdsFormat.EPUB)
    val hasOtherFormatsOnly: Boolean get() = hasOtherFormatsOnly(OpdsFormat.EPUB)

    /**
     * The acquisition link to download for [format]. When an entry offers several matching
     * formats (calibre lists them alphabetically, so CBR comes before CBZ) the format's preferred
     * extension wins: only a CBZ can carry the server's `.mokuro` entry.
     */
    fun acquisitionLink(format: OpdsFormat): OpdsLink? =
        links.filter { it.isAcquisition && format.accepts(it.type) }
            .minByOrNull { format.extensions.indexOf(format.extensionFor(it.type)) }
    fun acquisitionHref(format: OpdsFormat): String? = acquisitionLink(format)?.href
    fun hasOtherFormatsOnly(format: OpdsFormat): Boolean = acquisitionHref(format) == null && links.any { it.isAcquisition }
}

data class OpdsFeed(
    val url: String,
    val title: String,
    val entries: List<OpdsEntry>,
    val nextHref: String?,
    /** OpenSearch URL template containing `{searchTerms}`, resolved. */
    val searchTemplate: String?,
    /** OpenSearch description document URL, when the feed only advertises that. */
    val searchDescriptionHref: String?,
)

class OpdsException(message: String, val statusCode: Int? = null) : Exception(message)
