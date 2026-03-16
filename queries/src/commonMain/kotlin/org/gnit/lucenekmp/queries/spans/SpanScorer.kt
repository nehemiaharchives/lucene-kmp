package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.search.similarities.Similarity

/**
 * A basic [Scorer] over [Spans].
 *
 * @lucene.experimental
 */
open class SpanScorer(
    protected val spansInternal: Spans,
    protected val scorer: Similarity.SimScorer?,
    protected val norms: NumericDocValues?,
) : Scorer() {
    private var freq = 0f
    private var lastScoredDoc = -1

    /** return the Spans for this Scorer * */
    fun getSpans(): Spans {
        return spansInternal
    }

    override fun docID(): Int {
        return spansInternal.docID()
    }

    override fun iterator(): DocIdSetIterator {
        return spansInternal
    }

    override fun twoPhaseIterator(): TwoPhaseIterator? {
        return spansInternal.asTwoPhaseIterator()
    }

    /**
     * Score the current doc. The default implementation scores the doc with the similarity using the
     * slop-adjusted [freq].
     */
    @Throws(IOException::class)
    protected open fun scoreCurrentDoc(): Float {
        assert(scorer != null) { "${this::class} has a null docScorer!" }
        var norm = 1L
        if (norms != null && norms.advanceExact(docID())) {
            norm = norms.longValue()
        }
        return scorer!!.score(freq, norm)
    }

    /**
     * Sets [freq] for the current document.
     *
     * This will be called at most once per document.
     */
    @Throws(IOException::class)
    protected fun setFreqCurrentDoc() {
        freq = 0.0f

        spansInternal.doStartCurrentDoc()

        assert(spansInternal.startPosition() == -1) { "incorrect initial start position, $spansInternal" }
        assert(spansInternal.endPosition() == -1) { "incorrect initial end position, $spansInternal" }
        var prevStartPos = -1
        var prevEndPos = -1

        var startPos = spansInternal.nextStartPosition()
        assert(startPos != Spans.NO_MORE_POSITIONS) { "initial startPos NO_MORE_POSITIONS, $spansInternal" }
        do {
            assert(startPos >= prevStartPos)
            val endPos = spansInternal.endPosition()
            assert(endPos != Spans.NO_MORE_POSITIONS)
            assert(startPos != prevStartPos || endPos >= prevEndPos) { "decreased endPos=$endPos" }
            if (scorer == null) {
                freq = 1f
                return
            }
            freq += (1.0f / (1.0f + spansInternal.width()))
            spansInternal.doCurrentSpans()
            prevStartPos = startPos
            prevEndPos = endPos
            startPos = spansInternal.nextStartPosition()
        } while (startPos != Spans.NO_MORE_POSITIONS)

        assert(spansInternal.startPosition() == Spans.NO_MORE_POSITIONS) { "incorrect final start position, $spansInternal" }
        assert(spansInternal.endPosition() == Spans.NO_MORE_POSITIONS) { "incorrect final end position, $spansInternal" }
    }

    @Throws(IOException::class)
    private fun ensureFreq() {
        val currentDoc = docID()
        if (lastScoredDoc != currentDoc) {
            setFreqCurrentDoc()
            lastScoredDoc = currentDoc
        }
    }

    @Throws(IOException::class)
    override fun score(): Float {
        ensureFreq()
        return scoreCurrentDoc()
    }

    @Throws(IOException::class)
    override fun getMaxScore(upTo: Int): Float {
        return Float.POSITIVE_INFINITY
    }

    /**
     * Returns the intermediate "sloppy freq" adjusted for edit distance
     *
     * @lucene.internal
     */
    @Throws(IOException::class)
    fun sloppyFreq(): Float {
        ensureFreq()
        return freq
    }
}
