package org.gnit.lucenekmp.search

import kotlinx.io.IOException


/**
 * An iterator over match positions (and optionally offsets) for a single document and field
 *
 *
 * To iterate over the matches, call [.next] until it returns `false`, retrieving
 * positions and/or offsets after each call. You should not call the position or offset methods
 * before [.next] has been called, or after [.next] has returned `false`.
 *
 *
 * Matches from some queries may span multiple positions. You can retrieve the positions of
 * individual matching terms on the current match by calling [.getSubMatches].
 *
 *
 * Matches are ordered by start position, and then by end position. Match intervals may overlap.
 *
 * @see Weight.matches
 * @lucene.experimental
 */
interface MatchesIterator {
    /**
     * Advance the iterator to the next match position
     *
     * @return `true` if matches have not been exhausted
     */
    @Throws(IOException::class)
    fun next(): Boolean

    /**
     * The start position of the current match, or `-1` if positions are not available
     *
     *
     * Should only be called after [.next] has returned `true`
     */
    fun startPosition(): Int

    /**
     * The end position of the current match, or `-1` if positions are not available
     *
     *
     * Should only be called after [.next] has returned `true`
     */
    fun endPosition(): Int

    /**
     * The starting offset of the current match, or `-1` if offsets are not available
     *
     *
     * Should only be called after [.next] has returned `true`
     */
    @Throws(IOException::class)
    fun startOffset(): Int

    /**
     * The ending offset of the current match, or `-1` if offsets are not available
     *
     *
     * Should only be called after [.next] has returned `true`
     */
    @Throws(IOException::class)
    fun endOffset(): Int

    @get:Throws(IOException::class)
    val subMatches: MatchesIterator?

    /**
     * Returns the Query causing the current match
     *
     *
     * If this [MatchesIterator] has been returned from a [.getSubMatches] call, then
     * returns a [TermQuery] equivalent to the current match
     *
     *
     * Should only be called after [.next] has returned `true`
     */
    val query: Query
}
