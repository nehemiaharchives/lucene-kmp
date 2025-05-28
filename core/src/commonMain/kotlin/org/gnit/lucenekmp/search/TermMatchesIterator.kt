package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.PostingsEnum


/** A [MatchesIterator] over a single term's postings list  */
internal class TermMatchesIterator(query: Query, pe: PostingsEnum) : MatchesIterator {
    private var upto: Int
    private var pos = 0
    private val pe: PostingsEnum
    override val query: Query

    /** Create a new [TermMatchesIterator] for the given term and postings list  */
    init {
        this.pe = pe
        this.query = query
        this.upto = pe.freq()
    }

    @Throws(IOException::class)
    override fun next(): Boolean {
        if (upto-- > 0) {
            pos = pe.nextPosition()
            return true
        }
        return false
    }

    override fun startPosition(): Int {
        return pos
    }

    override fun endPosition(): Int {
        return pos
    }

    @Throws(IOException::class)
    override fun startOffset(): Int {
        return pe.startOffset()
    }

    @Throws(IOException::class)
    override fun endOffset(): Int {
        return pe.endOffset()
    }

    override val subMatches: MatchesIterator?
        get() {
            return null
        }

}
