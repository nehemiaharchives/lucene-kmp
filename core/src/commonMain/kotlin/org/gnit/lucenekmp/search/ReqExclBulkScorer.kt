package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.Bits
import kotlinx.io.IOException
import kotlin.math.min

internal class ReqExclBulkScorer : BulkScorer {
    private val req: BulkScorer
    private val exclApproximation: DocIdSetIterator
    private val exclTwoPhase: TwoPhaseIterator?

    constructor(req: BulkScorer, excl: Scorer) {
        this.req = req
        this.exclTwoPhase = excl.twoPhaseIterator()
        if (exclTwoPhase != null) {
            this.exclApproximation = exclTwoPhase.approximation()
        } else {
            this.exclApproximation = excl.iterator()
        }
    }

    constructor(req: BulkScorer, excl: DocIdSetIterator) {
        this.req = req
        this.exclTwoPhase = null
        this.exclApproximation = excl
    }

    constructor(req: BulkScorer, excl: TwoPhaseIterator) {
        this.req = req
        this.exclTwoPhase = excl
        this.exclApproximation = excl.approximation()
    }

    @Throws(IOException::class)
    override fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int): Int {
        var upTo = min
        var exclDoc = exclApproximation.docID()

        while (upTo < max) {
            if (exclDoc < upTo) {
                exclDoc = exclApproximation.advance(upTo)
            }
            if (exclDoc == upTo) {
                if (exclTwoPhase == null || exclTwoPhase.matches()) {
                    // upTo is excluded so we can consider that we scored up to upTo+1
                    upTo += 1
                }
                exclDoc = exclApproximation.nextDoc()
            } else {
                upTo = req.score(collector, acceptDocs, upTo, min(exclDoc, max))
            }
        }

        if (upTo == max) {
            upTo = req.score(collector, acceptDocs, upTo, upTo)
        }

        return upTo
    }

    override fun cost(): Long {
        return req.cost()
    }
}
