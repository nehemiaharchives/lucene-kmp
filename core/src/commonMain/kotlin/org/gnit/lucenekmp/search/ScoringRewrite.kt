package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.search.IndexSearcher.TooManyClauses
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.ByteBlockPool.DirectAllocator
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefHash
import org.gnit.lucenekmp.util.BytesRefHash.DirectBytesStartArray
import org.gnit.lucenekmp.util.RamUsageEstimator

/**
 * Base rewrite method that translates each term into a query, and keeps the scores as computed by
 * the query.
 *
 * @lucene.internal Only public to be accessible by spans package.
 */
abstract class ScoringRewrite<B> : TermCollectingRewrite<B>() {
    /**
     * This method is called after every new term to check if the number of max clauses (e.g. in
     * BooleanQuery) is not exceeded. Throws the corresponding [RuntimeException].
     */
    @Throws(IOException::class)
    protected abstract fun checkMaxClauseCount(count: Int)

    @Throws(IOException::class)
    override fun rewrite(indexSearcher: IndexSearcher, query: MultiTermQuery): Query {
        val reader: IndexReader = indexSearcher.indexReader
        val builder: B = topLevelBuilder
        val col: ParallelArraysTermCollector = this.ParallelArraysTermCollector()
        collectTerms(reader, query, col)

        val size: Int = col.terms.size()
        if (size > 0) {
            val sort: IntArray = col.terms.sort()
            val boost: FloatArray = col.array.boost!!
            val termStates: Array<TermStates?> = col.array.termState!!
            for (i in 0..<size) {
                val pos = sort[i]
                val term = Term(query.field, col.terms.get(pos, BytesRef()))
                require(reader.docFreq(term) == termStates[pos]!!.docFreq())
                addClause(builder, term, termStates[pos]!!.docFreq(), boost[pos], termStates[pos])
            }
        }
        return build(builder)
    }

    internal inner class ParallelArraysTermCollector : TermCollector() {
        val array: TermFreqBoostByteStart = TermFreqBoostByteStart(16)
        val terms: BytesRefHash = BytesRefHash(ByteBlockPool(DirectAllocator()), 16, array)
        var termsEnum: TermsEnum? = null

        private var boostAtt: BoostAttribute? = null

        override fun setNextEnum(termsEnum: TermsEnum) {
            this.termsEnum = termsEnum
            this.boostAtt = termsEnum.attributes().addAttribute(BoostAttribute::class)
        }

        @Throws(IOException::class)
        override fun collect(bytes: BytesRef): Boolean {
            val e: Int = terms.add(bytes)
            val state: TermState = checkNotNull(termsEnum!!.termState())
            if (e < 0) {
                // duplicate term: update docFreq
                val pos = (-e) - 1
                array.termState!![pos]!!.register(
                    state, readerContext!!.ord, termsEnum!!.docFreq(), termsEnum!!.totalTermFreq()
                )
                require(
                    array.boost!![pos] == boostAtt!!.boost
                ) { "boost should be equal in all segment TermsEnums" }
            } else {
                // new entry: we populate the entry initially
                array.boost!![e] = boostAtt!!.boost
                array.termState!![e] =
                    TermStates(
                        topReaderContext!!,
                        state,
                        readerContext!!.ord,
                        termsEnum!!.docFreq(),
                        termsEnum!!.totalTermFreq()
                    )
                this@ScoringRewrite.checkMaxClauseCount(terms.size())
            }
            return true
        }
    }

    /** Special implementation of BytesStartArray that keeps parallel arrays for boost and docFreq  */
    internal class TermFreqBoostByteStart(initSize: Int) : DirectBytesStartArray(initSize) {
        var boost: FloatArray? = null
        var termState: Array<TermStates?>? = null

        override fun init(): IntArray {
            val ord: IntArray = super.init()
            boost = FloatArray(ArrayUtil.oversize(ord.size, Float.SIZE_BYTES))
            termState =
                kotlin.arrayOfNulls(ArrayUtil.oversize(ord.size, RamUsageEstimator.NUM_BYTES_OBJECT_REF))
            require(termState!!.size >= ord.size && boost!!.size >= ord.size)
            return ord
        }

        override fun grow(): IntArray {
            val ord: IntArray = super.grow()
            boost = ArrayUtil.grow(boost!!, ord.size)
            if (termState!!.size < ord.size) {
                val tmpTermState: Array<TermStates?> =
                    kotlin.arrayOfNulls(
                        ArrayUtil.oversize(
                            ord.size,
                            RamUsageEstimator.NUM_BYTES_OBJECT_REF
                        )
                    )
                /*java.lang.System.arraycopy(termState, 0, tmpTermState, 0, termState.size)*/
                termState!!.copyInto(
                    destination = tmpTermState,
                    destinationOffset = 0,
                    startIndex = 0,
                    endIndex = termState!!.size
                )
                termState = tmpTermState
            }
            require(termState!!.size >= ord.size && boost!!.size >= ord.size)
            return ord
        }

        override fun clear(): IntArray? {
            boost = null
            termState = null
            return super.clear()
        }
    }

    companion object {
        /**
         * A rewrite method that first translates each term into [BooleanClause.Occur.SHOULD] clause
         * in a BooleanQuery, and keeps the scores as computed by the query. Note that typically such
         * scores are meaningless to the user, and require non-trivial CPU to compute, so it's almost
         * always better to use [MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE] or [ ][MultiTermQuery.CONSTANT_SCORE_REWRITE] instead.
         *
         *
         * **NOTE**: This rewrite method will hit [IndexSearcher.TooManyClauses] if the number
         * of terms exceeds [IndexSearcher.getMaxClauseCount].
         */
        val SCORING_BOOLEAN_REWRITE: ScoringRewrite<BooleanQuery.Builder?> =
            object : ScoringRewrite<BooleanQuery.Builder?>() {
                override val topLevelBuilder: BooleanQuery.Builder?
                    get() = BooleanQuery.Builder()

                override fun build(builder: BooleanQuery.Builder?): Query {
                    return builder!!.build()
                }

                override fun addClause(
                    topLevel: BooleanQuery.Builder?,
                    term: Term,
                    docCount: Int,
                    boost: Float,
                    states: TermStates?
                ) {
                    val tq = TermQuery(term, states!!)
                    topLevel!!.add(BoostQuery(tq, boost), BooleanClause.Occur.SHOULD)
                }

                override fun checkMaxClauseCount(count: Int) {
                    if (count > IndexSearcher.maxClauseCount) throw TooManyClauses()
                }
            }

        /**
         * Like [.SCORING_BOOLEAN_REWRITE] except scores are not computed. Instead, each matching
         * document receives a constant score equal to the query's boost.
         *
         *
         * **NOTE**: This rewrite method will hit [IndexSearcher.TooManyClauses] if the number
         * of terms exceeds [IndexSearcher.getMaxClauseCount].
         */
        val CONSTANT_SCORE_BOOLEAN_REWRITE: MultiTermQuery.RewriteMethod = object : MultiTermQuery.RewriteMethod() {
            @Throws(IOException::class)
            override fun rewrite(indexSearcher: IndexSearcher, query: MultiTermQuery): Query {
                val bq = SCORING_BOOLEAN_REWRITE.rewrite(indexSearcher, query)
                // strip the scores off
                return ConstantScoreQuery(bq)
            }
        }
    }
}
