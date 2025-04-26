package org.gnit.lucenekmp.search


import org.gnit.lucenekmp.search.ConjunctionDISI.Companion.addScorer

/** Helper methods for building conjunction iterators  */
object ConjunctionUtils {
    /**
     * Create a conjunction over the provided [Scorer]s. Note that the returned [ ] might leverage two-phase iteration in which case it is possible to retrieve
     * the [TwoPhaseIterator] using [TwoPhaseIterator.unwrap].
     */
    fun intersectScorers(scorers: MutableCollection<Scorer>): DocIdSetIterator {
        require(scorers.size >= 2) { "Cannot make a ConjunctionDISI of less than 2 iterators" }
        val allIterators: MutableList<DocIdSetIterator> = ArrayList()
        val twoPhaseIterators: MutableList<TwoPhaseIterator> = ArrayList()
        for (scorer in scorers) {
            addScorer(scorer, allIterators, twoPhaseIterators)
        }

        return ConjunctionDISI.createConjunction(allIterators, twoPhaseIterators)
    }

    /**
     * Create a conjunction over the provided DocIdSetIterators. Note that the returned [ ] might leverage two-phase iteration in which case it is possible to retrieve
     * the [TwoPhaseIterator] using [TwoPhaseIterator.unwrap].
     */
    fun intersectIterators(iterators: MutableList<out DocIdSetIterator>): DocIdSetIterator {
        require(iterators.size >= 2) { "Cannot make a ConjunctionDISI of less than 2 iterators" }
        val allIterators: MutableList<DocIdSetIterator> = ArrayList()
        val twoPhaseIterators: MutableList<TwoPhaseIterator> = ArrayList()
        for (iterator in iterators) {
            addIterator(iterator, allIterators, twoPhaseIterators)
        }

        return ConjunctionDISI.createConjunction(allIterators, twoPhaseIterators)
    }

    /**
     * Create a conjunction over the provided set of DocIdSetIterators and TwoPhaseIterators, using
     * two-phase iterator where possible. Note that the returned [DocIdSetIterator] might
     * leverage two-phase iteration in which case it is possible to retrieve the [ ] using [TwoPhaseIterator.unwrap].
     *
     * @param allIterators a list of DocIdSetIterators to combine
     * @param twoPhaseIterators a list of TwoPhaseIterators to combine
     */
    fun createConjunction(
        allIterators: MutableList<DocIdSetIterator>, twoPhaseIterators: MutableList<TwoPhaseIterator>
    ): DocIdSetIterator {
        return ConjunctionDISI.createConjunction(allIterators, twoPhaseIterators)
    }

    /**
     * Given a two-phase iterator, find any sub-iterators and add them to the provided
     * DocIdSetIterator and TwoPhaseIterator lists
     */
    fun addTwoPhaseIterator(
        twoPhaseIter: TwoPhaseIterator,
        allIterators: MutableList<DocIdSetIterator>,
        twoPhaseIterators: MutableList<TwoPhaseIterator>
    ) {
        ConjunctionDISI.addTwoPhaseIterator(twoPhaseIter, allIterators, twoPhaseIterators)
    }

    /**
     * Given a DocIdSetIterator, find any sub-iterators or two-phase iterators and add them to the
     * provided DocIdSetIterator and TwoPhaseIterator lists
     */
    fun addIterator(
        disi: DocIdSetIterator,
        allIterators: MutableList<DocIdSetIterator>,
        twoPhaseIterators: MutableList<TwoPhaseIterator>
    ) {
        ConjunctionDISI.addIterator(disi, allIterators, twoPhaseIterators)
    }
}
