package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TwoPhaseIterator
import kotlin.test.assertTrue

/** Wraps a Spans with additional asserts */
class AssertingSpans(val `in`: Spans) : Spans() {
    var doc = -1

    /** tracks current state of this spans */
    enum class State {
        /** document iteration has not yet begun (`docID() = -1`) */
        DOC_START,

        /**
         * two-phase iterator has moved to a new docid, but `matches()` has not been called or it
         * returned false.
         */
        DOC_UNVERIFIED,

        /** iterator set to a valid docID, but position iteration has not yet begun */
        POS_START,

        /** iterator set to a valid docID, and positioned */
        ITERATING,

        /** positions exhausted */
        POS_FINISHED,

        /** documents exhausted */
        DOC_FINISHED
    }

    var state = State.DOC_START

    @Throws(IOException::class)
    override fun nextStartPosition(): Int {
        assertTrue(state != State.DOC_START, "invalid position access, state=$state: ${`in`}")
        assertTrue(state != State.DOC_FINISHED, "invalid position access, state=$state: ${`in`}")
        assertTrue(state != State.DOC_UNVERIFIED, "invalid position access, state=$state: ${`in`}")
        checkCurrentPositions()
        val prev = `in`.startPosition()
        val start = `in`.nextStartPosition()
        assertTrue(start >= prev, "invalid startPosition (positions went backwards, previous=$prev): ${`in`}")
        state = if (start == NO_MORE_POSITIONS) State.POS_FINISHED else State.ITERATING
        checkCurrentPositions()
        return start
    }

    private fun checkCurrentPositions() {
        val start = `in`.startPosition()
        val end = `in`.endPosition()
        if (state == State.DOC_START || state == State.DOC_UNVERIFIED || state == State.POS_START) {
            assertTrue(start == -1, "invalid startPosition (should be -1): ${`in`}")
            assertTrue(end == -1, "invalid endPosition (should be -1): ${`in`}")
        } else if (state == State.POS_FINISHED) {
            assertTrue(start == NO_MORE_POSITIONS, "invalid startPosition (should be NO_MORE_POSITIONS): ${`in`}")
            assertTrue(end == NO_MORE_POSITIONS, "invalid endPosition (should be NO_MORE_POSITIONS): ${`in`}")
        } else {
            assertTrue(start >= 0, "invalid startPosition (negative): ${`in`}")
            assertTrue(start <= end, "invalid startPosition (> endPosition): ${`in`}")
        }
    }

    override fun startPosition(): Int {
        checkCurrentPositions()
        return `in`.startPosition()
    }

    override fun endPosition(): Int {
        checkCurrentPositions()
        return `in`.endPosition()
    }

    override fun width(): Int {
        assertTrue(state == State.ITERATING)
        val distance = `in`.width()
        assertTrue(distance >= 0)
        return distance
    }

    @Throws(IOException::class)
    override fun collect(collector: SpanCollector) {
        assertTrue(state == State.ITERATING, "collect() called in illegal state: $state: ${`in`}")
        `in`.collect(collector)
    }

