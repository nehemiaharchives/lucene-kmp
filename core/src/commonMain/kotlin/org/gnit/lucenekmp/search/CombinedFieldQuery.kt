package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.TreeMap
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.similarities.BM25Similarity
import org.gnit.lucenekmp.search.similarities.DFRSimilarity
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.SmallFloat
import kotlin.math.max

/**
 * A [Query] that treats multiple fields as a single stream and scores terms as if they had
 * been indexed in a single field whose values would be the union of the values of the provided
 * fields.
 *
 * <p>The query works as follows:
 *
 * <ol>
 *   <li>Given a list of fields and weights, it pretends there is a synthetic combined field where
 *       all terms have been indexed. It computes new term and collection statistics for this
 *       combined field.
 *   <li>It uses a disjunction iterator and [IndexSearcher.similarity] to score documents.
 * </ol>
 *
 * <p>In order for a similarity to be compatible, [Similarity.computeNorm] must be additive:
 * the norm of the combined field is the sum of norms for each individual field. The norms must also
 * be encoded using [SmallFloat.intToByte4]. These requirements hold for all similarities that
 * don't customize [Similarity.computeNorm], which includes [BM25Similarity] and [DFRSimilarity].
 * Per-field similarities are not supported.
 *
 * <p>The query also requires that either all fields or no fields have norms enabled. Having only
 * some fields with norms enabled can result in errors.
 *
 * <p>This query assumes that all fields share the same analyzer. Scores may not make much sense if
 * all fields don't have the same analyzer.
 *
 * <p>The scoring is based on BM25F's simple formula described in:
 * http://www.staff.city.ac.uk/~sb317/papers/foundations_bm25_review.pdf. This query implements the
 * same approach but allows other similarities besides [BM25Similarity].
 *
 * @lucene.experimental
 */
