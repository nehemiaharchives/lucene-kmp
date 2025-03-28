package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.*
import org.gnit.lucenekmp.util.DocIdSetBuilder


/**
 * This class provides the functionality behind [MultiTermQuery.CONSTANT_SCORE_REWRITE]. It
 * tries to rewrite per-segment as a boolean query that returns a constant score and otherwise fills
 * a bit set with matches and builds a Scorer on top of this bit set.
 */
internal class MultiTermQueryConstantScoreWrapper<Q : MultiTermQuery>
    (query: Q) : AbstractMultiTermQueryConstantScoreWrapper<Q>(query) {
    @Throws(IOException::class)
    public override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {

        val multiTermQueryConstantScoreWrapperQuery = query

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
                val builder = DocIdSetBuilder(context.reader().maxDoc(), terms)
                var docs: PostingsEnum? = null

                // Handle the already-collected terms:
                if (collectedTerms.isEmpty() == false) {
                    val termsEnum2 = terms.iterator()
                    for (t in collectedTerms) {
                        termsEnum2.seekExact(t.term, t.state)
                        docs = termsEnum2.postings(docs, PostingsEnum.NONE.toInt())
                        builder.add(docs)
                    }
                }

                // Then keep filling the bit set with remaining terms:
                do {
                    docs = termsEnum.postings(docs, PostingsEnum.NONE.toInt())
                    // If a term contains all docs with a value for the specified field, we can discard the
                    // other terms and just use the dense term's postings:
                    val docFreq = termsEnum.docFreq()
                    if (fieldDocCount == docFreq) {
                        val termStates = TermStates(searcher.topReaderContext)
                        termStates.register(
                            termsEnum.termState(), context.ord, docFreq, termsEnum.totalTermFreq()
                        )
                        val q: Query =
                            ConstantScoreQuery(
                                TermQuery(Term(multiTermQueryConstantScoreWrapperQuery.field, termsEnum.term()), termStates)
                            )
                        val weight: Weight? = searcher.rewrite(q).createWeight(searcher, scoreMode, score())
                        return WeightOrDocIdSetIterator(weight!!)
                    }
                    builder.add(docs)
                } while (termsEnum.next() != null)

                return WeightOrDocIdSetIterator(builder.build().iterator())
            }
        }
    }
}
