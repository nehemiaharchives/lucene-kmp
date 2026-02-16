package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.document.FeatureField.FeatureFunction
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TermScorer
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import org.gnit.lucenekmp.util.BytesRef

internal class FeatureQuery(fieldName: String, featureName: String, function: FeatureFunction) : Query() {
    private val fieldName: String = requireNotNull(fieldName)
    private val featureName: String = requireNotNull(featureName)
    private val function: FeatureFunction = requireNotNull(function)

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val rewritten: FeatureFunction = function.rewrite(indexSearcher)!!
        if (function !== rewritten) {
            return FeatureQuery(fieldName, featureName, rewritten)
        }
        return super.rewrite(indexSearcher)
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null || this::class != obj::class) {
            return false
        }
        val that = obj as FeatureQuery
        return fieldName == that.fieldName
                && featureName == that.featureName
                && function == that.function
    }

    override fun hashCode(): Int {
        var h = this::class.hashCode()
        h = 31 * h + fieldName.hashCode()
        h = 31 * h + featureName.hashCode()
        h = 31 * h + function.hashCode()
        return h
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        if (!scoreMode.needsScores()) {
            // We don't need scores (e.g. for faceting), and since features are stored as terms,
            // allow TermQuery to optimize in this case
            val tq = TermQuery(Term(fieldName, featureName))
            return searcher.rewrite(tq).createWeight(searcher, scoreMode, boost)
        }

        return object : Weight(this) {
            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return false
            }

            @Throws(IOException::class)
            override fun explain(context: LeafReaderContext, doc: Int): Explanation {
                val desc = "weight($query in $doc) [$function]"

                val terms: Terms? = context.reader().terms(fieldName)
                if (terms == null) {
                    return Explanation.noMatch("$desc. Field $fieldName doesn't exist.")
                }
                val termsEnum: TermsEnum = terms.iterator()
                if (termsEnum.seekExact(BytesRef(featureName)) == false) {
                    return Explanation.noMatch("$desc. Feature $featureName doesn't exist.")
                }

                val postings: PostingsEnum = termsEnum.postings(null, PostingsEnum.FREQS.toInt())!!
                if (postings.advance(doc) != doc) {
                    return Explanation.noMatch("$desc. Feature $featureName isn't set.")
                }

                return function.explain(fieldName, featureName, boost, postings.freq())
            }

            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val terms: Terms = Terms.getTerms(context.reader(), fieldName)
                val termsEnum: TermsEnum = terms.iterator()
                if (termsEnum.seekExact(BytesRef(featureName)) == false) {
                    return null
                }
                val docFreq: Int = termsEnum.docFreq()

                return object : ScorerSupplier() {
                    private var topLevelScoringClause = false

                    @Throws(IOException::class)
                    override fun get(leadCost: Long): Scorer {
                        val scorer: SimScorer = function.scorer(boost)
                        val impacts: ImpactsEnum = termsEnum.impacts(PostingsEnum.FREQS.toInt())
                        return TermScorer(impacts, scorer, null, topLevelScoringClause)
                    }

                    override fun cost(): Long {
                        return docFreq.toLong()
                    }

                    override fun setTopLevelScoringClause() {
                        topLevelScoringClause = true
                    }
                }
            }
        }
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(fieldName)) {
            visitor.visitLeaf(this)
        }
    }

    override fun toString(field: String?): String {
        return "FeatureQuery(field=$fieldName, feature=$featureName, function=$function)"
    }
}
