package org.gnit.lucenekmp.search

import okio.IOException

internal class MultiLeafFieldComparator(
    comparators: Array<LeafFieldComparator>,
    reverseMul: IntArray
) : LeafFieldComparator {
    private val comparators: Array<LeafFieldComparator>
    private val reverseMul: IntArray

    // we extract the first comparator to avoid array access in the common case
    // that the first comparator compares worse than the bottom entry in the queue
    private val firstComparator: LeafFieldComparator
    private val firstReverseMul: Int

    init {
        require(comparators.size == reverseMul.size) {
            ("Must have the same number of comparators and reverseMul, got "
                    + comparators.size
                    + " and "
                    + reverseMul.size)
        }
        this.comparators = comparators
        this.reverseMul = reverseMul
        this.firstComparator = comparators[0]
        this.firstReverseMul = reverseMul[0]
    }

    @Throws(IOException::class)
    override fun setBottom(slot: Int) {
        for (comparator in comparators) {
            comparator.setBottom(slot)
        }
    }

    @Throws(IOException::class)
    override fun compareBottom(doc: Int): Int {
        var cmp: Int = firstReverseMul * firstComparator.compareBottom(doc)
        if (cmp != 0) {
            return cmp
        }
        for (i in 1..<comparators.size) {
            cmp = reverseMul[i] * comparators[i].compareBottom(doc)
            if (cmp != 0) {
                return cmp
            }
        }
        return 0
    }

    @Throws(IOException::class)
    override fun compareTop(doc: Int): Int {
        var cmp: Int = firstReverseMul * firstComparator.compareTop(doc)
        if (cmp != 0) {
            return cmp
        }
        for (i in 1..<comparators.size) {
            cmp = reverseMul[i] * comparators[i].compareTop(doc)
            if (cmp != 0) {
                return cmp
            }
        }
        return 0
    }

    @Throws(IOException::class)
    override fun copy(slot: Int, doc: Int) {
        for (comparator in comparators) {
            comparator.copy(slot, doc)
        }
    }

    @Throws(IOException::class)
    override fun setScorer(scorer: Scorable) {
        for (comparator in comparators) {
            comparator.setScorer(scorer)
        }
    }

    @Throws(IOException::class)
    override fun setHitsThresholdReached() {
        // this is needed for skipping functionality that is only relevant for the 1st comparator
        firstComparator.setHitsThresholdReached()
    }

    @Throws(IOException::class)
    override fun competitiveIterator(): DocIdSetIterator? {
        // this is needed for skipping functionality that is only relevant for the 1st comparator
        return firstComparator.competitiveIterator()
    }
}
