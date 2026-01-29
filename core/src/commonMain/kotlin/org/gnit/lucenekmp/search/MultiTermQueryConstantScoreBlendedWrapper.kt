package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.util.DocIdSetBuilder
import org.gnit.lucenekmp.util.PriorityQueue


/**
 * This class provides the functionality behind [ ][MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE]. It maintains a boolean query-like approach over a
 * limited number of the most costly terms while rewriting the remaining terms into a filter bitset.
 */
internal class MultiTermQueryConstantScoreBlendedWrapper<Q : MultiTermQuery>(query : Q) : AbstractMultiTermQueryConstantScoreWrapper<Q>(query = query) {
    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return object : RewritingWeight(query, boost, scoreMode, searcher) {
            @Throws(IOException::class)
            override fun rewriteInner(
                context: LeafReaderContext,
                fieldDocCount: Int,
                terms: Terms,
                termsEnum: TermsEnum,
                collectedTerms: MutableList<TermAndState>,
                leadCost: Long
            ): WeightOrDocIdSetIterator {
                val otherTerms = DocIdSetBuilder(context.reader().maxDoc(), terms)
                val highFrequencyTerms: PriorityQueue<PostingsEnum> =
                    object : PriorityQueue<PostingsEnum>(collectedTerms.size) {
                        override fun lessThan(a: PostingsEnum, b: PostingsEnum): Boolean {
                            return a.cost() < b.cost()
                        }
                    }

                // Handle the already-collected terms:
                var reuse: PostingsEnum? = null
                if (!collectedTerms.isEmpty()) {
                    val termsEnum2: TermsEnum = terms.iterator()
                    for (t in collectedTerms) {
                        termsEnum2.seekExact(t.term, t.state)
                        reuse = termsEnum2.postings(reuse, PostingsEnum.NONE.toInt())
                        if (t.docFreq <= POSTINGS_PRE_PROCESS_THRESHOLD) {
                            otherTerms.add(reuse!!)
                        } else {
                            highFrequencyTerms.add(reuse!!)
                            reuse = null // can't reuse since we haven't processed the postings
                        }
                    }
                }

                // Then collect remaining terms:
                do {
                    reuse = termsEnum.postings(reuse, PostingsEnum.NONE.toInt())
                    // If a term contains all docs with a value for the specified field, we can discard the
                    // other terms and just use the dense term's postings:
                    val docFreq: Int = termsEnum.docFreq()
                    if (fieldDocCount == docFreq) {
                        val termStates = TermStates(searcher.topReaderContext)
                        termStates.register(
                            termsEnum.termState(), context.ord, docFreq, termsEnum.totalTermFreq()
                        )
                        val q: Query =
                            ConstantScoreQuery(
                                TermQuery(Term((query as MultiTermQuery).field, termsEnum.term()!!), termStates)
                            )
                        val weight: Weight? = searcher.rewrite(q).createWeight(searcher, scoreMode, score())
                        return WeightOrDocIdSetIterator(weight!!)
                    }

                    if (docFreq <= POSTINGS_PRE_PROCESS_THRESHOLD) {
                        otherTerms.add(reuse!!)
                    } else {
                        val dropped: PostingsEnum? = highFrequencyTerms.insertWithOverflow(reuse!!)
                        if (dropped != null) {
                            otherTerms.add(dropped)
                        }
                        // Reuse the postings that drop out of the PQ. Note that `dropped` will be null here
                        // if nothing is evicted, meaning we will _not_ reuse any postings (which is intentional
                        // since we can't reuse postings that are in the PQ).
                        reuse = dropped
                    }
                } while (termsEnum.next() != null)

                val subs: MutableList<DisiWrapper> = ArrayList(highFrequencyTerms.size() + 1)
                for (disi in highFrequencyTerms) {
                    val s = wrapWithDummyScorer(this, disi)
                    subs.add(DisiWrapper(s, false))
                }
                val s = wrapWithDummyScorer(this, otherTerms.build().iterator())
                subs.add(DisiWrapper(s, false))

                return WeightOrDocIdSetIterator(DisjunctionDISIApproximation(subs, leadCost))
            }
        }
    }

    companion object {
        // postings lists under this threshold will always be "pre-processed" into a bitset
        private const val POSTINGS_PRE_PROCESS_THRESHOLD = 512

        /**
         * Wraps a DISI with a "dummy" scorer so we can easily use [DisiWrapper] and [ ] as-is. This is really just a convenient vehicle to get the DISI
         * into the priority queue used by [DisjunctionDISIApproximation]. The [Scorer]
         * ultimately provided by the weight provides a constant boost and reflects the actual score mode.
         */
        private fun wrapWithDummyScorer(weight: Weight?, disi: DocIdSetIterator): Scorer {
            // The score and score mode do not actually matter here, except that using TOP_SCORES results
            // in another wrapper object getting created around the disi, so we try to avoid that:
            return ConstantScoreScorer(1f, ScoreMode.COMPLETE_NO_SCORES, disi)
        }
    }
}