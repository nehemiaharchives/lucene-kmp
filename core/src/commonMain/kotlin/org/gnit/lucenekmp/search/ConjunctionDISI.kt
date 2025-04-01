package org.gnit.lucenekmp.search


import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.CollectionUtil
import kotlin.math.min

/**
 * A conjunction of DocIdSetIterators. Requires that all of its sub-iterators must be on the same
 * document all the time. This iterates over the doc ids that are present in each given
 * DocIdSetIterator. <br></br>
 *
 * @lucene.internal
 */
internal class ConjunctionDISI private constructor(iterators: MutableList<out DocIdSetIterator>) : DocIdSetIterator() {
    val lead1: DocIdSetIterator
    val lead2: DocIdSetIterator
    val others: Array<DocIdSetIterator>

    init {
        require(iterators.size >= 2)

        // Sort the array the first time to allow the least frequent DocsEnum to
        // lead the matching.
        CollectionUtil.timSort(iterators) { o1, o2 -> Long.compare(o1.cost(), o2.cost()) }
        lead1 = iterators[0]
        lead2 = iterators[1]
        others = iterators.subList(2, iterators.size).toTypedArray<DocIdSetIterator>()
    }

    @Throws(IOException::class)
    private fun doNext(doc: Int): Int {
        var doc = doc
        advanceHead@ while (true) {
            require(doc == lead1.docID())

            // find agreement between the two iterators with the lower costs
            // we special case them because they do not need the
            // 'other.docID() < doc' check that the 'others' iterators need
            val next2 = lead2.advance(doc)
            if (next2 != doc) {
                doc = lead1.advance(next2)
                if (next2 != doc) {
                    continue
                }
            }

            // then find agreement with other iterators
            for (other in others) {
                // other.doc may already be equal to doc if we "continued advanceHead"
                // on the previous iteration and the advance on the lead scorer exactly matched.
                if (other.docID() < doc) {
                    val next = other.advance(doc)

                    if (next > doc) {
                        // iterator beyond the current doc - advance lead and continue to the new highest doc.
                        doc = lead1.advance(next)
                        continue@advanceHead
                    }
                }
            }

            // success - all iterators are on the same doc
            return doc
        }
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        require(
            assertItersOnSameDoc()
        ) { "Sub-iterators of ConjunctionDISI are not one the same document!" }
        return doNext(lead1.advance(target))
    }

    override fun docID(): Int {
        return lead1.docID()
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        require(
            assertItersOnSameDoc()
        ) { "Sub-iterators of ConjunctionDISI are not on the same document!" }
        return doNext(lead1.nextDoc())
    }

    override fun cost(): Long {
        return lead1.cost() // overestimate
    }

    // Returns {@code true} if all sub-iterators are on the same doc ID, {@code false} otherwise
    private fun assertItersOnSameDoc(): Boolean {
        val curDoc = lead1.docID()
        var iteratorsOnTheSameDoc = (lead2.docID() == curDoc)
        var i = 0
        while ((i < others.size && iteratorsOnTheSameDoc)) {
            iteratorsOnTheSameDoc = iteratorsOnTheSameDoc && (others[i].docID() == curDoc)
            i++
        }
        return iteratorsOnTheSameDoc
    }

