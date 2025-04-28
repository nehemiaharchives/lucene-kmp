package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.Bits
import kotlinx.io.IOException

/**
 * BulkScorer implementation of [ConjunctionScorer]. For simplicity, it focuses on scorers
 * that produce regular [DocIdSetIterator]s and not [TwoPhaseIterator]s.
 */
internal class ConjunctionBulkScorer(requiredScoring: MutableList<Scorer>, requiredNoScoring: MutableList<Scorer>) :
    BulkScorer() {
    private val scoringScorers: Array<Scorable>
    private val lead1: DocIdSetIterator
    private val lead2: DocIdSetIterator
    private val others: MutableList<DocIdSetIterator>
    private val scorable: Scorable

    init {
        val numClauses = requiredScoring.size + requiredNoScoring.size
        require(numClauses > 1) { "Expected 2 or more clauses, got $numClauses" }
        val allScorers: MutableList<Scorer> = ArrayList()
        allScorers.addAll(requiredScoring)
        allScorers.addAll(requiredNoScoring)

        this.scoringScorers =
            requiredScoring.map(ScorerUtil::likelyTermScorer).toTypedArray<Scorable>()
        val iterators: MutableList<DocIdSetIterator> = ArrayList()
        for (scorer in allScorers) {
            iterators.add(scorer.iterator())
        }
        iterators.sortBy { it.cost() }
        lead1 = iterators[0]
        lead2 = iterators[1]
        others = iterators.subList(2, iterators.size).toMutableList()
        scorable =
            object : Scorable() {
                @Throws(IOException::class)
                override fun score(): Float {
                    var score = 0.0
                    for (scorer in scoringScorers) {
                        score += scorer.score()
                    }
                    return score.toFloat()
                }

                @get:Throws(IOException::class)
                override val children: MutableCollection<ChildScorable> get(){
                    val children: ArrayList<ChildScorable> = ArrayList()
                    for (scorer in allScorers) {
                        children.add(ChildScorable(scorer, "MUST"))
                    }
                    return children
                }
            }
    }

    @Throws(IOException::class)
    override fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int): Int {
        require(lead1.docID() >= lead2.docID())

        if (lead1.docID() < min) {
            lead1.advance(min)
        }

        if (lead1.docID() >= max) {
            return lead1.docID()
        }

        collector.setScorer(scorable)

        var otherIterators = this.others
        val collectorIterator = collector.competitiveIterator()
        if (collectorIterator != null) {
            otherIterators = ArrayList(otherIterators)
            otherIterators.add(collectorIterator)
        }

        val others: Array<DocIdSetIterator> =
            otherIterators.toTypedArray<DocIdSetIterator>()

        // In the main for loop, we want to be able to rely on the invariant that lead1.docID() >
        // lead2.doc(). However it's possible that these two are equal on the first document in a
        // scoring window. So we treat this case separately here.
        if (lead1.docID() == lead2.docID()) {
            val doc = lead1.docID()
            if (acceptDocs == null || acceptDocs.get(doc)) {
                var match = true
                for (it in others) {
                    if (it.docID() < doc) {
                        val next = it.advance(doc)
                        if (next != doc) {
                            lead1.advance(next)
                            match = false
                            break
                        }
                    }
                    require(it.docID() == doc)
                }

                if (match) {
                    collector.collect(doc)
                    lead1.nextDoc()
                }
            } else {
                lead1.nextDoc()
            }
        }

        var doc = lead1.docID()
        advanceHead@ while (doc < max) {
            require(lead2.docID() < doc)

            if (acceptDocs != null && !acceptDocs.get(doc)) {
                doc = lead1.nextDoc()
                continue
            }

            // We maintain the invariant that lead2.docID() < lead1.docID() so that we don't need to check
            // if lead2 is already on the same doc as lead1 here.
            val next2 = lead2.advance(doc)
            if (next2 != doc) {
                doc = lead1.advance(next2)
                if (doc != next2) {
                    continue
                } else if (doc >= max) {
                    break
                } else if (acceptDocs != null && !acceptDocs.get(doc)) {
                    doc = lead1.nextDoc()
                    continue
                }
            }
            require(lead2.docID() == doc)

            for (it in others) {
                if (it.docID() < doc) {
                    val next = it.advance(doc)
                    if (next != doc) {
                        doc = lead1.advance(next)
                        continue@advanceHead
                    }
                }
                require(it.docID() == doc)
            }

            collector.collect(doc)
            doc = lead1.nextDoc()
        }
        return lead1.docID()
    }

    override fun cost(): Long {
        return lead1.cost()
    }
}
