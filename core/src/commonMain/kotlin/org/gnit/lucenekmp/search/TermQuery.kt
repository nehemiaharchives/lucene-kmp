package org.gnit.lucenekmp.search


import okio.IOException
import org.gnit.lucenekmp.index.IndexReaderContext
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.ReaderUtil
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import org.gnit.lucenekmp.util.IOSupplier


/**
 * A Query that matches documents containing a term. This may be combined with other terms with a
 * [BooleanQuery].
 */
class TermQuery : Query {

    private val term: Term
    private val perReaderTermState: TermStates?
    private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}

    internal inner class TermWeight (searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float, termStates: TermStates?) : Weight(this@TermQuery ) {
        private val similarity: Similarity
        private var simScorer: SimScorer? = null
        private val termStates: TermStates?
        private val scoreMode: ScoreMode

        init {
            check(!(scoreMode.needsScores() && termStates == null)) {"termStates are required when scores are needed"}
            this.scoreMode = scoreMode
            this.termStates = termStates
            this.similarity = searcher.similarity

            val collectionStats: CollectionStatistics
            val termStats: TermStatistics?
            if (scoreMode.needsScores()) {
                collectionStats = searcher.collectionStatistics(term.field())!!
                termStats =
                    if (termStates!!.docFreq() > 0)
                        searcher.termStatistics(term, termStates.docFreq(), termStates.totalTermFreq())
                    else
                        null
            } else {
                // we do not need the actual stats, use fake stats with docFreq=maxDoc=ttf=1
                collectionStats = CollectionStatistics(term.field(), 1, 1, 1, 1)
                termStats = TermStatistics(term.bytes(), 1, 1)
            }

            if (termStats == null) {
                this.simScorer = null // term doesn't exist in any segment, we won't use similarity at all
            } else {
                // Assigning a dummy simScorer in case score is not needed to avoid unnecessary float[]
                // allocations in case default BM25Scorer is used.
                // See: https://github.com/apache/lucene/issues/12297
                if (scoreMode.needsScores()) {
                    this.simScorer = similarity.scorer(boost, collectionStats, termStats)
                } else {
                    // Assigning a dummy scorer as this is not expected to be called since scores are not
                    // needed.
                    this.simScorer =
                        object : SimScorer() {
                            override fun score(freq: Float, norm: Long): Float {
                                return 0f
                            }
                        }
                }
            }
        }

        @Throws(IOException::class) override fun matches(context: LeafReaderContext, doc: Int): Matches? {
            val te: TermsEnum? = getTermsEnum(context)
            if (te == null) {
                return null
            }

            val pe: PostingsEnum = te.postings(null, PostingsEnum.OFFSETS.toInt())
            if (pe.advance(doc) != doc) {
                return null
            }

            return MatchesUtils.forField(
                term.field()
            ) { TermMatchesIterator(query, pe) }
        }

        override fun toString(): String {
            return "weight(" + this@TermQuery + ")"
        }

        @Throws(IOException::class) override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
            logger.debug { "[TermQuery.scorerSupplier] enter term=${term.field()}:${term.text()} ord=${context.ord}" }
            require(termStates == null || termStates.wasBuiltFor(ReaderUtil.getTopLevelContext(context))
            ) {("The top-reader used to create Weight is not the same as the current reader's top-reader ("
                    + ReaderUtil.getTopLevelContext(context))}

            val stateSupplier: IOSupplier<TermState?>? = termStates?.get(context)
            if (stateSupplier == null) {
                logger.debug { "[TermQuery.scorerSupplier] no term state term=${term.field()}:${term.text()} ord=${context.ord}" }
                return null
            }

            return object : ScorerSupplier() {

                private var termsEnum: TermsEnum? = null
                private var topLevelScoringClause: Boolean = false

                @Throws(IOException::class) fun getTermsEnum(): TermsEnum? {
                    if (termsEnum == null) {
                        logger.debug { "[TermQuery.getTermsEnum] load state term=${term.field()}:${term.text()} ord=${context.ord}" }
                        val state: TermState? = stateSupplier.get()
                        if (state == null) {
                            logger.debug { "[TermQuery.getTermsEnum] no state term=${term.field()}:${term.text()} ord=${context.ord}" }
                            return null
                        }
                        logger.debug { "[TermQuery.getTermsEnum] terms() start term=${term.field()}:${term.text()} ord=${context.ord}" }
                        termsEnum = context.reader().terms(term.field())!!.iterator()
                        logger.debug { "[TermQuery.getTermsEnum] seekExact start term=${term.field()}:${term.text()} ord=${context.ord}" }
                        termsEnum!!.seekExact(term.bytes(), state)
                        logger.debug { "[TermQuery.getTermsEnum] seekExact done term=${term.field()}:${term.text()} ord=${context.ord}" }
                    }
                    return termsEnum
                }

                @Throws(IOException::class) override fun get(leadCost: Long): Scorer {
                    val termsEnum: TermsEnum? = getTermsEnum()
                    if (termsEnum == null) {
                        return ConstantScoreScorer(0f, scoreMode, DocIdSetIterator.empty())
                    }

                    var norms: NumericDocValues? = null
                    if (scoreMode.needsScores()) {
                        norms = context.reader().getNormValues(term.field())
                    }

                    if (scoreMode == ScoreMode.TOP_SCORES) {
                        return TermScorer(
                            termsEnum.impacts(PostingsEnum.FREQS.toInt()), simScorer!!, norms, topLevelScoringClause)
                    } else {
                        val flags: Int = if (scoreMode.needsScores()) PostingsEnum.FREQS.toInt() else PostingsEnum.NONE.toInt()
                        return TermScorer(termsEnum.postings(null, flags), simScorer!!, norms)
                    }
                }

                @Throws(IOException::class) override fun bulkScorer(): BulkScorer? {
                    if (!scoreMode.needsScores()) {
                        val iterator: DocIdSetIterator = get(Long.MAX_VALUE).iterator()
                        val maxDoc: Int = context.reader().maxDoc()
                        return ConstantScoreScorerSupplier.fromIterator(iterator, 0f, scoreMode, maxDoc)
                            .bulkScorer()
                    }
                    return super.bulkScorer()
                }

                override fun cost(): Long {
                    try {
                        val te: TermsEnum? = getTermsEnum()
                        return te?.docFreq()?.toLong() ?: 0L
                    }catch (e: IOException) {
                        throw IOException(e.message)
                    }
                }

                override fun setTopLevelScoringClause() {
                    topLevelScoringClause = true
                }
            }
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return true
        }

        /**
         * Returns a [TermsEnum] positioned at this weights Term or null if the term does not
         * exist in the given context
         */
        @Throws(IOException::class) private fun getTermsEnum(context: LeafReaderContext): TermsEnum? {
            checkNotNull(termStates)
            require(termStates.wasBuiltFor(ReaderUtil.getTopLevelContext(context))
            ) {("The top-reader used to create Weight is not the same as the current reader's top-reader ("
                    + ReaderUtil.getTopLevelContext(context))}
            val supplier: IOSupplier<TermState?>? = termStates.get(context)
            val state: TermState? = supplier?.get()
            if (state == null) { // term is not present in that reader
                require(termNotInReader(context.reader(), term)
                ) { "no termstate found but term exists in reader term=$term" }
                return null
            }
            val termsEnum: TermsEnum = context.reader().terms(term.field())!!.iterator()
            termsEnum.seekExact(term.bytes(), state)
            return termsEnum
        }

        @Throws(IOException::class) private fun termNotInReader(reader: LeafReader, term: Term): Boolean {
            // only called from assert
            // System.out.println("TQ.termNotInReader reader=" + reader + " term=" +
            // field + ":" + bytes.utf8ToString());
            return reader.docFreq(term) == 0
        }

        @Throws(IOException::class) override fun explain(context: LeafReaderContext, doc: Int): Explanation {
            val scorer: Scorer? = scorer(context)
            if (scorer != null) {
                val newDoc: Int = scorer.iterator().advance(doc)
                if (newDoc == doc) {
                    val freq: Float = (scorer as TermScorer).freq().toFloat()
                    val norms: NumericDocValues? = context.reader().getNormValues(term.field())
                    var norm = 1L
                    if (norms != null && norms.advanceExact(doc)) {
                        norm = norms.longValue()
                    }
                    val freqExplanation: Explanation =
                        Explanation.match(freq, "freq, occurrences of term within document")
                    val scoreExplanation: Explanation = simScorer!!.explain(freqExplanation, norm)
                    val description = ("weight("
                    + query
                    + " in "
                    + doc
                    + ") ["
                    + similarity::class.simpleName
                    + "], result of:")

                    return Explanation.match(
                        value = scoreExplanation.value,
                        description = description,
                        details = mutableListOf(scoreExplanation))
                }
            }
            return Explanation.noMatch("no matching term")
        }

        @Throws(IOException::class) override fun count(context: LeafReaderContext): Int {
            return if (!context.reader().hasDeletions()) {
                val termsEnum: TermsEnum? = getTermsEnum(context)
                // termsEnum is not null if term state is available
                termsEnum?.docFreq() ?: // the term cannot be found in the dictionary so the count is 0
                0
            } else {
                super.count(context)
            }
        }
    }

    /** Constructs a query for the term `t`.  */
    constructor(t: Term) {
        term = requireNotNull<Term>(t)
        perReaderTermState = null
    }

    /**
     * Expert: constructs a TermQuery that will use the provided docFreq instead of looking up the
     * docFreq against the searcher.
     */
    constructor(t: Term, states: TermStates) {
        checkNotNull(states)
        term = requireNotNull<Term>(t)
        perReaderTermState = requireNotNull<TermStates>(states)
    }

    /** Returns the term of this query.  */
    fun getTerm(): Term {
        return term
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        val context: IndexReaderContext = searcher.topReaderContext
        val termState: TermStates = if (perReaderTermState == null || !perReaderTermState.wasBuiltFor(context)) {
            TermStates.build(searcher, term, scoreMode.needsScores())
        } else {
            // PRTS was pre-build for this IS
            this.perReaderTermState
        }

        return this.TermWeight(searcher, scoreMode, boost, termState)
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(term.field())) {
            visitor.consumeTerms(this, term)
        }
    }

    /** Prints a user-readable version of this query.  */
    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        if (term.field() != field) {
            buffer.append(term.field())
            buffer.append(":")
        }
        buffer.append(term.text())
        return buffer.toString()
    }

    val termStates: TermStates?
        /**
         * Returns the [TermStates] passed to the constructor, or null if it was not passed.
         *
         * @lucene.experimental
         */
        get() = perReaderTermState

    /** Returns true iff `other` is equal to `this`.  */
    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && term == (other as TermQuery).term
    }

    override fun hashCode(): Int {
        return classHash() xor term.hashCode()
    }
}
