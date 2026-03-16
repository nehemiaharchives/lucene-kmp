package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TwoPhaseIterator

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

    private inline fun assertCondition(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw AssertionError(message())
        }
    }

    @Throws(IOException::class)
    override fun nextStartPosition(): Int {
        assertCondition(state != State.DOC_START) { "invalid position access, state=$state: ${`in`}" }
        assertCondition(state != State.DOC_FINISHED) { "invalid position access, state=$state: ${`in`}" }
        assertCondition(state != State.DOC_UNVERIFIED) { "invalid position access, state=$state: ${`in`}" }
        checkCurrentPositions()
        val prev = `in`.startPosition()
        val start = `in`.nextStartPosition()
        assertCondition(start >= prev) { "invalid startPosition (positions went backwards, previous=$prev): ${`in`}" }
        state = if (start == NO_MORE_POSITIONS) State.POS_FINISHED else State.ITERATING
        checkCurrentPositions()
        return start
    }

    private fun checkCurrentPositions() {
        val start = `in`.startPosition()
        val end = `in`.endPosition()
        if (state == State.DOC_START || state == State.DOC_UNVERIFIED || state == State.POS_START) {
            assertCondition(start == -1) { "invalid startPosition (should be -1): ${`in`}" }
            assertCondition(end == -1) { "invalid endPosition (should be -1): ${`in`}" }
        } else if (state == State.POS_FINISHED) {
            assertCondition(start == NO_MORE_POSITIONS) { "invalid startPosition (should be NO_MORE_POSITIONS): ${`in`}" }
            assertCondition(end == NO_MORE_POSITIONS) { "invalid endPosition (should be NO_MORE_POSITIONS): ${`in`}" }
        } else {
            assertCondition(start >= 0) { "invalid startPosition (negative): ${`in`}" }
            assertCondition(start <= end) { "invalid startPosition (> endPosition): ${`in`}" }
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
        assertCondition(state == State.ITERATING) { "width() called in illegal state: $state: ${`in`}" }
        val distance = `in`.width()
        assertCondition(distance >= 0) { "width() returned negative distance=$distance: ${`in`}" }
        return distance
    }

    @Throws(IOException::class)
    override fun collect(collector: SpanCollector) {
        assertCondition(state == State.ITERATING) { "collect() called in illegal state: $state: ${`in`}" }
        `in`.collect(collector)
    }

    override fun docID(): Int {
        val currentDoc = `in`.docID()
        assertCondition(currentDoc == doc) { "broken docID() impl: docID() = $currentDoc, but next/advance last returned: $doc: ${`in`}" }
        return currentDoc
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        assertCondition(state != State.DOC_FINISHED) { "nextDoc() called after NO_MORE_DOCS: ${`in`}" }
        val nextDoc = `in`.nextDoc()
        assertCondition(nextDoc > doc) { "backwards nextDoc from $doc to $nextDoc: ${`in`}" }
        if (nextDoc == NO_MORE_DOCS) {
            state = State.DOC_FINISHED
        } else {
            assertCondition(`in`.startPosition() == -1) { "invalid initial startPosition() [should be -1]: ${`in`}" }
            assertCondition(`in`.endPosition() == -1) { "invalid initial endPosition() [should be -1]: ${`in`}" }
            state = State.POS_START
        }
        doc = nextDoc
        return docID()
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        assertCondition(state != State.DOC_FINISHED) { "advance() called after NO_MORE_DOCS: ${`in`}" }
        assertCondition(target > doc) { "target must be > docID(), got $target <= $doc: ${`in`}" }
        val advanced = `in`.advance(target)
        assertCondition(advanced >= target) { "backwards advance from: $target to: $advanced: ${`in`}" }
        if (advanced == NO_MORE_DOCS) {
            state = State.DOC_FINISHED
        } else {
            assertCondition(`in`.startPosition() == -1) { "invalid initial startPosition() [should be -1]: ${`in`}" }
            assertCondition(`in`.endPosition() == -1) { "invalid initial endPosition() [should be -1]: ${`in`}" }
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
        assertCondition(!cost.isNaN()) { "positionsCost() should not be NaN" }
        assertCondition(cost > 0) { "positionsCost() must be positive" }
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
            assertCondition(disiIn.docID() == this@AssertingSpans.docID()) {
                "broken disi docID() impl: disiIn.docID()=${disiIn.docID()} outer.docID()=${this@AssertingSpans.docID()}"
            }
            return disiIn.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            assertCondition(state != State.DOC_FINISHED) { "nextDoc() called after NO_MORE_DOCS: $disiIn" }
            val nextDoc = disiIn.nextDoc()
            assertCondition(nextDoc > doc) { "backwards nextDoc from $doc to $nextDoc: $disiIn" }
            state = if (nextDoc == NO_MORE_DOCS) State.DOC_FINISHED else State.DOC_UNVERIFIED
            doc = nextDoc
            return docID()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            assertCondition(state != State.DOC_FINISHED) { "advance() called after NO_MORE_DOCS: $disiIn" }
            assertCondition(target > doc) { "target must be > docID(), got $target <= $doc: $disiIn" }
            val advanced = disiIn.advance(target)
            assertCondition(advanced >= target) { "backwards advance from: $target to: $advanced: $disiIn" }
            state = if (advanced == NO_MORE_DOCS) State.DOC_FINISHED else State.DOC_UNVERIFIED
            doc = advanced
            return docID()
        }

        override fun cost(): Long {
            return disiIn.cost()
        }
    }
}
