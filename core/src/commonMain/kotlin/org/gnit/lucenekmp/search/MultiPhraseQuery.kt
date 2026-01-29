package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SlowImpactsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.PriorityQueue

/**
 * A generalized version of [PhraseQuery], with the possibility of adding more than one term
 * at the same position that are treated as a disjunction (OR). To use this class to search for the
 * phrase "Microsoft app*" first create a Builder and use [Builder.add] on the term
 * "microsoft" (assuming lowercase analysis), then find all terms that have "app" as prefix using
 * [LeafReader.terms], seeking to "app" then iterating and collecting terms until
 * there is no longer that prefix, and finally use [Builder.add] to add them. [ ][Builder.build] returns the fully constructed (and immutable) MultiPhraseQuery.
 */
class MultiPhraseQuery private constructor(
    private val field: String,
    termArrays: Array<Array<Term>>,
    positions: IntArray,
    slop: Int
) : Query() {
    /** A builder for multi-phrase queries  */
    class Builder {
        private var field: String? // becomes non-null on first add() then is unmodified
        private val termArrays: ArrayList<Array<Term>>
        private val positions: IntArrayList
        private var slop: Int

        /** Default constructor.  */
        constructor() {
            this.field = null
            this.termArrays = ArrayList<Array<Term>>()
            this.positions = IntArrayList()
            this.slop = 0
        }

        /**
         * Copy constructor: this will create a builder that has the same configuration as the provided
         * builder.
         */
        constructor(multiPhraseQuery: MultiPhraseQuery) {
            this.field = multiPhraseQuery.field

            val length = multiPhraseQuery.termArrays.size

            this.termArrays = ArrayList<Array<Term>>(length)
            this.positions = IntArrayList(length)

            for (i in 0..<length) {
                this.termArrays.add(multiPhraseQuery.termArrays[i])
                this.positions.add(multiPhraseQuery.positions[i])
            }

            this.slop = multiPhraseQuery.slop
        }

        /**
         * Sets the phrase slop for this query.
         *
         * @see PhraseQuery.getSlop
         */
        fun setSlop(s: Int): Builder {
            require(s >= 0) { "slop value cannot be negative" }
            slop = s

            return this
        }

        /** Add a single term at the next position in the phrase.  */
        fun add(term: Term): Builder {
            return add(arrayOf(term))
        }

        /**
         * Add multiple terms at the next position in the phrase. Any of the terms may match (a
         * disjunction). The array is not copied or mutated, the caller should consider it immutable
         * subsequent to calling this method.
         */
        fun add(terms: Array<Term>): Builder {
            var position = 0
            if (positions.size() > 0) position = positions.get(positions.size() - 1) + 1

            return add(terms, position)
        }

        /**
         * Allows to specify the relative position of terms within the phrase. The array is not copied
         * or mutated, the caller should consider it immutable subsequent to calling this method.
         */
        fun add(terms: Array<Term>, position: Int): Builder {
            /*java.util.Objects.requireNonNull<Array<Term>>(
                terms,
                "Term array must not be null"
            )*/
            if (termArrays.isEmpty()) field = terms[0].field()

            for (term in terms) {
                require(term.field() == field) { "All phrase terms must be in the same field ($field): $term" }
            }

            termArrays.add(terms)
            positions.add(position)

            return this
        }

        /** Builds a [MultiPhraseQuery].  */
        fun build(): MultiPhraseQuery {
            val termArraysArray: Array<Array<Term>> =
                termArrays.toTypedArray<Array<Term>>()
            return MultiPhraseQuery(field!!, termArraysArray, positions.toArray(), slop)
        }
    }

    private val termArrays: Array<Array<Term>>

    /** Returns the relative positions of terms in this phrase. Do not modify!  */
    val positions: IntArray

    /**
     * Sets the phrase slop for this query.
     *
     * @see PhraseQuery.getSlop
     */
    val slop: Int

    init {
        // No argument checks here since they are provided by the MultiPhraseQuery.Builder
        this.termArrays = termArrays
        this.positions = positions
        this.slop = slop
    }

    /** Returns the arrays of arrays of terms in the multi-phrase. Do not modify!  */
    fun getTermArrays(): Array<Array<Term>> {
        return termArrays
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        if (termArrays.isEmpty()) {
            return MatchNoDocsQuery("empty MultiPhraseQuery")
        } else if (termArrays.size == 1) { // optimize one-term case
            val terms: Array<Term> = termArrays[0]
            val builder: BooleanQuery.Builder = BooleanQuery.Builder()
            for (term in terms) {
                builder.add(
                    TermQuery(term),
                    BooleanClause.Occur.SHOULD
                )
            }
            return builder.build()
        } else {
            return super.rewrite(indexSearcher)
        }
    }

    override fun visit(visitor: QueryVisitor) {
        if (!visitor.acceptField(field)) {
            return
        }
        val v: QueryVisitor =
            visitor.getSubVisitor(BooleanClause.Occur.MUST, this)
        for (terms in termArrays) {
            val sv: QueryVisitor =
                v.getSubVisitor(BooleanClause.Occur.SHOULD, this)
            sv.consumeTerms(this, *terms)
        }
    }

    override fun createWeight(
        searcher: IndexSearcher,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        val termStates: MutableMap<Term, TermStates> = mutableMapOf()
        return object : PhraseWeight(this, field, searcher, scoreMode) {
            @Throws(IOException::class)
            override fun getStats(searcher: IndexSearcher): SimScorer? {
                // compute idf

                val allTermStats: ArrayList<TermStatistics> = ArrayList()
                for (terms in termArrays) {
                    for (term in terms!!) {
                        var ts: TermStates? = termStates[term]
                        if (ts == null) {
                            ts = TermStates.build(searcher, term, scoreMode.needsScores())
                            termStates[term] = ts
                        }
                        if (scoreMode.needsScores() && ts.docFreq() > 0) {
                            allTermStats.add(searcher.termStatistics(term, ts.docFreq(), ts.totalTermFreq()))
                        }
                    }
                }
                return if (allTermStats.isEmpty()) {
                    null // none of the terms were found, we won't use sim at all
                } else {
                    similarity.scorer(
                        boost,
                        searcher.collectionStatistics(field)!!,
                        *allTermStats.toTypedArray<TermStatistics>()
                    )
                }
            }

            @Throws(IOException::class)
            override fun getPhraseMatcher(
                context: LeafReaderContext,
                scorer: SimScorer,
                exposeOffsets: Boolean
            ): PhraseMatcher? {
                assert(termArrays.isNotEmpty())
                val reader: LeafReader = context.reader()

                val postingsFreqs: Array<PhraseQuery.PostingsAndFreq> =
                    kotlin.arrayOfNulls<PhraseQuery.PostingsAndFreq>(termArrays.size) as Array<PhraseQuery.PostingsAndFreq>

                val fieldTerms: Terms? = reader.terms(field)
                if (fieldTerms == null) {
                    return null
                }

                // TODO: move this check to createWeight to happen earlier to the user
                check(fieldTerms.hasPositions()) {
                    ("field \""
                            + field
                            + "\" was indexed without position data;"
                            + " cannot run MultiPhraseQuery (phrase="
                            + query
                            + ")")
                }

                // Reuse single TermsEnum below:
                val termsEnum: TermsEnum = fieldTerms.iterator()
                var totalMatchCost = 0f

                for (pos in postingsFreqs.indices) {
                    val terms: Array<Term> = termArrays[pos]
                    val postings: MutableList<PostingsEnum> = mutableListOf()

                    for (term in terms) {
                        val supplier: IOSupplier<TermState?>? = termStates[term]!!.get(context)
                        val termState: TermState? =
                            if (supplier == null) null else supplier.get()
                        if (termState != null) {
                            termsEnum.seekExact(term.bytes(), termState)
                            postings.add(
                                termsEnum.postings(
                                    null,
                                    (if (exposeOffsets) PostingsEnum.ALL else PostingsEnum.POSITIONS).toInt()
                                )!!
                            )
                            totalMatchCost += PhraseQuery.termPositionsCost(termsEnum)
                        }
                    }

                    if (postings.isEmpty()) {
                        return null
                    }
                    val postingsEnum: PostingsEnum = if (postings.size == 1) {
                        postings[0]
                    } else {
                        if (exposeOffsets)
                            UnionFullPostingsEnum(postings)
                        else
                            UnionPostingsEnum(postings)
                    }

                    postingsFreqs[pos] =
                        PhraseQuery.PostingsAndFreq(
                            postingsEnum, SlowImpactsEnum(postingsEnum), positions[pos], *terms
                        )
                }

                // sort by increasing docFreq order
                if (slop == 0) {
                    ArrayUtil.timSort(postingsFreqs)
                    return ExactPhraseMatcher(postingsFreqs, scoreMode, scorer, totalMatchCost)
                } else {
                    return SloppyPhraseMatcher(
                        postingsFreqs, slop, scoreMode, scorer, totalMatchCost, exposeOffsets
                    )
                }
            }
        }
    }

    /** Prints a user-readable version of this query.  */
    override fun toString(f: String?): String {
        val buffer = StringBuilder()
        if (field == null || field != f) {
            buffer.append(field)
            buffer.append(":")
        }

        buffer.append("\"")
        var lastPos = -1

        for (i in termArrays.indices) {
            val terms: Array<Term> = termArrays[i]
            val position = positions[i]
            if (i != 0) {
                buffer.append(" ")
                for (j in 1..<(position - lastPos)) {
                    buffer.append(" ")
                }
            }
            if (terms.size > 1) {
                buffer.append("(")
                for (j in terms.indices) {
                    buffer.append(terms[j].text())
                    if (j < terms.size - 1) buffer.append(" ")
                }
                buffer.append(")")
            } else {
                buffer.append(terms[0].text())
            }
            lastPos = position
        }
        buffer.append("\"")

        if (slop != 0) {
            buffer.append("~")
            buffer.append(slop)
        }

        return buffer.toString()
    }

    /** Returns true if `o` is equal to this.  */
    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) /*&& equalsTo(this::class.cast(other))*/
    }

    private fun equalsTo(other: MultiPhraseQuery): Boolean {
        return this.slop == other.slop && termArraysEquals(this.termArrays, other.termArrays)
                && this.positions.contentEquals(other.positions)
    }

    /** Returns a hash code value for this object.  */
    override fun hashCode(): Int {
        return (classHash()
                xor slop
                xor termArraysHashCode() // terms equal implies field equal
                xor positions.contentHashCode())
    }

    // Breakout calculation of the termArrays hashcode
    private fun termArraysHashCode(): Int {
        var hashCode = 1
        for (termArray in termArrays) {
            hashCode = 31 * hashCode + (if (termArray == null) 0 else termArray.contentHashCode())
        }
        return hashCode
    }

    // Breakout calculation of the termArrays equals
    private fun termArraysEquals(
        termArrays1: Array<Array<Term>>,
        termArrays2: Array<Array<Term>>
    ): Boolean {
        if (termArrays1.size != termArrays2.size) {
            return false
        }

        for (i in termArrays1.indices) {
            val termArray1: Array<Term> = termArrays1[i]
            val termArray2: Array<Term> = termArrays2[i]
            if (!(if (termArray1 == null) termArray2 == null else termArray1.contentEquals(termArray2))) {
                return false
            }
        }
        return true
    }

    /**
     * Takes the logical union of multiple PostingsEnum iterators.
     *
     *
     * Note: positions are merged during freq()
     *
     * @lucene.internal
     */
    open class UnionPostingsEnum(subs: MutableCollection<PostingsEnum>) :
        PostingsEnum() {
        /** queue ordered by docid  */
        val docsQueue: DocsQueue = DocsQueue(subs.size)

        /** cost of this enum: sum of its subs  */
        val cost: Long

        /** queue ordered by position for current doc  */
        open val posQueue: PositionsQueue = PositionsQueue()

        /** current doc posQueue is working  */
        var posQueueDoc: Int = -2

        /** list of subs (unordered)  */
        open lateinit var subs: Array<out PostingsEnum>

        init {
            var cost: Long = 0
            for (sub in subs) {
                docsQueue.add(sub)
                cost += sub.cost()
            }
            this.cost = cost
            this.subs = subs.toTypedArray<PostingsEnum>()
        }

        @Throws(IOException::class)
        override fun freq(): Int {
            val doc = docID()
            if (doc != posQueueDoc) {
                posQueue.clear()
                for (sub in subs) {
                    if (sub.docID() == doc) {
                        val freq: Int = sub.freq()
                        for (i in 0..<freq) {
                            posQueue.add(sub.nextPosition())
                        }
                    }
                }
                posQueue.sort()
                posQueueDoc = doc
            }
            return posQueue.size()
        }

        @Throws(IOException::class)
        override fun nextPosition(): Int {
            return posQueue.next()
        }

        override fun docID(): Int {
            return docsQueue.top().docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            var top: PostingsEnum = docsQueue.top()
            val doc: Int = top.docID()

            do {
                top.nextDoc()
                top = docsQueue.updateTop()
            } while (top.docID() == doc)

            return top.docID()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            var top: PostingsEnum = docsQueue.top()

            do {
                top.advance(target)
                top = docsQueue.updateTop()
            } while (top.docID() < target)

            return top.docID()
        }

        override fun cost(): Long {
            return cost
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            return -1 // offsets are unsupported
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            return -1 // offsets are unsupported
        }

        override val payload: BytesRef?
            get() = null // payloads are unsupported

        /** disjunction of postings ordered by docid.  */
        class DocsQueue(size: Int) :
            PriorityQueue<PostingsEnum>(size) {
            override fun lessThan(
                a: PostingsEnum,
                b: PostingsEnum
            ): Boolean {
                return a.docID() < b.docID()
            }
        }

        /**
         * queue of terms for a single document. its a sorted array of all the positions from all the
         * postings
         */
        class PositionsQueue {
            private var arraySize = 16
            private var index = 0
            private var size = 0
            private var array = IntArray(arraySize)

            fun add(i: Int) {
                if (size == arraySize) growArray()

                array[size++] = i
            }

            fun next(): Int {
                return array[index++]
            }

            fun sort() {
                Arrays.sort(array, index, size)
            }

            fun clear() {
                index = 0
                size = 0
            }

            fun size(): Int {
                return size
            }

            private fun growArray() {
                val newArray = IntArray(arraySize * 2)
                System.arraycopy(array, 0, newArray, 0, arraySize)
                array = newArray
                arraySize *= 2
            }
        }
    }

    class PostingsAndPosition(val pe: PostingsEnum) : PostingsEnum() {
        var pos: Int = 0
        var upto: Int = 0

        override fun freq() = this.pe.freq()
        override fun nextPosition() = this.pe.nextPosition()
        override fun startOffset() = this.pe.startOffset()
        override fun endOffset() = this.pe.endOffset()
        override val payload: BytesRef?
            get() = this.pe.payload

        override fun docID() = this.pe.docID()
        override fun nextDoc() = this.pe.nextDoc()
        override fun advance(target: Int) = this.pe.advance(target)
        override fun cost(): Long = this.pe.cost()
    }

    /**
     * Slower version of UnionPostingsEnum that delegates offsets and positions, for use by
     * MatchesIterator
     *
     * @lucene.internal
     */
    class UnionFullPostingsEnum(subs: MutableList<PostingsEnum>) : UnionPostingsEnum(subs) {
        var freq: Int = -1
        var started: Boolean = false

        val prQueue: PriorityQueue<PostingsAndPosition> = object : PriorityQueue<PostingsAndPosition>(subs.size) {
            override fun lessThan(a: PostingsAndPosition, b: PostingsAndPosition): Boolean {
                return a.pos < b.pos
            }
        }

        override var subs: Array<out PostingsEnum>

        init {
            val al = ArrayList<PostingsAndPosition>()
            for (pe in subs) {
                al.add(PostingsAndPosition(pe))
            }
            this.subs = al.toTypedArray<PostingsAndPosition>()
        }

        @Throws(IOException::class)
        override fun freq(): Int {
            val doc = docID()
            if (doc == posQueueDoc) {
                return freq
            }
            freq = 0
            started = false
            prQueue.clear()
            for (ppBeforeCast in subs) {

                val pp = ppBeforeCast as PostingsAndPosition

                if (pp.pe.docID() == doc) {
                    pp.pos = pp.pe.nextPosition()
                    pp.upto = pp.pe.freq()
                    prQueue.add(pp)
                    freq += pp.upto
                }
            }
            return freq
        }

        @Throws(IOException::class)
        override fun nextPosition(): Int {
            if (!started) {
                started = true
                return prQueue.top().pos
            }
            if (prQueue.top().upto == 1) {
                prQueue.pop()
                return prQueue.top().pos
            }
            prQueue.top().pos = prQueue.top().pe.nextPosition()
            prQueue.top().upto--
            prQueue.updateTop()
            return prQueue.top().pos
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            return prQueue.top().pe.startOffset()
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            return prQueue.top().pe.endOffset()
        }

        override val payload
            get(): BytesRef? {
                return prQueue.top().pe.payload
            }
    }
}
