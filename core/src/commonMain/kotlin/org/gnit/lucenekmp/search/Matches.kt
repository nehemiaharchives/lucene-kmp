package org.gnit.lucenekmp.search

import kotlinx.io.IOException


/**
 * Reports the positions and optionally offsets of all matching terms in a query for a single
 * document
 *
 *
 * To obtain a [MatchesIterator] for a particular field, call [.getMatches].
 * Note that you can call [.getMatches] multiple times to retrieve new iterators, but
 * it is not thread-safe.
 *
 * @lucene.experimental
 */
interface Matches : Iterable<String> {
    /**
     * Returns a [MatchesIterator] over the matches for a single field, or `null` if there
     * are no matches in that field.
     */
    @Throws(IOException::class)
    fun getMatches(field: String): MatchesIterator?

    /**
     * Returns a collection of Matches that make up this instance; if it is not a composite, then this
     * returns an empty list
     */
    val subMatches: MutableCollection<Matches>
}
