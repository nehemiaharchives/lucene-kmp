package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.util.IntsRef

/**
 * [FiniteStringsIterator] which limits the number of iterated accepted strings. If more than
 * `limit` strings are accepted, the first `limit` strings found are returned.
 *
 *
 * If the [Automaton] has cycles then this iterator may throw an `IllegalArgumentException`, but this is not guaranteed!
 *
 *
 * Be aware that the iteration order is implementation dependent and may change across releases.
 *
 * @lucene.experimental
 */
class LimitedFiniteStringsIterator(a: Automaton, limit: Int) :
    FiniteStringsIterator(a) {
    /** Maximum number of finite strings to create.  */
    private val limit: Int

    /** Number of generated finite strings.  */
    private var count = 0

    /**
     * Constructor.
     *
     * @param a Automaton to create finite string from.
     * @param limit Maximum number of finite strings to create, or -1 for infinite.
     */
    init {
        require(!(limit != -1 && limit <= 0)) { "limit must be -1 (which means no limit), or > 0; got: $limit" }

        this.limit = if (limit > 0) limit else Int.Companion.MAX_VALUE
    }

    override fun next(): IntsRef? {
        if (count >= limit) {
            // Abort on limit.
            return null
        }

        val result: IntsRef? = super.next()
        if (result != null) {
            count++
        }

        return result
    }

    /** Number of iterated finite strings.  */
    fun size(): Int {
        return count
    }
}