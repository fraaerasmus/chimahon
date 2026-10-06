package chimahon.custom.lookup

import chimahon.HoshiDicts
import chimahon.LookupResult
import chimahon.dictionary.DeinflectedLookup
import chimahon.dictionary.Deinflector
import chimahon.dictionary.LookupPolicy

/**
 * The lookup for the languages that go through a [Deinflector] (everything but Japanese).
 * Upstream's `DictionaryRepository` and `DictionaryTab` each try this first and keep their own
 * code after it, unreached. The popup asks for 20 results and the Dictionary tab for 50; apart
 * from that the two get the same answer for the same text.
 */
object GenericLookup {

    /**
     * Every prefix of [query] is deinflected, inflected forms are followed to their lemma, and
     * results are filtered by part of speech and ranked.
     */
    fun lookup(
        session: Long,
        query: String,
        deinflector: Deinflector,
        languageCode: String,
        maxResults: Int,
    ): List<LookupResult> {
        val results = DeinflectedLookup.lookup(query, deinflector, languageCode, maxResults) { candidate ->
            HoshiDicts.query(session, candidate).toList()
        }
        return LookupPolicy.rank(results, languageCode, maxResults)
    }
}
