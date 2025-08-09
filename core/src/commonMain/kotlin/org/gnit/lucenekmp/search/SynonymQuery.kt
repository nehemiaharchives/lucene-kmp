package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.Impacts
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.ImpactsSource
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SlowImpactsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.compareUnsigned
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.PriorityQueue
import kotlin.jvm.JvmOverloads
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A query that treats multiple terms as synonyms.
 *
 *
 * For scoring purposes, this query tries to score the terms as if you had indexed them as one
 * term: it will match any of the terms but only invoke the similarity a single time, scoring the
 * sum of all term frequencies for the document.
 */
class SynonymQuery private constructor(
    private val terms: Array<TermAndBoost>,
    /** Returns the field name of this [SynonymQuery]  */
    val field: String
) : Query() {

    /** A builder for [SynonymQuery].  */
    class Builder
    /**
     * Sole constructor
     *
     * @param field The target field name
     */(private val field: String) {
        private val terms: MutableList<TermAndBoost> = mutableListOf()

        /**
         * Adds the provided `term` as a synonym, document frequencies of this term will be
         * boosted by `boost`.
         */
        /** Adds the provided `term` as a synonym.  */
        @JvmOverloads
        fun addTerm(term: Term, boost: Float = 1f): Builder {
            require(field == term.field()) { "Synonyms must be across the same field" }
            return addTerm(term.bytes(), boost)
        }

        /**
         * Adds the provided `term` as a synonym, document frequencies of this term will be
         * boosted by `boost`.
         */
        fun addTerm(term: BytesRef, boost: Float): Builder {
            require(
                !(Float.isNaN(boost) || Float.compare(
                    boost,
                    0f
                ) <= 0 || Float.compare(boost, 1f) > 0)
            ) { "boost must be a positive float between 0 (exclusive) and 1 (inclusive)" }
            terms.add(TermAndBoost(term, boost))
            if (terms.size > IndexSearcher.maxClauseCount) {
                throw IndexSearcher.TooManyClauses()
            }
            return this
        }

        /** Builds the [SynonymQuery].  */
        fun build(): SynonymQuery {
            terms.sortBy { it.term }
            return SynonymQuery(terms.toTypedArray<TermAndBoost>(), field)
        }
    }

    /** Returns the terms of this [SynonymQuery]  */
    fun getTerms(): MutableList<Term> {
        return terms.map { t: TermAndBoost -> Term(field, t.term) }.toMutableList()
    }

    override fun toString(field: String?): String {
        val builder = StringBuilder("Synonym(")
        for (i in terms.indices) {
            if (i != 0) {
                builder.append(" ")
            }
            val termQuery: Query =
                TermQuery(Term(this.field, terms[i].term))
            builder.append(termQuery.toString(field))
            if (terms[i].boost != 1f) {
                builder.append("^")
                builder.append(terms[i].boost)
            }
        }
        builder.append(")")
        return builder.toString()
    }

    override fun hashCode(): Int {
        return 31 * classHash() + terms.contentHashCode() + field.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other)
                && field == (other as SynonymQuery).field
                && terms.contentEquals(other.terms)
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        // optimize zero and non-boosted single term cases
        if (terms.isEmpty()) {
            return BooleanQuery.Builder().build()
        }
        if (terms.size == 1 && terms[0].boost == 1f) {
            return TermQuery(Term(field, terms[0].term))
        }
        return this
    }

    override fun visit(visitor: QueryVisitor) {
        if (!visitor.acceptField(field)) {
            return
        }
        val v: QueryVisitor = visitor.getSubVisitor(BooleanClause.Occur.SHOULD, this)
        val ts: Array<Term> = terms.map { t -> Term(field, t.term) }.toTypedArray()
        v.consumeTerms(this, *ts)
    }

    override fun createWeight(
        searcher: IndexSearcher,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        if (scoreMode.needsScores()) {
            return SynonymWeight(this, searcher, scoreMode, boost)
        } else {
            // if scores are not needed, let BooleanWeight deal with optimizing that case.
            val bq: BooleanQuery.Builder = BooleanQuery.Builder()
            for (term in terms) {
                bq.add(
                    TermQuery(Term(field, term.term)),
                    BooleanClause.Occur.SHOULD
                )
            }
            return searcher
                .rewrite(bq.build())
                .createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, boost)
        }
    }

    internal inner class SynonymWeight(
        query: Query,
        searcher: IndexSearcher,
        scoreMode: ScoreMode,
        boost: Float
    ) : Weight(query) {
        private val termStates: Array<TermStates>
        private val similarity: Similarity
        private val simWeight: SimScorer?
        private val scoreMode: ScoreMode

        init {
            assert(scoreMode.needsScores())
            this.scoreMode = scoreMode
            val collectionStats: CollectionStatistics? = searcher.collectionStatistics(field)
            var docFreq: Long = 0
            var totalTermFreq: Long = 0
            termStates = kotlin.arrayOfNulls<TermStates>(terms.size) as Array<TermStates>
            for (i in termStates.indices) {
                val term = Term(field, terms[i].term)
                val ts: TermStates =
                    TermStates.build(searcher, term, true)
                termStates[i] = ts
                if (ts.docFreq() > 0) {
                    val termStats: TermStatistics =
                        searcher.termStatistics(term, ts.docFreq(), ts.totalTermFreq())
                    docFreq = max(termStats.docFreq, docFreq)
                    totalTermFreq += termStats.totalTermFreq
                }
            }
            this.similarity = searcher.similarity
            if (docFreq > 0) {
                val pseudoStats =
                    TermStatistics(
                        BytesRef("synonym pseudo-term"),
                        docFreq,
                        totalTermFreq
                    )
                this.simWeight = similarity.scorer(boost, collectionStats!!, pseudoStats)
            } else {
                this.simWeight = null // no terms exist at all, we won't use similarity
            }
        }

        @Throws(IOException::class)
        override fun matches(
            context: LeafReaderContext,
            doc: Int
        ): Matches {
            val indexTerms: Terms? = context.reader().terms(field)
            if (indexTerms == null) {
                return super.matches(context, doc)!!
            }
            val termList: MutableList<Term> = terms.map { t -> Term(field, t.term) }.toMutableList()
            return MatchesUtils.forField(
                field
            ) {
                DisjunctionMatchesIterator.fromTerms(
                    context,
                    doc,
                    query,
                    field,
                    termList
                )!!
            }!!
        }

        @Throws(IOException::class)
        override fun explain(
            context: LeafReaderContext,
            doc: Int
        ): Explanation {
            val scorer: Scorer? = scorer(context)
            if (scorer != null) {
                val newDoc: Int = scorer.iterator().advance(doc)
                if (newDoc == doc) {
                    val freq: Float
                    when (scorer) {
                        is SynonymScorer -> {
                            freq = scorer.freq()
                        }

                        is FreqBoostTermScorer -> {
                            freq = scorer.freq()
                        }

                        else -> {
                            assert(scorer is TermScorer)
                            freq = (scorer as TermScorer).freq().toFloat()
                        }
                    }
                    val freqExplanation: Explanation =
                        Explanation.match(freq, "termFreq=$freq")
                    val norms: NumericDocValues? = context.reader().getNormValues(field)
                    var norm = 1L
                    if (norms != null && norms.advanceExact(doc)) {
                        norm = norms.longValue()
                    }
                    val scoreExplanation: Explanation =
                        simWeight!!.explain(freqExplanation, norm)
                    return Explanation.match(
                        scoreExplanation.value,
                        ("weight("
                                + query
                                + " in "
                                + doc
                                + ") ["
                                + similarity::class.simpleName
                                + "], result of:"),
                        scoreExplanation
                    )
                }
            }
            return Explanation.noMatch("no matching term")
        }

        @Throws(IOException::class)
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier {

            val termStateSuppliers: Array<IOSupplier<TermState>> = Array(terms.size) {
                termStates[it].get(context) as IOSupplier<TermState>
            }

            return object : ScorerSupplier() {
                lateinit var iterators: MutableList<PostingsEnum>
                lateinit var impacts: MutableList<ImpactsEnum>
                lateinit var termBoosts: MutableList<Float>
                var cost: Long = 0

                @Throws(IOException::class)
                fun init() {
                    if (iterators != null) {
                        return
                    }
                    iterators = ArrayList()
                    impacts = ArrayList()
                    termBoosts = ArrayList()
                    cost = 0L

                    for (i in terms.indices) {
                        val supplier: IOSupplier<TermState> = termStateSuppliers[i]
                        val state: TermState? = if (supplier == null) null else supplier.get()
                        if (state != null) {
                            val termsEnum: TermsEnum = context.reader().terms(field)!!.iterator()
                            termsEnum.seekExact(terms[i].term, state)
                            if (scoreMode == ScoreMode.TOP_SCORES) {
                                val impactsEnum: ImpactsEnum =
                                    termsEnum.impacts(PostingsEnum.FREQS.toInt())
                                iterators.add(impactsEnum)
                                impacts.add(impactsEnum)
                            } else {
                                val postingsEnum: PostingsEnum =
                                    termsEnum.postings(null, PostingsEnum.FREQS.toInt())
                                iterators.add(postingsEnum)
                                impacts.add(SlowImpactsEnum(postingsEnum))
                            }
                            termBoosts.add(terms[i].boost)
                        }
                    }

                    for (iterator in iterators) {
                        cost += iterator.cost()
                    }
                }

                @Throws(IOException::class)
                override fun get(leadCost: Long): Scorer {
                    init()

                    if (iterators.isEmpty()) {
                        return ConstantScoreScorer(
                            0f,
                            scoreMode,
                            DocIdSetIterator.empty()
                        )
                    }

                    val norms: NumericDocValues = context.reader().getNormValues(field)!!

                    // we must optimize this case (term not in segment), disjunctions require >= 2 subs
                    if (iterators.size == 1) {
                        val scorer: TermScorer = if (scoreMode == ScoreMode.TOP_SCORES) {
                            TermScorer(impacts[0], simWeight!!, norms)
                        } else {
                            TermScorer(iterators[0], simWeight!!, norms)
                        }
                        val boost: Float = termBoosts[0]
                        return if (scoreMode == ScoreMode.COMPLETE_NO_SCORES || boost == 1f)
                            scorer
                        else
                            FreqBoostTermScorer(boost, scorer, simWeight, norms)
                    } else {
                        // we use termscorers + disjunction as an impl detail

                        val wrappers: MutableList<DisiWrapper> = mutableListOf()
                        for (i in iterators.indices) {
                            val postings: PostingsEnum = iterators[i]
                            val termScorer = TermScorer(postings, simWeight!!, norms)
                            val boost: Float = termBoosts[i]
                            val wrapper = DisiWrapperFreq(termScorer, boost)
                            wrappers.add(wrapper)
                        }
                        // Even though it is called approximation, it is accurate since none of
                        // the sub iterators are two-phase iterators.
                        val disjunctionIterator = DisjunctionDISIApproximation(wrappers, leadCost)
                        var iterator: DocIdSetIterator = disjunctionIterator

                        val boosts = FloatArray(impacts.size)
                        for (i in boosts.indices) {
                            boosts[i] = termBoosts[i]
                        }
                        val impactsSource: ImpactsSource =
                            mergeImpacts(impacts.toTypedArray<ImpactsEnum>(), boosts)
                        val maxScoreCache = MaxScoreCache(impactsSource, simWeight!!)
                        val impactsDisi = ImpactsDISI(iterator, maxScoreCache)

                        if (scoreMode == ScoreMode.TOP_SCORES) {
                            // TODO: only do this when this is the top-level scoring clause
                            // (ScorerSupplier#setTopLevelScoringClause) to save the overhead of wrapping with
                            // ImpactsDISI when it would not help
                            iterator = impactsDisi
                        }

                        return SynonymScorer(iterator, disjunctionIterator, impactsDisi, simWeight, norms)
                    }
                }

                override fun cost(): Long {
                    try {
                        init()
                    } catch (e: IOException) {
                        throw UncheckedIOException(e)
                    }
                    return cost
                }
            }
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return true
        }
    }

    private class SynonymScorer(
        private val iterator: DocIdSetIterator,
        private val disjunctionDisi: DisjunctionDISIApproximation,
        private val impactsDisi: ImpactsDISI,
        private val scorer: SimScorer,
        private val norms: NumericDocValues
    ) : Scorer() {
        private val maxScoreCache: MaxScoreCache = impactsDisi.getMaxScoreCache()

        override fun docID(): Int {
            return iterator.docID()
        }

        @Throws(IOException::class)
        fun freq(): Float {
            var w = disjunctionDisi.topList() as DisiWrapperFreq
            var freq = w.freq()
            w = w.next as DisiWrapperFreq
            while (w != null) {
                freq += w.freq()
                w = w.next as DisiWrapperFreq
            }
            return freq
        }

        @Throws(IOException::class)
        override fun score(): Float {
            var norm = 1L
            if (norms != null && norms.advanceExact(iterator.docID())) {
                norm = norms.longValue()
            }
            return scorer.score(freq(), norm)
        }

        override fun iterator(): DocIdSetIterator {
            return iterator
        }

        @Throws(IOException::class)
        override fun getMaxScore(upTo: Int): Float {
            return maxScoreCache.getMaxScore(upTo)
        }

        @Throws(IOException::class)
        override fun advanceShallow(target: Int): Int {
            return maxScoreCache.advanceShallow(target)
        }

        override var minCompetitiveScore: Float
            get() {
                throw UnsupportedOperationException(
                    "minCompetitiveScore is not supported for SynonymScorer"
                )
            }
            set(minScore) {
                impactsDisi.setMinCompetitiveScore(minScore)
            }
    }

    private class DisiWrapperFreq(scorer: Scorer, val boost: Float) :
        DisiWrapper(scorer, false) {
        val pe: PostingsEnum = scorer.iterator() as PostingsEnum

        @Throws(IOException::class)
        fun freq(): Float {
            return boost * pe.freq()
        }
    }

    private class FreqBoostTermScorer(
        boost: Float,
        `in`: TermScorer,
        scorer: SimScorer,
        norms: NumericDocValues
    ) : FilterScorer(`in`) {
        val boost: Float
        override val `in`: TermScorer
        val scorer: SimScorer
        val norms: NumericDocValues

        init {
            require(
                !(Float.isNaN(boost) || Float.compare(boost, 0f) < 0 || Float.compare(
                    boost,
                    1f
                ) > 0)
            ) { "boost must be a positive float between 0 (exclusive) and 1 (inclusive)" }
            this.boost = boost
            this.`in` = `in`
            this.scorer = scorer
            this.norms = norms
        }

        @Throws(IOException::class)
        fun freq(): Float {
            return boost * `in`.freq()
        }

        @Throws(IOException::class)
        override fun score(): Float {
            var norm = 1L
            if (norms != null && norms.advanceExact(`in`.docID())) {
                norm = norms.longValue()
            }
            return scorer.score(freq(), norm)
        }

        @Throws(IOException::class)
        override fun getMaxScore(upTo: Int): Float {
            return `in`.getMaxScore(upTo)
        }

        @Throws(IOException::class)
        override fun advanceShallow(target: Int): Int {
            return `in`.advanceShallow(target)
        }

        override var minCompetitiveScore: Float
            get() {
                throw UnsupportedOperationException(
                    "minCompetitiveScore is not supported for FreqBoostTermScorer"
                )
            }
            set(minScore) {
                `in`.setMinCompetitiveScore(minScore)
            }
    }

    private class TermAndBoost(val term: BytesRef, val boost: Float)

    companion object {
        /** Merge impacts for multiple synonyms.  */
        fun mergeImpacts(
            impactsEnums: Array<ImpactsEnum>,
            boosts: FloatArray
        ): ImpactsSource {
            assert(impactsEnums.size == boosts.size)
            return object : ImpactsSource {
                inner class SubIterator(val iterator: MutableIterator<Impact>) {
                    var previousFreq: Int = 0
                    var current: Impact?

                    init {
                        this.current = iterator.next()
                    }

                    fun next() {
                        previousFreq = current!!.freq
                        current = if (!iterator.hasNext()) {
                            null
                        } else {
                            iterator.next()
                        }
                    }
                }

                override val impacts: Impacts
                    get() {
                        val impacts: Array<Impacts> =
                            kotlin.arrayOfNulls<Impacts>(impactsEnums.size) as Array<Impacts>
                        // Use the impacts that have the lower next boundary as a lead.
                        // It will decide on the number of levels and the block boundaries.
                        var tmpLead: Impacts? = null
                        for (i in impactsEnums.indices) {
                            impacts[i] = impactsEnums[i].impacts
                            if (tmpLead == null || impacts[i].getDocIdUpTo(0) < tmpLead.getDocIdUpTo(0)) {
                                tmpLead = impacts[i]
                            }
                        }
                        val lead: Impacts = tmpLead!!
                        return object : Impacts() {
                            override fun numLevels(): Int {
                                // Delegate to the lead
                                return lead.numLevels()
                            }

                            override fun getDocIdUpTo(level: Int): Int {
                                // Delegate to the lead
                                return lead.getDocIdUpTo(level)
                            }

                            /**
                             * Return the minimum level whose impacts are valid up to `docIdUpTo`, or `-1`
                             * if there is no such level.
                             */
                            fun getLevel(impacts: Impacts, docIdUpTo: Int): Int {
                                var level = 0
                                val numLevels: Int = impacts.numLevels()
                                while (level < numLevels) {
                                    if (impacts.getDocIdUpTo(level) >= docIdUpTo) {
                                        return level
                                    }
                                    ++level
                                }
                                return -1
                            }

                            override fun getImpacts(level: Int): MutableList<Impact> {
                                val docIdUpTo = getDocIdUpTo(level)

                                val toMerge: MutableList<MutableList<Impact>> = mutableListOf()

                                for (i in impactsEnums.indices) {
                                    if (impactsEnums[i].docID() <= docIdUpTo) {
                                        val impactsLevel = getLevel(impacts[i], docIdUpTo)
                                        if (impactsLevel == -1) {
                                            // One instance doesn't have impacts that cover up to docIdUpTo
                                            // Return impacts that trigger the maximum score
                                            return mutableListOf(
                                                Impact(
                                                    Int.MAX_VALUE, 1L
                                                )
                                            )
                                        }
                                        val impactList: MutableList<Impact>
                                        if (boosts[i] != 1f) {
                                            val boost = boosts[i]
                                            impactList =
                                                impacts[i].getImpacts(impactsLevel)
                                                    .map { impact: Impact ->
                                                        Impact(
                                                            ceil((impact.freq * boost).toDouble()).toInt(), impact.norm
                                                        )
                                                    }
                                                    .toMutableList()
                                        } else {
                                            impactList = impacts[i].getImpacts(impactsLevel)
                                        }
                                        toMerge.add(impactList)
                                    }
                                }
                                assert(toMerge.isNotEmpty()) // otherwise it would mean the docID is > docIdUpTo, which is wrong

                                if (toMerge.size == 1) {
                                    // common if one synonym is common and the other one is rare
                                    return toMerge[0]
                                }

                                val pq: PriorityQueue<SubIterator> =
                                    object : PriorityQueue<SubIterator>(impacts.size) {
                                        override fun lessThan(a: SubIterator, b: SubIterator): Boolean {
                                            if (a.current == null) { // means iteration is finished
                                                return false
                                            }
                                            if (b.current == null) {
                                                return true
                                            }
                                            return Long.compareUnsigned(a.current!!.norm, b.current!!.norm) < 0
                                        }
                                    }
                                for (impacts in toMerge) {
                                    pq.add(SubIterator(impacts.iterator()))
                                }

                                val mergedImpacts: MutableList<Impact> = mutableListOf()

                                // Idea: merge impacts by norm. The tricky thing is that we need to
                                // consider norm values that are not in the impacts too. For
                                // instance if the list of impacts is [{freq=2,norm=10}, {freq=4,norm=12}],
                                // there might well be a document that has a freq of 2 and a length of 11,
                                // which was just not added to the list of impacts because {freq=2,norm=10}
                                // is more competitive. So the way it works is that we track the sum of
                                // the term freqs that we have seen so far in order to account for these
                                // implicit impacts.
                                var sumTf: Long = 0
                                var top: SubIterator = pq.top()
                                do {
                                    val norm: Long = top.current!!.norm
                                    do {
                                        sumTf += (top.current!!.freq - top.previousFreq).toLong()
                                        top.next()
                                        top = pq.updateTop()
                                    } while (top.current != null && top.current!!.norm == norm)

                                    val freqUpperBound = min(Int.MAX_VALUE.toLong(), sumTf).toInt()
                                    if (mergedImpacts.isEmpty()) {
                                        mergedImpacts.add(Impact(freqUpperBound, norm))
                                    } else {
                                        val prevImpact: Impact =
                                            mergedImpacts[mergedImpacts.size - 1]
                                        assert(Long.compareUnsigned(prevImpact.norm, norm) < 0)
                                        if (freqUpperBound > prevImpact.freq) {
                                            mergedImpacts.add(Impact(freqUpperBound, norm))
                                        } // otherwise the previous impact is already more competitive
                                    }
                                } while (top.current != null)

                                return mergedImpacts
                            }
                        }
                    }

                @Throws(IOException::class)
                override fun advanceShallow(target: Int) {
                    for (impactsEnum in impactsEnums) {
                        if (impactsEnum.docID() < target) {
                            impactsEnum.advanceShallow(target)
                        }
                    }
                }
            }
        }
    }
}