    override fun docID(): Int {
        val currentDoc = `in`.docID()
        assertTrue(currentDoc == doc, "broken docID() impl: docID() = $currentDoc, but next/advance last returned: $doc: ${`in`}")
        return currentDoc
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        assertTrue(state != State.DOC_FINISHED, "nextDoc() called after NO_MORE_DOCS: ${`in`}")
        val nextDoc = `in`.nextDoc()
        assertTrue(nextDoc > doc, "backwards nextDoc from $doc to $nextDoc: ${`in`}")
        if (nextDoc == NO_MORE_DOCS) {
            state = State.DOC_FINISHED
        } else {
            assertTrue(`in`.startPosition() == -1, "invalid initial startPosition() [should be -1]: ${`in`}")
            assertTrue(`in`.endPosition() == -1, "invalid initial endPosition() [should be -1]: ${`in`}")
            state = State.POS_START
        }
        doc = nextDoc
        return docID()
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        assertTrue(state != State.DOC_FINISHED, "advance() called after NO_MORE_DOCS: ${`in`}")
        assertTrue(target > doc, "target must be > docID(), got $target <= $doc: ${`in`}")
        val advanced = `in`.advance(target)
        assertTrue(advanced >= target, "backwards advance from: $target to: $advanced: ${`in`}")
        if (advanced == NO_MORE_DOCS) {
            state = State.DOC_FINISHED
        } else {
            assertTrue(`in`.startPosition() == -1, "invalid initial startPosition() [should be -1]: ${`in`}")
            assertTrue(`in`.endPosition() == -1, "invalid initial endPosition() [should be -1]: ${`in`}")
            state = State.POS_START
        }
        doc = advanced
        return docID()
    }

    override fun toString(): String {
        return "Asserting(${`in`})"
    }

    override fun cost(): Long {
        return `in`.cost()
    }

    override fun positionsCost(): Float {
        val cost = `in`.positionsCost()
        assertTrue(!cost.isNaN(), "positionsCost() should not be NaN")
        assertTrue(cost > 0, "positionsCost() must be positive")
        return cost
    }

    override fun asTwoPhaseIterator(): TwoPhaseIterator? {
        val iterator = `in`.asTwoPhaseIterator() ?: return null
        return AssertingTwoPhaseView(iterator)
    }

    inner class AssertingTwoPhaseView(val twoPhaseIn: TwoPhaseIterator) :
        TwoPhaseIterator(AssertingDISI(twoPhaseIn.approximation())) {
        var lastDoc = -1

        @Throws(IOException::class)
        override fun matches(): Boolean {
            if (approximation().docID() == -1 || approximation().docID() == NO_MORE_DOCS) {
                throw AssertionError("matches() should not be called on doc ID ${approximation().docID()}")
            }
            if (lastDoc == approximation().docID()) {
                throw AssertionError("matches() has been called twice on doc ID ${approximation().docID()}")
            }
            lastDoc = approximation().docID()
            val v = twoPhaseIn.matches()
            if (v) {
                state = State.POS_START
            }
            return v
        }

        override fun matchCost(): Float {
            val cost = twoPhaseIn.matchCost()
            if (cost.isNaN()) {
                throw AssertionError("matchCost()=$cost should not be NaN on doc ID ${approximation().docID()}")
            }
            if (cost < 0) {
                throw AssertionError("matchCost()=$cost should be non negative on doc ID ${approximation().docID()}")
            }
            return cost
        }
    }

    inner class AssertingDISI(val disiIn: DocIdSetIterator) : DocIdSetIterator() {
        override fun docID(): Int {
            assertTrue(disiIn.docID() == this@AssertingSpans.docID())
            return disiIn.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            assertTrue(state != State.DOC_FINISHED, "nextDoc() called after NO_MORE_DOCS: $disiIn")
            val nextDoc = disiIn.nextDoc()
            assertTrue(nextDoc > doc, "backwards nextDoc from $doc to $nextDoc: $disiIn")
            state = if (nextDoc == NO_MORE_DOCS) State.DOC_FINISHED else State.DOC_UNVERIFIED
            doc = nextDoc
            return docID()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            assertTrue(state != State.DOC_FINISHED, "advance() called after NO_MORE_DOCS: $disiIn")
            assertTrue(target > doc, "target must be > docID(), got $target <= $doc: $disiIn")
            val advanced = disiIn.advance(target)
            assertTrue(advanced >= target, "backwards advance from: $target to: $advanced: $disiIn")
            state = if (advanced == NO_MORE_DOCS) State.DOC_FINISHED else State.DOC_UNVERIFIED
            doc = advanced
            return docID()
        }

        override fun cost(): Long {
            return disiIn.cost()
        }
    }
}
