package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TwoPhaseIterator

/**
 * Iterates through combinations of start/end positions per-doc. Each start/end position represents
 * a range of term positions within the current document. These are enumerated in order, by
 * increasing document number, within that by increasing start position and finally by increasing
 * end position.
 */
abstract class Spans : DocIdSetIterator() {
    /**
     * Returns the next start position for the current doc. There is always at least one start/end
     * position per doc. After the last start/end position at the current doc this returns
     * [NO_MORE_POSITIONS].
     */
    @Throws(IOException::class)
    abstract fun nextStartPosition(): Int

    /**
     * Returns the start position in the current doc, or -1 when [nextStartPosition] was not yet
     * called on the current doc. After the last start/end position at the current doc this returns
     * [NO_MORE_POSITIONS].
     */
    abstract fun startPosition(): Int

    /**
     * Returns the end position for the current start position, or -1 when [nextStartPosition] was
     * not yet called on the current doc. After the last start/end position at the current doc this
     * returns [NO_MORE_POSITIONS].
     */
    abstract fun endPosition(): Int

    /**
     * Return the width of the match, which is typically used to sloppy freq.
     */
    abstract fun width(): Int

    /**
     * Collect postings data from the leaves of the current Spans.
     *
     * @lucene.experimental
     */
    @Throws(IOException::class)
    abstract fun collect(collector: SpanCollector)

    /**
     * Return an estimation of the cost of using the positions of this [Spans] for any single
     * document.
     *
     * @lucene.experimental
     */
    abstract fun positionsCost(): Float

    /**
     * Optional method: Return a [TwoPhaseIterator] view of this [Spans]. A return value of `null`
     * indicates that two-phase iteration is not supported.
     */
    open fun asTwoPhaseIterator(): TwoPhaseIterator? {
        return null
    }

    override fun toString(): String {
        val clazz = this::class
        val simpleName = clazz.simpleName ?: clazz.toString()
        return "$simpleName(doc=${docID()},start=${startPosition()},end=${endPosition()})"
    }

    /** Called before the current doc's frequency is calculated */
    @Throws(IOException::class)
    internal open fun doStartCurrentDoc() {}

    /** Called each time the scorer's SpanScorer is advanced during frequency calculation */
    @Throws(IOException::class)
    internal open fun doCurrentSpans() {}

    companion object {
        const val NO_MORE_POSITIONS: Int = Int.MAX_VALUE
    }
}
