package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.search.ConjunctionUtils
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TwoPhaseIterator

/** Common super class for multiple sub spans required in a document. */
abstract class ConjunctionSpans(subSpans: List<Spans>) : Spans() {
    protected val subSpansInternal: Array<Spans> = subSpans.toTypedArray()
    val conjunction: DocIdSetIterator = intersectSpans(subSpans)
    protected var atFirstInCurrentDoc: Boolean = true
    protected var oneExhaustedInCurrentDoc: Boolean = false

    init {
        require(subSpans.size >= 2) { "Less than 2 subSpans.size():${subSpans.size}" }
    }

    override fun docID(): Int {
        return conjunction.docID()
    }

    override fun cost(): Long {
        return conjunction.cost()
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        return if (conjunction.nextDoc() == NO_MORE_DOCS) NO_MORE_DOCS else toMatchDoc()
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        return if (conjunction.advance(target) == NO_MORE_DOCS) NO_MORE_DOCS else toMatchDoc()
    }

    @Throws(IOException::class)
    private fun toMatchDoc(): Int {
        oneExhaustedInCurrentDoc = false
        while (true) {
            if (twoPhaseCurrentDocMatches()) {
                return docID()
            }
            if (conjunction.nextDoc() == NO_MORE_DOCS) {
                return NO_MORE_DOCS
            }
        }
    }

    @Throws(IOException::class)
    protected abstract fun twoPhaseCurrentDocMatches(): Boolean

    override fun asTwoPhaseIterator(): TwoPhaseIterator {
        var totalMatchCost = 0f
        for (spans in subSpansInternal) {
            val tpi = spans.asTwoPhaseIterator()
            totalMatchCost += tpi?.matchCost() ?: spans.positionsCost()
        }
        val matchCost = totalMatchCost
        return object : TwoPhaseIterator(conjunction) {
            @Throws(IOException::class)
            override fun matches(): Boolean {
                return twoPhaseCurrentDocMatches()
            }

            override fun matchCost(): Float {
                return matchCost
            }
        }
    }

    override fun positionsCost(): Float {
        throw UnsupportedOperationException()
    }

    fun getSubSpans(): Array<Spans> {
        return subSpansInternal
    }

    companion object {
        private fun intersectSpans(spanList: List<Spans>): DocIdSetIterator {
            require(spanList.size >= 2) { "Cannot make a ConjunctionDISI of less than 2 iterators" }
            val allIterators = ArrayList<DocIdSetIterator>()
            val twoPhaseIterators = ArrayList<TwoPhaseIterator>()
            for (spans in spanList) {
                addSpans(spans, allIterators, twoPhaseIterators)
            }
            return ConjunctionUtils.createConjunction(allIterators, twoPhaseIterators)
        }

        private fun addSpans(
            spans: Spans,
            allIterators: MutableList<DocIdSetIterator>,
            twoPhaseIterators: MutableList<TwoPhaseIterator>,
        ) {
            val twoPhaseIter = spans.asTwoPhaseIterator()
            if (twoPhaseIter != null) {
                ConjunctionUtils.addTwoPhaseIterator(twoPhaseIter, allIterators, twoPhaseIterators)
            } else {
                ConjunctionUtils.addIterator(spans, allIterators, twoPhaseIterators)
            }
        }
    }
}
