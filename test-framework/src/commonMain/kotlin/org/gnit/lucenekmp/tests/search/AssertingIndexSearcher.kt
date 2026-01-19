package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexReaderContext
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.Collector
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Weight
import kotlin.random.Random

/**
 * Helper class that adds some extra checks to ensure correct usage of `IndexSearcher` and
 * `Weight`.
 */
open class AssertingIndexSearcher : IndexSearcher {
    val random: Random

    constructor(random: Random, r: IndexReader) : super(r) {
        this.random = Random(random.nextLong())
    }

    constructor(
        random: Random,
        context: IndexReaderContext
    ) : super(context) {
        this.random = Random(random.nextLong())
    }

    constructor(
        random: Random,
        r: IndexReader,
        ex: ExecutorService?
    ) : super(r, ex) {
        this.random = Random(random.nextLong())
    }

    constructor(
        random: Random,
        context: IndexReaderContext,
        ex: ExecutorService?
    ) : super(context, ex) {
        this.random = Random(random.nextLong())
    }

    @Throws(IOException::class)
    override fun createWeight(
        query: Query,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        // this adds assertions to the inner weights/scorers too
        return AssertingWeight(random, super.createWeight(query, scoreMode, boost), scoreMode)
    }

    @Throws(IOException::class)
    override fun rewrite(original: Query): Query {
        // TODO: use the more sophisticated QueryUtils.check sometimes!
        QueryUtils.check(original)
        val rewritten: Query = super.rewrite(original)
        QueryUtils.check(rewritten)
        return rewritten
    }

    @Throws(IOException::class)
    override fun search(
        leaves: Array<LeafReaderContextPartition>,
        weight: Weight,
        collector: Collector
    ) {
        assert(weight is AssertingWeight)
        val assertingCollector: AssertingCollector = AssertingCollector.wrap(collector)
        super.search(leaves, weight, assertingCollector)
        assert(assertingCollector.hasFinishedCollectingPreviousLeaf)
    }

    override fun toString(): String {
        return "AssertingIndexSearcher(" + super.toString() + ")"
    }
}
