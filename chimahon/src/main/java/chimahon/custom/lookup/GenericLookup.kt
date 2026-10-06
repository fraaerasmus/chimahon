package chimahon.custom.lookup

import chimahon.HoshiDicts
import chimahon.LookupResult
import chimahon.dictionary.DeinflectedLookup
import chimahon.dictionary.Deinflector
import chimahon.dictionary.FrenchLookupPolicy

/**
 * Lookups for the languages that go through a [Deinflector] (everything but Japanese). Upstream's
 * `DictionaryRepository` and `DictionaryTab` each try these first and keep their own code after
 * it, unreached.
 */
object GenericLookup {

    /**
     * The popup's lookup: every prefix of [query] is deinflected, inflected forms are followed to
     * their lemma, and results are filtered and ranked by part of speech.
     */
    fun forPopup(session: Long, query: String, deinflector: Deinflector, languageCode: String): List<LookupResult> {
        val queryResults = FrenchLookupPolicy.lookupQueries(query, languageCode).flatMap { lookupQuery ->
            DeinflectedLookup.lookup(lookupQuery, deinflector, languageCode, POPUP_RESULTS) { candidate ->
                HoshiDicts.query(session, candidate).toList()
            }
        }
        return FrenchLookupPolicy.mergeResults(queryResults, languageCode, POPUP_RESULTS)
    }

    /**
     * The Dictionary tab's lookup: the whole [query] is deinflected, as upstream does, with the
     * French elision and merge rules on top. It does not follow lemmas the way [forPopup] does.
     */
    fun forDictionaryTab(session: Long, query: String, deinflector: Deinflector, languageCode: String): List<LookupResult> {
        fun lookupOne(lookupQuery: String): List<LookupResult> {
            val preprocessed = deinflector.preProcess(lookupQuery)
            val deinflected = preprocessed.flatMap { deinflector.deinflect(it, languageCode) }
            val candidates = deinflected.map { it.text }.distinct()
            return candidates.flatMap { candidate ->
                HoshiDicts.lookup(session, candidate, TAB_RESULTS, 25).toList()
            }.distinctBy { it.term.expression to it.term.reading }.take(TAB_RESULTS)
        }

        val queryResults = FrenchLookupPolicy.lookupQueries(query, languageCode).flatMap(::lookupOne)
        return FrenchLookupPolicy.mergeResults(queryResults, languageCode, TAB_RESULTS)
    }

    private const val POPUP_RESULTS = 20
    private const val TAB_RESULTS = 50
}
