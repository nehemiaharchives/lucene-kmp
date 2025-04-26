package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.PriorityQueue
import kotlin.reflect.KClass


/** Util class for Scorer related methods  */
internal object ScorerUtil {
    private val DEFAULT_IMPACTS_ENUM_CLASS: KClass<*> = Lucene101PostingsFormat.getImpactsEnumImpl()
    private val DEFAULT_ACCEPT_DOCS_CLASS: KClass<*> = FixedBitSet(1).asReadOnlyBits()::class

    fun costWithMinShouldMatch(costs: Sequence<Long>, numScorers: Int, minShouldMatch: Int): Long {
        // the idea here is the following: a boolean query c1,c2,...cn with minShouldMatch=m
        // could be rewritten to:
        // (c1 AND (c2..cn|msm=m-1)) OR (!c1 AND (c2..cn|msm=m))
        // if we assume that clauses come in ascending cost, then
        // the cost of the first part is the cost of c1 (because the cost of a conjunction is
        // the cost of the least costly clause)
        // the cost of the second part is the cost of finding m matches among the c2...cn
        // remaining clauses
        // since it is a disjunction overall, the total cost is the sum of the costs of these
        // two parts

        // If we recurse infinitely, we find out that the cost of a msm query is the sum of the
        // costs of the num_scorers - minShouldMatch + 1 least costly scorers

        val pq: PriorityQueue<Long> =
            object : PriorityQueue<Long>(numScorers - minShouldMatch + 1) {
                override fun lessThan(a: Long, b: Long): Boolean {
                    return a > b
                }
            }
        costs.forEach(pq::insertWithOverflow)
        return pq.asSequence().sum()
    }

    /**
     * Optimize a [DocIdSetIterator] for the case when it is likely implemented via an [ ]. This return method only has 2 possible return types, which helps make sure that
     * calls to [DocIdSetIterator.nextDoc] and [DocIdSetIterator.advance] are
     * bimorphic at most and candidate for inlining.
     */
    fun likelyImpactsEnum(it: DocIdSetIterator): DocIdSetIterator {
        var it = it
        if (it::class !== DEFAULT_IMPACTS_ENUM_CLASS
            && it is FilterDocIdSetIterator
        ) {
            it = FilterDocIdSetIterator(it)
        }
        return it
    }

    /**
     * Optimize a [Scorable] for the case when it is likely implemented via a [ ]. This return method only has 2 possible return types, which helps make sure that
     * calls to [Scorable.score] are bimorphic at most and candidate for inlining.
     */
    fun likelyTermScorer(scorable: Scorable): Scorable {
        var scorable = scorable
        if (scorable::class !== TermScorer::class && scorable is FilterScorable) {
            scorable = FilterScorable(scorable)
        }
        return scorable
    }

    /**
     * Optimize [Bits] representing the set of accepted documents for the case when it is likely
     * implemented as live docs. This helps make calls to [Bits.get] inlinable, which
     * in-turn helps speed up query evaluation. This is especially helpful as inlining will sometimes
     * enable auto-vectorizing shifts and masks that are done in [FixedBitSet.get].
     */
    fun likelyLiveDocs(acceptDocs: Bits?): Bits? {
        return if (acceptDocs == null) {
            acceptDocs
        } else if (acceptDocs::class == DEFAULT_ACCEPT_DOCS_CLASS) {
            acceptDocs
        } else {
            FilterBits(acceptDocs)
        }
    }

    private class FilterBits(private val `in`: Bits) : Bits {

        override fun get(index: Int): Boolean {
            return `in`.get(index)
        }

        override fun length(): Int {
            return `in`.length()
        }
    }
}