    /** Conjunction between a [DocIdSetIterator] and one or more [BitSetIterator]s.  */
    private class BitSetConjunctionDISI(
        val lead: DocIdSetIterator,
        bitSetIterators: MutableCollection<BitSetIterator>
    ) : DocIdSetIterator() {
        val bitSetIterators: Array<BitSetIterator>
        private val bitSets: Array<BitSet?>
        private val minLength: Int

        init {
            require(bitSetIterators.isNotEmpty())

            this.bitSetIterators = bitSetIterators.toTypedArray<BitSetIterator>()
            // Put the least costly iterators first so that we exit as soon as possible
            ArrayUtil.timSort(this.bitSetIterators) { a, b -> Long.compare(a.cost(), b.cost()) }
            this.bitSets = kotlin.arrayOfNulls<BitSet>(this.bitSetIterators.size)
            var minLen = Int.Companion.MAX_VALUE
            for (i in this.bitSetIterators.indices) {
                val bitSet: BitSet = this.bitSetIterators[i].bitSet
                this.bitSets[i] = bitSet
                minLen = min(minLen, bitSet.length())
            }
            this.minLength = minLen
        }

        override fun docID(): Int {
            return lead.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            require(
                assertItersOnSameDoc()
            ) { "Sub-iterators of ConjunctionDISI are not on the same document!" }
            return doNext(lead.nextDoc())
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            require(
                assertItersOnSameDoc()
            ) { "Sub-iterators of ConjunctionDISI are not on the same document!" }
            return doNext(lead.advance(target))
        }

        @Throws(IOException::class)
        fun doNext(doc: Int): Int {
            var doc = doc
            advanceLead@ while (true) {
                if (doc >= minLength) {
                    if (doc != NO_MORE_DOCS) {
                        lead.advance(NO_MORE_DOCS)
                    }
                    return NO_MORE_DOCS
                }
                for (bitSet in bitSets) {
                    if (bitSet!!.get(doc) == false) {
                        doc = lead.nextDoc()
                        continue@advanceLead
                    }
                }
                for (iterator in bitSetIterators) {
                    iterator.setDocId(doc)
                }
                return doc
                doc = lead.nextDoc()
            }
        }

        override fun cost(): Long {
            return lead.cost()
        }

        // Returns {@code true} if all sub-iterators are on the same doc ID, {@code false} otherwise
        fun assertItersOnSameDoc(): Boolean {
            val curDoc = lead.docID()
            var iteratorsOnTheSameDoc = true
            var i = 0
            while ((i < bitSetIterators.size && iteratorsOnTheSameDoc)) {
                iteratorsOnTheSameDoc = iteratorsOnTheSameDoc && (bitSetIterators[i].docID() == curDoc)
                i++
            }
            return iteratorsOnTheSameDoc
        }
    }

    /** [TwoPhaseIterator] implementing a conjunction.  */
    private class ConjunctionTwoPhaseIterator(
        approximation: DocIdSetIterator,
        twoPhaseIterators: MutableList<out TwoPhaseIterator>
    ) : TwoPhaseIterator(
        approximation
    ) {
        val twoPhaseIterators: Array<TwoPhaseIterator>
        private val matchCost: Float

        init {
            require(twoPhaseIterators.isNotEmpty())

            CollectionUtil.timSort(
                twoPhaseIterators
            ) { o1, o2 -> Float.compare(o1.matchCost(), o2.matchCost()) }

            this.twoPhaseIterators =
                twoPhaseIterators.toTypedArray<TwoPhaseIterator>()

            // Compute the matchCost as the total matchCost of the sub iterators.
            // TODO: This could be too high because the matching is done cheapest first: give the lower
            // matchCosts a higher weight.
            var totalMatchCost = 0f
            for (tpi in twoPhaseIterators) {
                totalMatchCost += tpi.matchCost()
            }
            matchCost = totalMatchCost
        }

        @Throws(IOException::class)
        override fun matches(): Boolean {
            for (twoPhaseIterator in twoPhaseIterators) { // match cheapest first
                if (twoPhaseIterator.matches() == false) {
                    return false
                }
            }
            return true
        }

        override fun matchCost(): Float {
            return matchCost
        }
    }

