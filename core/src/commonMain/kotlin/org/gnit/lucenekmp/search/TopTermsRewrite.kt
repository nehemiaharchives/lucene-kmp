package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.PriorityQueue
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import kotlin.math.max
import kotlin.math.min


/**
 * Base rewrite method for collecting only the top terms via a priority queue.
 *
 * @lucene.internal Only public to be accessible by spans package.
 */
abstract class TopTermsRewrite<B>
/**
 * Create a TopTermsBooleanQueryRewrite for at most `size` terms.
 *
 *
 * NOTE: if [IndexSearcher.getMaxClauseCount] is smaller than `size`, then it
 * will be used instead.
 */(
    /** return the maximum priority queue size  */
    val size: Int
) : TermCollectingRewrite<B>() {
    /**
     * return the maximum size of the priority queue (for boolean rewrites this is
     * BooleanQuery#getMaxClauseCount).
     */
    protected abstract val maxSize: Int

    @Throws(IOException::class)
    override fun rewrite(indexSearcher: IndexSearcher, query: MultiTermQuery): Query {
        val maxSize = min(size, this.maxSize)
        val stQueue: PriorityQueue<ScoreTerm> = PriorityQueue()
        collectTerms(
            indexSearcher.getIndexReader(),
            query,
            object : TermCollector() {
                private val maxBoostAtt: MaxNonCompetitiveBoostAttribute =
                    attributes.addAttribute(MaxNonCompetitiveBoostAttribute::class)

                private val visitedTerms: MutableMap<BytesRef, ScoreTerm> = mutableMapOf()

                private var termsEnum: TermsEnum? = null
                private var boostAtt: BoostAttribute? = null
                private var st: ScoreTerm? = null

                override fun setNextEnum(termsEnum: TermsEnum) {
                    this.termsEnum = termsEnum

                    require(compareToLastTerm(null))

                    // lazy init the initial ScoreTerm because comparator is not known on ctor:
                    if (st == null) st = ScoreTerm(TermStates(topReaderContext))
                    boostAtt = termsEnum.attributes().addAttribute(BoostAttribute::class)
                }

                // for assert:
                private var lastTerm: BytesRefBuilder? = null

                fun compareToLastTerm(t: BytesRef?): Boolean {
                    if (lastTerm == null && t != null) {
                        lastTerm = BytesRefBuilder()
                        lastTerm!!.append(t)
                    } else if (t == null) {
                        lastTerm = null
                    } else {
                        require(lastTerm!!.get() < t) { "lastTerm=$lastTerm t=$t" }
                        lastTerm!!.copyBytes(t)
                    }
                    return true
                }

                @Throws(IOException::class)
                override fun collect(bytes: BytesRef): Boolean {
                    val boost: Float = boostAtt!!.boost

                    // make sure within a single seg we always collect
                    // terms in order
                    require(compareToLastTerm(bytes))

                    // System.out.println("TTR.collect term=" + bytes.utf8ToString() + " boost=" + boost + "
                    // ord=" + readerContext.ord);
                    // ignore uncompetitive hits
                    if (stQueue.size() == maxSize) {
                        val t: ScoreTerm = stQueue.peek()!!
                        if (boost < t.boost) return true
                        if (boost == t.boost && bytes > t.bytes.get()) return true
                    }
                    var t = visitedTerms[bytes]
                    val state: TermState = checkNotNull(termsEnum!!.termState())
                    if (t != null) {
                        // if the term is already in the PQ, only update docFreq of term in PQ
                        require(t.boost == boost) { "boost should be equal in all segment TermsEnums" }
                        t.termState.register(
                            state, readerContext!!.ord, termsEnum!!.docFreq(), termsEnum!!.totalTermFreq()
                        )
                    } else {
                        // add new entry in PQ, we must clone the term, else it may get overwritten!
                        st!!.bytes.copyBytes(bytes)
                        st!!.boost = boost
                        visitedTerms.put(st!!.bytes.get(), st!!)
                        require(st!!.termState.docFreq() == 0)
                        st!!.termState.register(
                            state, readerContext!!.ord, termsEnum!!.docFreq(), termsEnum!!.totalTermFreq()
                        )
                        stQueue.offer(st)
                        // possibly drop entries from queue
                        if (stQueue.size() > maxSize) {
                            st = stQueue.poll()
                            visitedTerms.remove(st!!.bytes.get())
                            st!!.termState.clear() // reset the termstate!
                        } else {
                            st = ScoreTerm(TermStates(topReaderContext))
                        }
                        require(stQueue.size() <= maxSize) { "the PQ size must be limited to maxSize" }
                        // set maxBoostAtt with values to help FuzzyTermsEnum to optimize
                        if (stQueue.size() == maxSize) {
                            t = stQueue.peek()
                            maxBoostAtt.maxNonCompetitiveBoost = t!!.boost
                            maxBoostAtt.setCompetitiveTerm(t.bytes.get())
                        }
                    }

                    return true
                }
            })

        val b: B = topLevelBuilder
        val scoreTerms: Array<ScoreTerm> = stQueue.toTypedArray()
        ArrayUtil.timSort(scoreTerms) { st1, st2 -> st1.bytes.get().compareTo(st2.bytes.get()) }

        for (st in scoreTerms) {
            val term = Term(query.field, st.bytes.toBytesRef())
            // We allow negative term scores (fuzzy query does this, for example) while collecting the
            // terms,
            // but truncate such boosts to 0.0f when building the query:
            addClause(
                b, term, st.termState.docFreq(), max(0.0f, st.boost), st.termState
            ) // add to query
        }
        return build(b)
    }

    override fun hashCode(): Int {
        return 31 * size
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (this::class != obj::class) return false
        val other = obj as TopTermsRewrite<*>
        return size == other.size
    }

    internal class ScoreTerm(val termState: TermStates) : Comparable<ScoreTerm> {
        val bytes: BytesRefBuilder = BytesRefBuilder()
        var boost: Float = 0f

        override fun compareTo(other: ScoreTerm): Int {
            return if (this.boost == other.boost) other.bytes.get().compareTo(this.bytes.get())
            else Float.compare(this.boost, other.boost)
        }
    }
}
