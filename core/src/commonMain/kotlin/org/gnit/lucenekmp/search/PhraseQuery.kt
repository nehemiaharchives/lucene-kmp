package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.ImpactsEnum
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
import org.gnit.lucenekmp.search.ExactPhraseMatcher
import org.gnit.lucenekmp.search.SloppyPhraseMatcher
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOSupplier
import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import kotlin.jvm.JvmOverloads
import kotlin.jvm.Transient
import kotlin.reflect.cast

/**
 * A Query that matches documents containing a particular sequence of terms. A PhraseQuery is built
 * by QueryParser for input like `"new york"`.
 *
 *
 * This query may be combined with other terms or queries with a [BooleanQuery].
 *
 *
 * **NOTE**: All terms in the phrase must match, even those at the same position. If you have
 * terms at the same position, perhaps synonyms, you probably want [MultiPhraseQuery] instead
 * which only requires one term at a position to match. <br></br>
 * Also, Leading holes don't have any particular meaning for this query and will be ignored. For
 * instance this query:
 *
 * <pre class="prettyprint">
 * PhraseQuery.Builder builder = new PhraseQuery.Builder();
 * builder.add(new Term("body", "one"), 4);
 * builder.add(new Term("body", "two"), 5);
 * PhraseQuery pq = builder.build();
</pre> *
 *
 * is equivalent to the below query:
 *
 * <pre class="prettyprint">
 * PhraseQuery.Builder builder = new PhraseQuery.Builder();
 * builder.add(new Term("body", "one"), 0);
 * builder.add(new Term("body", "two"), 1);
 * PhraseQuery pq = builder.build();
</pre> *
 */
