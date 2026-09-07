package eu.kanade.tachiyomi.data.upload

/** The ComicInfo `LanguageISO` value for a source language, or null when the source is not language-specific. */
object ComicInfoLanguage {
    private val untagged = setOf("", "all", "other", "multi", "unknown", "localsourcelang")

    fun fromSourceLang(lang: String?): String? {
        val base = lang.orEmpty().trim().lowercase().substringBefore('-').substringBefore('_')
        if (base in untagged || base.length !in 2..3 || base.any { it !in 'a'..'z' }) return null
        return base
    }
}