class CombinedFieldQuery private constructor(
    // sorted map for fields.
    private val fieldAndWeights: TreeMap<String, FieldAndWeight>,
    // term bytes
    private val term: BytesRef,
) : Query(), Accountable {
    // array of terms per field, sorted by field
    private val fieldTerms: Array<Term>

    private val ramBytesUsed: Long

    /** A builder for [CombinedFieldQuery]. */
    class Builder {
        private val fieldAndWeights = HashMap<String, FieldAndWeight>()
        private val term: BytesRef

        /** Create a builder for the given term [String]. */
        constructor(term: String) {
            this.term = BytesRef(term)
        }

        /** Create a builder for the given term bytes. */
        constructor(term: BytesRef) {
            this.term = BytesRef.deepCopyOf(term)
        }

        /**
         * Adds a field to this builder.
         *
         * @param field The field name.
         */
        fun addField(field: String): Builder {
            return addField(field, 1f)
        }

        /**
         * Adds a field to this builder.
         *
         * @param field The field name.
         * @param weight The weight associated to this field.
         */
        fun addField(field: String, weight: Float): Builder {
            if (weight < 1f) {
                throw IllegalArgumentException("weight must be greater or equal to 1")
            }
            fieldAndWeights[field] = FieldAndWeight(field, weight)
            return this
        }

        /** Builds the [CombinedFieldQuery]. */
        fun build(): CombinedFieldQuery {
            if (fieldAndWeights.size > IndexSearcher.maxClauseCount) {
                throw IndexSearcher.TooManyClauses()
            }
            return CombinedFieldQuery(TreeMap(fieldAndWeights), term)
        }
    }

    data class FieldAndWeight(val field: String, val weight: Float)

    init {
        if (fieldAndWeights.size > IndexSearcher.maxClauseCount) {
            throw IndexSearcher.TooManyClauses()
        }
        fieldTerms = Array(fieldAndWeights.size) { Term("") }
        var pos = 0
        for (field in fieldAndWeights.keys) {
            fieldTerms[pos++] = Term(field, term)
        }

        ramBytesUsed =
            BASE_RAM_BYTES +
                RamUsageEstimator.sizeOfObject(fieldAndWeights) +
                RamUsageEstimator.sizeOfObject(fieldTerms) +
                RamUsageEstimator.sizeOfObject(term)
    }

    override fun toString(field: String?): String {
        val builder = StringBuilder("CombinedFieldQuery((")
        var pos = 0
        for (fieldWeight in fieldAndWeights.values) {
            if (pos++ != 0) {
                builder.append(" ")
            }
            builder.append(fieldWeight.field)
            if (fieldWeight.weight != 1f) {
                builder.append("^")
                builder.append(fieldWeight.weight)
            }
        }
        builder.append(")(")
        builder.append(Term.toString(term))
        builder.append("))")
        return builder.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (sameClassAs(other) == false) return false
        other as CombinedFieldQuery
        return fieldAndWeights == other.fieldAndWeights && term == other.term
    }

    override fun hashCode(): Int {
        var result = classHash()
        result = 31 * result + Objects.hash(fieldAndWeights)
        result = 31 * result + term.hashCode()
        return result
    }

    override fun ramBytesUsed(): Long {
        return ramBytesUsed
    }

    @Throws(Exception::class)
    override fun rewrite(indexSearcher: IndexSearcher): Query {
        if (fieldAndWeights.isEmpty()) {
            return BooleanQuery.Builder().build()
        }
        return this
    }

    override fun visit(visitor: QueryVisitor) {
        val selectedTerms = fieldTerms.filter { visitor.acceptField(it.field()) }.toTypedArray()
        if (selectedTerms.isNotEmpty()) {
            val v = visitor.getSubVisitor(BooleanClause.Occur.SHOULD, this)
            v.consumeTerms(this, *selectedTerms)
        }
    }

    private fun rewriteToBoolean(): BooleanQuery {
        // rewrite to a simple disjunction if the score is not needed.
        val bq = BooleanQuery.Builder()
        for (term in fieldTerms) {
            bq.add(TermQuery(term), BooleanClause.Occur.SHOULD)
        }
        return bq.build()
    }

    @Throws(Exception::class)
    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        validateConsistentNorms(searcher.indexReader)
        return if (scoreMode.needsScores()) {
            CombinedFieldWeight(this, searcher, scoreMode, boost)
        } else {
            // rewrite to a simple disjunction if the score is not needed.
            val bq: Query = rewriteToBoolean()
            searcher.rewrite(bq).createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, boost)
        }
    }

    private fun validateConsistentNorms(reader: IndexReader) {
        var allFieldsHaveNorms = true
        var noFieldsHaveNorms = true

        for (context in reader.leaves()) {
            val fieldInfos: FieldInfos = context.reader().fieldInfos
            for (field in fieldAndWeights.keys) {
                val fieldInfo = fieldInfos.fieldInfo(field)
                if (fieldInfo != null) {
                    allFieldsHaveNorms = allFieldsHaveNorms and fieldInfo.hasNorms()
                    noFieldsHaveNorms = noFieldsHaveNorms and fieldInfo.omitsNorms()
                }
            }
        }

        if (allFieldsHaveNorms == false && noFieldsHaveNorms == false) {
            throw IllegalArgumentException(
                "${this::class.simpleName} requires norms to be consistent across fields: some fields cannot have norms enabled, while others have norms disabled"
            )
        }
    }

    inner class CombinedFieldWeight(query: Query, private val searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float) : Weight(query) {
        private val termStates: Array<TermStates>
        private val simWeight: Similarity.SimScorer?

        init {
            assert(scoreMode.needsScores())
            var docFreq = 0L
            var totalTermFreq = 0L
            termStates = Array(fieldTerms.size) { i ->
                val field = fieldAndWeights[fieldTerms[i].field()]!!
                val ts = TermStates.build(searcher, fieldTerms[i], true)
                if (ts.docFreq() > 0) {
                    val termStats = searcher.termStatistics(fieldTerms[i], ts.docFreq(), ts.totalTermFreq())
                    docFreq = max(termStats.docFreq, docFreq)
                    totalTermFreq += (field.weight * termStats.totalTermFreq.toDouble()).toLong()
                }
                ts
            }
            simWeight = if (docFreq > 0L) {
                val pseudoCollectionStats = mergeCollectionStatistics(searcher)
                val pseudoTermStatistics =
                    TermStatistics(BytesRef("pseudo_term"), docFreq, max(1L, totalTermFreq))
                searcher.similarity.scorer(boost, pseudoCollectionStats, pseudoTermStatistics)
            } else {
                null
            }
        }

        @Throws(IOException::class)
        private fun mergeCollectionStatistics(searcher: IndexSearcher): CollectionStatistics {
            var maxDoc = 0L
            var docCount = 0L
            var sumTotalTermFreq = 0L
            var sumDocFreq = 0L
            for (fieldWeight in fieldAndWeights.values) {
                val collectionStats = searcher.collectionStatistics(fieldWeight.field)
                if (collectionStats != null) {
                    maxDoc = max(collectionStats.maxDoc, maxDoc)
                    docCount = max(collectionStats.docCount, docCount)
                    sumDocFreq = max(collectionStats.sumDocFreq, sumDocFreq)
                    sumTotalTermFreq +=
                        (fieldWeight.weight * collectionStats.sumTotalTermFreq.toDouble()).toLong()
                }
            }

            return CollectionStatistics(
                "pseudo_field",
                maxDoc,
                docCount,
                sumTotalTermFreq,
                sumDocFreq,
            )
        }

        @Throws(IOException::class)
        override fun matches(context: LeafReaderContext, doc: Int): Matches? {
            val weight = searcher.rewrite(rewriteToBoolean()).createWeight(searcher, ScoreMode.COMPLETE, 1f)
            return weight.matches(context, doc)
        }

        @Throws(IOException::class)
        override fun explain(context: LeafReaderContext, doc: Int): Explanation {
            val scorer = scorer(context)
            if (scorer != null) {
                val newDoc = scorer.iterator().advance(doc)
                if (newDoc == doc) {
                    assert(scorer is CombinedFieldScorer)
                    val freq = (scorer as CombinedFieldScorer).freq()
                    val docScorer =
                        MultiNormsLeafSimScorer(simWeight!!, context.reader(), fieldAndWeights.values, true)
                    val freqExplanation = Explanation.match(freq, "termFreq=$freq")
                    val scoreExplanation = docScorer.explain(doc, freqExplanation)
                    return Explanation.match(
                        scoreExplanation.value,
                        "weight($query in $doc), result of:",
                        scoreExplanation,
                    )
                }
            }
            return Explanation.noMatch("no matching term")
        }

        @Throws(IOException::class)
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
            val iterators = ArrayList<PostingsEnum>()
            val fields = ArrayList<FieldAndWeight>()
            var cost = 0L
            for (i in fieldTerms.indices) {
                val supplier: IOSupplier<TermState?>? = termStates[i].get(context)
                val state = supplier?.get()
                if (state != null) {
                    val termsEnum: TermsEnum = requireNotNull(context.reader().terms(fieldTerms[i].field())).iterator()
                    termsEnum.seekExact(fieldTerms[i].bytes(), state)
                    val postingsEnum = termsEnum.postings(null, PostingsEnum.FREQS.toInt())!!
                    iterators.add(postingsEnum)
                    fields.add(fieldAndWeights[fieldTerms[i].field()]!!)
                    cost += postingsEnum.cost()
                }
            }

            if (iterators.isEmpty()) {
                return null
            }

            val scoringSimScorer =
                MultiNormsLeafSimScorer(simWeight!!, context.reader(), fieldAndWeights.values, true)

            val finalCost = cost
            return object : ScorerSupplier() {
                @Throws(IOException::class)
                override fun get(leadCost: Long): Scorer {
                    // we use termscorers + disjunction as an impl detail
                    val wrappers = ArrayList<DisiWrapper>(iterators.size)
                    for (i in iterators.indices) {
                        val weight = fields[i].weight
                        wrappers.add(WeightedDisiWrapper(TermScorer(iterators[i], simWeight, null), weight))
                    }
                    // Even though it is called approximation, it is accurate since none of
                    // the sub iterators are two-phase iterators.
                    val iterator = DisjunctionDISIApproximation(wrappers, leadCost)
                    return CombinedFieldScorer(iterator, scoringSimScorer)
                }

                override fun cost(): Long {
                    return finalCost
                }
            }
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return false
        }
    }

    private class WeightedDisiWrapper(scorer: Scorer, val weight: Float) : DisiWrapper(scorer, false) {
        val postingsEnum = scorer.iterator() as PostingsEnum

        @Throws(IOException::class)
        fun freq(): Float {
            return weight * postingsEnum.freq()
        }
    }

    private class CombinedFieldScorer(
        private val iterator: DisjunctionDISIApproximation,
        private val simScorer: MultiNormsLeafSimScorer,
    ) : Scorer() {
        private val maxScore: Float = simScorer.getSimScorer().score(Float.POSITIVE_INFINITY, 1L)

        override fun docID(): Int {
            return iterator.docID()
        }

        @Throws(IOException::class)
        fun freq(): Float {
            var w = iterator.topList()
            var freq = (w as WeightedDisiWrapper).freq()
            w = w.next
            while (w != null) {
                freq += (w as WeightedDisiWrapper).freq()
                if (freq < 0) { // overflow
                    return Int.MAX_VALUE.toFloat()
                }
                w = w.next
            }
            return freq
        }

        @Throws(IOException::class)
        override fun score(): Float {
            return simScorer.score(iterator.docID(), freq())
        }

        override fun iterator(): DocIdSetIterator {
            return iterator
        }

        @Throws(IOException::class)
        override fun getMaxScore(upTo: Int): Float {
            return maxScore
        }
    }

    companion object {
        private val BASE_RAM_BYTES = RamUsageEstimator.shallowSizeOfInstance(CombinedFieldQuery::class)
    }
}
