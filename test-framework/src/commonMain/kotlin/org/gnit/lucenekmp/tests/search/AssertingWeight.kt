package org.gnit.lucenekmp.tests.search

import okio.IOException

import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.BulkScorer
import org.gnit.lucenekmp.search.FilterWeight
import org.gnit.lucenekmp.search.Matches
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.random.Random

internal class AssertingWeight(
    random: Random,
    `in`: Weight,
    scoreMode: ScoreMode
) : FilterWeight(`in`) {
    val random: Random
    val scoreMode: ScoreMode

    init {
        this.random = random
        this.scoreMode = scoreMode
    }

    @Throws(IOException::class)
    override fun count(context: LeafReaderContext): Int {
        val count: Int = `in`.count(context)
        if (count < -1 || count > context.reader().numDocs()) {
            throw AssertionError(
                "count=" + count + ", numDocs=" + context.reader().numDocs()
            )
        }
        return count
    }

    @Throws(IOException::class)
    override fun matches(
        context: LeafReaderContext,
        doc: Int
    ): Matches? {
        val matches: Matches? = `in`.matches(context, doc)
        if (matches == null) return null
        return AssertingMatches(matches)
    }

    @Throws(IOException::class)
    override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
        val inScorerSupplier: ScorerSupplier? = `in`.scorerSupplier(context)
        if (inScorerSupplier == null) {
            return null
        }
        return object : ScorerSupplier() {
            private var getCalled = false
            private var topLevelScoringClause = false

            @Throws(IOException::class)
            override fun get(leadCost: Long): Scorer? {
                assert(getCalled == false)
                getCalled = true
                assert(leadCost >= 0) { leadCost }
                return AssertingScorer.wrap(
                    Random(random.nextLong()),
                    inScorerSupplier.get(leadCost),
                    scoreMode,
                    topLevelScoringClause
                )
            }

            @Throws(IOException::class)
            override fun bulkScorer(): BulkScorer {
                assert(getCalled == false)

                val inScorer: BulkScorer?
                // We explicitly test both the delegate's bulk scorer, and also the normal scorer.
                // This ensures that normal scorers are sometimes tested with an asserting wrapper.
                if (LuceneTestCase.usually(random)) {
                    getCalled = true
                    inScorer = inScorerSupplier.bulkScorer()
                } else {
                    // Don't set getCalled = true, since this calls #get under the hood
                    inScorer = super.bulkScorer()
                    assert(getCalled)
                }

                return AssertingBulkScorer.wrap(
                    Random(random.nextLong()), inScorer!!, context.reader().maxDoc()
                )
            }

            override fun cost(): Long {
                val cost: Long = inScorerSupplier.cost()
                assert(cost >= 0)
                return cost
            }

            override fun setTopLevelScoringClause() {
                assert(getCalled == false)
                topLevelScoringClause = true
                inScorerSupplier.setTopLevelScoringClause()
            }
        }
    }
}