class PhraseQuery private constructor(slop: Int, terms: Array<Term>, positions: IntArray) :
    Query() {
    /** A builder for phrase queries.  */
    class Builder {
        private var slop = 0
        private val terms: MutableList<Term>
        private val positions: IntArrayList

        /** Sole constructor.  */
        init {
            terms = ArrayList<Term>()
            positions = IntArrayList()
        }

        /**
         * Set the slop.
         *
         * @see PhraseQuery.getSlop
         */
        fun setSlop(slop: Int): Builder {
            this.slop = slop
            return this
        }

        /**
         * Adds a term to the end of the query phrase. The relative position of the term within the
         * phrase is specified explicitly, but must be greater than or equal to that of the previously
         * added term. A greater position allows phrases with gaps (e.g. in connection with stopwords).
         * If the position is equal, you most likely should be using [MultiPhraseQuery] instead
         * which only requires one term at each position to match; this class requires all of them.
         */
        /**
         * Adds a term to the end of the query phrase. The relative position of the term is the one
         * immediately after the last term added.
         */
        @JvmOverloads
        fun add(
            term: Term,
            position: Int = if (positions.isEmpty) 0 else 1 + positions.get(positions.size() - 1)
        ): Builder {
            requireNotNull<Term>(term) { "Cannot add a null term to PhraseQuery" }
            require(position >= 0) { "Positions must be >= 0, got $position" }
            if (!positions.isEmpty) {
                val lastPosition: Int = positions.get(positions.size() - 1)
                require(position >= lastPosition) { "Positions must be added in order, got $position after $lastPosition" }
            }
            require(!(terms.isEmpty() == false && term.field() == terms.get(0).field() == false)) {
                ("All terms must be on the same field, got "
                        + term.field()
                        + " and "
                        + terms[0].field())
            }
            terms.add(term)
            positions.add(position)
            return this
        }

        /** Build a phrase query based on the terms that have been added.  */
        fun build(): PhraseQuery {
            val terms: Array<Term> = this.terms.toTypedArray<Term>()
            return PhraseQuery(slop, terms, positions.toArray())
        }
    }

    /**
     * Return the slop for this [PhraseQuery].
     *
     *
     * The slop is an edit distance between respective positions of terms as defined in this [ ] and the positions of terms in a document.
     *
     *
     * For instance, when searching for `"quick fox"`, it is expected that the difference
     * between the positions of `fox` and `quick` is 1. So `"a quick brown fox"`
     * would be at an edit distance of 1 since the difference of the positions of `fox` and
     * `quick` is 2. Similarly, `"the fox is quick"` would be at an edit distance of 3
     * since the difference of the positions of `fox` and `quick` is -2. The slop defines
     * the maximum edit distance for a document to match.
     *
     *
     * More exact matches are scored higher than sloppier matches, thus search results are sorted
     * by exactness.
     */
    val slop: Int

    /** Returns the field this query applies to  */
    val field: String?
    private val terms: Array<Term>

    /** Returns the relative positions of terms in this phrase.  */
    val positions: IntArray

    /**
     * Create a phrase query which will match documents that contain the given list of terms at
     * consecutive positions in `field`, and at a maximum edit distance of `slop`. For
     * more complicated use-cases, use [PhraseQuery.Builder].
     *
     * @see .getSlop
     */
    constructor(slop: Int, field: String, vararg terms: String) : this(
        slop,
        Companion.toTerms(field, *terms),
        incrementalPositions(terms.size)
    )

    /**
     * Create a phrase query which will match documents that contain the given list of terms at
     * consecutive positions in `field`.
     */
    constructor(field: String, vararg terms: String) : this(0, field, *terms)

    /**
     * Create a phrase query which will match documents that contain the given list of terms at
     * consecutive positions in `field`, and at a maximum edit distance of `slop`. For
     * more complicated use-cases, use [PhraseQuery.Builder].
     *
     * @see .getSlop
     */
    constructor(slop: Int, field: String, vararg terms: BytesRef) : this(
        slop,
        toTerms(field, *terms),
        incrementalPositions(terms.size)
    )

    /**
     * Create a phrase query which will match documents that contain the given list of terms at
     * consecutive positions in `field`.
     */
    constructor(field: String, vararg terms: BytesRef) : this(0, field, *terms)

    /** Returns the list of terms in this phrase.  */
    fun getTerms(): Array<Term> {
        return terms
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        if (terms.size == 0) {
            return MatchNoDocsQuery("empty PhraseQuery")
        } else if (terms.size == 1) {
            return TermQuery(terms[0])
        } else if (positions[0] != 0) {
            val newPositions = IntArray(positions.size)
            for (i in positions.indices) {
                newPositions[i] = positions[i] - positions[0]
            }
            return PhraseQuery(slop, terms, newPositions)
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
        v.consumeTerms(this, *terms)
    }

    /**
     * Term postings and position information for phrase matching
     *
     * @lucene.internal
     */
    class PostingsAndFreq : Comparable<PostingsAndFreq> {
        val postings: PostingsEnum
        val impacts: ImpactsEnum
        val position: Int
        val terms: Array<out Term>?
        val nTerms: Int // for faster comparisons

        /** Creates PostingsAndFreq instance  */
        constructor(
            postings: PostingsEnum,
            impacts: ImpactsEnum,
            position: Int,
            vararg terms: Term
        ) {
            this.postings = postings
            this.impacts = impacts
            this.position = position
            nTerms = if (terms == null) 0 else terms.size
            if (nTerms > 0) {
                if (terms.size == 1) {
                    this.terms = terms
                } else {
                    val terms2: Array<Term> =
                        kotlin.arrayOfNulls<Term>(terms.size) as Array<Term>
                    System.arraycopy(terms as Array<Term>, 0, terms2, 0, terms.size)
                    Arrays.sort(terms2)
                    this.terms = terms2
                }
            } else {
                this.terms = null
            }
        }

        constructor(
            postings: PostingsEnum,
            impacts: ImpactsEnum,
            position: Int,
            terms: MutableList<Term>
        ) {
            this.postings = postings
            this.impacts = impacts
            this.position = position
            nTerms = if (terms == null) 0 else terms.size
            if (nTerms > 0) {
                val terms2: Array<Term> = terms.toTypedArray<Term>()
                if (nTerms > 1) {
                    Arrays.sort(terms2)
                }
                this.terms = terms2
            } else {
                this.terms = null
            }
        }

        override fun compareTo(other: PostingsAndFreq): Int {
            if (position != other.position) {
                return position - other.position
            }
            if (nTerms != other.nTerms) {
                return nTerms - other.nTerms
            }
            if (nTerms == 0) {
                return 0
            }
            for (i in terms!!.indices) {
                val res = terms[i].compareTo(other.terms!![i])
                if (res != 0) return res
            }
            return 0
        }

        override fun hashCode(): Int {
            val prime = 31
            var result = 1
            result = prime * result + position
            for (i in 0..<nTerms) {
                result = prime * result + terms!![i].hashCode()
            }
            return result
        }

        override fun equals(obj: Any?): Boolean {
            if (this === obj) return true
            if (obj == null) return false
            if (this::class != obj::class) return false
            val other = obj as PostingsAndFreq
            if (position != other.position) return false
            if (terms == null) return other.terms == null
            return terms.contentEquals(other.terms)
        }
    }

    init {
        require(terms.size == positions.size) { "Must have as many terms as positions" }
        require(slop >= 0) { "Slop must be >= 0, got $slop" }
        for (term in terms) {
            requireNotNull<Term>(term) { "Cannot add a null term to PhraseQuery" }
        }
        for (i in 1..<terms.size) {
            require(terms[i - 1].field() == terms[i].field() != false) { "All terms should have the same field" }
        }
        for (position in positions) {
            require(position >= 0) { "Positions must be >= 0, got $position" }
        }
        for (i in 1..<positions.size) {
            require(positions[i] >= positions[i - 1]) {
                ("Positions should not go backwards, got "
                        + positions[i - 1]
                        + " before "
                        + positions[i])
            }
        }
        this.slop = slop
        this.terms = terms
        this.positions = positions
        this.field = if (terms.size == 0) null else terms[0].field()
    }

    override fun createWeight(
        searcher: IndexSearcher,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        return object : PhraseWeight(this, field!!, searcher, scoreMode) {
            @Transient
            private lateinit var states: Array<TermStates>

            @Throws(IOException::class)
            override fun getStats(searcher: IndexSearcher): SimScorer? {
                val positions = this@PhraseQuery.positions
                check(positions.size >= 2) { "PhraseWeight does not support less than 2 terms, call rewrite first" }
                check(positions[0] == 0) { "PhraseWeight requires that the first position is 0, call rewrite first" }
                states = kotlin.arrayOfNulls<TermStates>(terms.size) as Array<TermStates>
                val termStats: Array<TermStatistics> =
                    kotlin.arrayOfNulls<TermStatistics>(terms.size) as Array<TermStatistics>
                var termUpTo = 0
                for (i in terms.indices) {
                    val term: Term = terms[i]
                    states[i] = TermStates.build(searcher, term, scoreMode.needsScores())
                    if (scoreMode.needsScores()) {
                        val ts: TermStates = states[i]
                        if (ts.docFreq() > 0) {
                            termStats[termUpTo++] =
                                searcher.termStatistics(term, ts.docFreq(), ts.totalTermFreq())!!
                        }
                    }
                }
                if (termUpTo > 0) {
                    return similarity.scorer(
                        boost,
                        searcher.collectionStatistics(field)!!,
                        *ArrayUtil.copyOfSubArray<TermStatistics>(
                            termStats,
                            0,
                            termUpTo
                        )
                    )
                } else {
                    return null // no terms at all, we won't use similarity
                }
            }

            @Throws(IOException::class)
            override fun getPhraseMatcher(
                context: LeafReaderContext,
                scorer: SimScorer,
                exposeOffsets: Boolean
            ): PhraseMatcher? {
                assert(terms.size > 0)
                val reader: LeafReader = context.reader()
                val postingsFreqs = kotlin.arrayOfNulls<PostingsAndFreq>(terms.size) as Array<PostingsAndFreq>

                val fieldTerms: Terms? = reader.terms(field)
                if (fieldTerms == null) {
                    return null
                }

                check(fieldTerms.hasPositions()) {
                    ("field \""
                            + field
                            + "\" was indexed without position data; cannot run PhraseQuery (phrase="
                            + query
                            + ")")
                }

                // Reuse single TermsEnum below:
                val te: TermsEnum = fieldTerms.iterator()
                var totalMatchCost = 0f

                for (i in terms.indices) {
                    val t: Term = terms[i]
                    val supplier: IOSupplier<TermState?>? =
                        states[i].get(context)
                    val state: TermState? = supplier?.get()
                    if (state == null) {
                        /* term doesnt exist in this segment */
                        assert(termNotInReader(reader, t)) { "no termstate found but term exists in reader" }
                        return null
                    }
                    te.seekExact(t.bytes(), state)
                    val postingsEnum: PostingsEnum
                    val impactsEnum: ImpactsEnum
                    if (scoreMode == ScoreMode.TOP_SCORES) {
                        impactsEnum =
                            te.impacts((if (exposeOffsets) PostingsEnum.OFFSETS else PostingsEnum.POSITIONS).toInt())
                        postingsEnum = impactsEnum
                    } else {
                        postingsEnum =
                            te.postings(
                                null,
                                (if (exposeOffsets) PostingsEnum.OFFSETS else PostingsEnum.POSITIONS).toInt()
                            )
                        impactsEnum = SlowImpactsEnum(postingsEnum)
                    }
                    postingsFreqs[i] = PostingsAndFreq(postingsEnum, impactsEnum, positions[i], t)
                    totalMatchCost += termPositionsCost(te)
                }

                // sort by increasing docFreq order
                if (slop == 0) {
                    ArrayUtil.timSort<PostingsAndFreq>(postingsFreqs)
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
        if (field != null && field != f) {
            buffer.append(field)
            buffer.append(":")
        }

        buffer.append("\"")
        val maxPosition: Int
        if (positions.size == 0) {
            maxPosition = -1
        } else {
            maxPosition = positions[positions.size - 1]
        }
        val pieces = kotlin.arrayOfNulls<String>(maxPosition + 1)
        for (i in terms.indices) {
            val pos = positions[i]
            var s = pieces[pos]
            if (s == null) {
                s = (terms[i]).text()
            } else {
                s = s + "|" + (terms[i]).text()
            }
            pieces[pos] = s
        }
        for (i in pieces.indices) {
            if (i > 0) {
                buffer.append(' ')
            }
            val s = pieces[i]
            if (s == null) {
                buffer.append("")
            } else {
                buffer.append(s)
            }
        }
        buffer.append("\"")

        if (slop != 0) {
            buffer.append("~")
            buffer.append(slop)
        }

        return buffer.toString()
    }

    /** Returns true iff `o` is equal to this.  */
    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(this::class.cast(other))
    }

    private fun equalsTo(other: PhraseQuery): Boolean {
        return slop == other.slop && terms.contentEquals(other.terms) && positions.contentEquals(other.positions)
    }

    /** Returns a hash code value for this object.  */
    override fun hashCode(): Int {
        var h: Int = classHash()
        h = 31 * h + slop
        h = 31 * h + terms.contentHashCode()
        h = 31 * h + positions.contentHashCode()
        return h
    }

    companion object {
        private fun incrementalPositions(length: Int): IntArray {
            val positions = IntArray(length)
            for (i in 0..<length) {
                positions[i] = i
            }
            return positions
        }

        private fun toTerms(field: String, vararg termStrings: String): Array<Term> {
            val terms: Array<Term> =
                kotlin.arrayOfNulls<Term>(termStrings.size) as Array<Term>
            for (i in terms.indices) {
                requireNotNull<String>(termStrings[i]) { "Cannot add a null term to PhraseQuery" }
                terms[i] = Term(field, termStrings[i])
            }
            return terms
        }

        private fun toTerms(
            field: String,
            vararg termBytes: BytesRef
        ): Array<Term> {
            val terms: Array<Term> =
                kotlin.arrayOfNulls<Term>(termBytes.size) as Array<Term>
            for (i in terms.indices) {
                requireNotNull<BytesRef>(termBytes[i]) { "Cannot add a null term to PhraseQuery" }
                terms[i] = Term(field, termBytes[i])
            }
            return terms
        }

        /**
         * A guess of the average number of simple operations for the initial seek and buffer refill per
         * document for the positions of a term. See also [ ][Lucene101PostingsReader.BlockPostingsEnum.nextPosition].
         *
         *
         * Aside: Instead of being constant this could depend among others on [ ][Lucene101PostingsFormat.BLOCK_SIZE], [TermsEnum.docFreq], [ ][TermsEnum.totalTermFreq], [DocIdSetIterator.cost] (expected number of matching docs),
         * [LeafReader.maxDoc] (total number of docs in the segment), and the seek time and block
         * size of the device storing the index.
         */
        private const val TERM_POSNS_SEEK_OPS_PER_DOC = 128

        /**
         * Number of simple operations in [Lucene101PostingsReader.BlockPostingsEnum.nextPosition]
         * when no seek or buffer refill is done.
         */
        private const val TERM_OPS_PER_POS = 7

        /**
         * Returns an expected cost in simple operations of processing the occurrences of a term in a
         * document that contains the term. This is for use by [TwoPhaseIterator.matchCost]
         * implementations.
         *
         * @param termsEnum The term is the term at which this TermsEnum is positioned.
         */
        @Throws(IOException::class)
        fun termPositionsCost(termsEnum: TermsEnum): Float {
            val docFreq: Int = termsEnum.docFreq()
            assert(docFreq > 0)
            val totalTermFreq: Long = termsEnum.totalTermFreq()
            val expOccurrencesInMatchingDoc = totalTermFreq / docFreq.toFloat()
            return TERM_POSNS_SEEK_OPS_PER_DOC + expOccurrencesInMatchingDoc * TERM_OPS_PER_POS
        }

        // only called from assert
        @Throws(IOException::class)
        private fun termNotInReader(
            reader: LeafReader,
            term: Term
        ): Boolean {
            return reader.docFreq(term) == 0
        }
    }
}