    companion object {
        /**
         * Adds the scorer, possibly splitting up into two phases or collapsing if it is another
         * conjunction
         */
        fun addScorer(
            scorer: Scorer,
            allIterators: MutableList<DocIdSetIterator>,
            twoPhaseIterators: MutableList<TwoPhaseIterator>
        ) {
            val twoPhaseIter = scorer.twoPhaseIterator()
            if (twoPhaseIter != null) {
                addTwoPhaseIterator(twoPhaseIter, allIterators, twoPhaseIterators)
            } else { // no approximation support, use the iterator as-is
                addIterator(scorer.iterator(), allIterators, twoPhaseIterators)
            }
        }

        fun addIterator(
            disi: DocIdSetIterator,
            allIterators: MutableList<DocIdSetIterator>,
            twoPhaseIterators: MutableList<TwoPhaseIterator>
        ) {
            val twoPhase = TwoPhaseIterator.unwrap(disi)
            if (twoPhase != null) {
                addTwoPhaseIterator(twoPhase, allIterators, twoPhaseIterators)
            } else if (disi is ConjunctionDISI) { // Check for exactly this class for collapsing
                val conjunction = disi
                // subconjuctions have already split themselves into two phase iterators and others, so we can
                // take those
                // iterators as they are and move them up to this conjunction
                allIterators.add(conjunction.lead1)
                allIterators.add(conjunction.lead2)
                allIterators.addAll(conjunction.others)
            } else if (disi is BitSetConjunctionDISI) {
                val conjunction = disi
                allIterators.add(conjunction.lead)
                allIterators.addAll(conjunction.bitSetIterators)
            } else {
                allIterators.add(disi)
            }
        }

        fun addTwoPhaseIterator(
            twoPhaseIter: TwoPhaseIterator,
            allIterators: MutableList<DocIdSetIterator>,
            twoPhaseIterators: MutableList<TwoPhaseIterator>
        ) {
            addIterator(twoPhaseIter.approximation(), allIterators, twoPhaseIterators)
            if (twoPhaseIter is ConjunctionTwoPhaseIterator) { // Check for exactly this class for collapsing
                twoPhaseIterators.addAll(twoPhaseIter.twoPhaseIterators)
            } else {
                twoPhaseIterators.add(twoPhaseIter)
            }
        }

        fun createConjunction(
            allIterators: MutableList<DocIdSetIterator>, twoPhaseIterators: MutableList<TwoPhaseIterator>
        ): DocIdSetIterator {
            // check that all sub-iterators are on the same doc ID

            val curDoc =
                if (allIterators.isNotEmpty())
                    allIterators[0].docID()
                else
                    twoPhaseIterators[0].approximation.docID()
            var minCost = Long.Companion.MAX_VALUE
            for (allIterator in allIterators) {
                if (allIterator.docID() != curDoc) {
                    throwSubIteratorsNotOnSameDocument()
                }
                minCost = min(allIterator.cost(), minCost)
            }
            for (it in twoPhaseIterators) {
                if (it.approximation().docID() != curDoc) {
                    throwSubIteratorsNotOnSameDocument()
                }
            }
            val bitSetIterators: MutableList<BitSetIterator> = mutableListOf<BitSetIterator>()
            val iterators: MutableList<DocIdSetIterator> = mutableListOf<DocIdSetIterator>()
            for (iterator in allIterators) {
                if (iterator is BitSetIterator && iterator.cost() > minCost) {
                    // we put all bitset iterators into bitSetIterators
                    // except if they have the minimum cost, since we need
                    // them to lead the iteration in that case
                    bitSetIterators.add(iterator)
                } else {
                    iterators.add(iterator)
                }
            }

            var disi: DocIdSetIterator
            disi = if (iterators.size == 1) {
                iterators[0]
            } else {
                ConjunctionDISI(iterators)
            }

            if (bitSetIterators.isNotEmpty()) {
                disi = BitSetConjunctionDISI(disi, bitSetIterators)
            }

            if (twoPhaseIterators.isEmpty() == false) {
                disi =
                    TwoPhaseIterator.asDocIdSetIterator(
                        ConjunctionTwoPhaseIterator(disi, twoPhaseIterators)
                    )
            }

            return disi
        }

        private fun throwSubIteratorsNotOnSameDocument() {
            throw IllegalArgumentException(
                "Sub-iterators of ConjunctionDISI are not on the same document!"
            )
        }
    }
}
