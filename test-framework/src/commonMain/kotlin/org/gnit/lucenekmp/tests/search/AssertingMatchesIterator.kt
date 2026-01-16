package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.MatchesIterator
import org.gnit.lucenekmp.search.Query

internal class AssertingMatchesIterator(private val `in`: MatchesIterator) :
    MatchesIterator {
    private var state = State.UNPOSITIONED

    private enum class State {
        UNPOSITIONED,
        ITERATING,
        EXHAUSTED
    }

    @Throws(IOException::class)
    override fun next(): Boolean {
        assert(state != State.EXHAUSTED) { state }
        val more: Boolean = `in`.next()
        if (more == false) {
            state = State.EXHAUSTED
        } else {
            state = State.ITERATING
        }
        return more
    }

    override fun startPosition(): Int {
        assert(state == State.ITERATING) { state }
        return `in`.startPosition()
    }

    override fun endPosition(): Int {
        assert(state == State.ITERATING) { state }
        return `in`.endPosition()
    }

    @Throws(IOException::class)
    override fun startOffset(): Int {
        assert(state == State.ITERATING) { state }
        return `in`.startOffset()
    }

    @Throws(IOException::class)
    override fun endOffset(): Int {
        assert(state == State.ITERATING) { state }
        return `in`.endOffset()
    }

    override val subMatches: MatchesIterator?
        get() {
            assert(state == State.ITERATING) { state }
            return `in`.subMatches
        }

    override val query: Query
        get() {
            assert(state == State.ITERATING) { state }
            return `in`.query
        }
}
