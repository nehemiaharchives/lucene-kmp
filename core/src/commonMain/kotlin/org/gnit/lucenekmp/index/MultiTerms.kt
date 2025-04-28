package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton

/**
 * Exposes flex API, merged from flex API of sub-segments.
 *
 * @lucene.experimental
 */
class MultiTerms(
    /** Expert: returns the Terms being merged.  */
    val subTerms: Array<Terms>,
    /** Expert: returns pointers to the sub-readers corresponding to the Terms being merged.  */
    val subSlices: Array<ReaderSlice>
) : Terms() {
    private val hasFreqs: Boolean
    private val hasOffsets: Boolean
    private val hasPositions: Boolean
    private val hasPayloads: Boolean

    /**
     * Sole constructor. Use [.getTerms] instead if possible.
     *
     * @param subTerms The [Terms] instances of all sub-readers.
     * @param subSlices A parallel array (matching `subs`) describing the sub-reader slices.
     * @lucene.internal
     */
    init { // TODO make private?

        require(subTerms.size > 0) { "inefficient: don't use MultiTerms over one sub" }
        var _hasFreqs = true
        var _hasOffsets = true
        var _hasPositions = true
        var _hasPayloads = false
        for (i in subTerms.indices) {
            _hasFreqs = _hasFreqs and subTerms[i].hasFreqs()
            _hasOffsets = _hasOffsets and subTerms[i].hasOffsets()
            _hasPositions = _hasPositions and subTerms[i].hasPositions()
            _hasPayloads = _hasPayloads or subTerms[i].hasPayloads()
        }

        hasFreqs = _hasFreqs
        hasOffsets = _hasOffsets
        hasPositions = _hasPositions
        hasPayloads =
            hasPositions && _hasPayloads // if all subs have pos, and at least one has payloads.
    }

    @Throws(IOException::class)
    override fun intersect(compiled: CompiledAutomaton, startTerm: BytesRef?): TermsEnum {
        val termsEnums: MutableList<TermsEnumIndex> = mutableListOf<TermsEnumIndex>()
        for (i in subTerms.indices) {
            val termsEnum: TermsEnum? = this.subTerms[i].intersect(compiled, startTerm)
            if (termsEnum != null) {
                termsEnums.add(TermsEnumIndex(termsEnum, i))
            }
        }

        if (termsEnums.isNotEmpty()) {
            return MultiTermsEnum(subSlices).reset(termsEnums.toTypedArray())
        } else {
            return TermsEnum.EMPTY
        }
    }

    @get:Throws(IOException::class)
    override val min: BytesRef?
        get() {
            var minTerm: BytesRef? = null
            for (terms in this.subTerms) {
                val term: BytesRef? = terms.min
                if (minTerm == null || term!! < minTerm) {
                    minTerm = term
                }
            }

            return minTerm
        }

    @get:Throws(IOException::class)
    override val max: BytesRef?
        get() {
            var maxTerm: BytesRef? = null
            for (terms in this.subTerms) {
                val term: BytesRef? = terms.max
                if (maxTerm == null || term!! > maxTerm) {
                    maxTerm = term
                }
            }

            return maxTerm
        }

    @Throws(IOException::class)
    override fun iterator(): TermsEnum {
        val termsEnums: MutableList<TermsEnumIndex> = mutableListOf<TermsEnumIndex>()
        for (i in subTerms.indices) {
            val termsEnum: TermsEnum? = this.subTerms[i].iterator()
            if (termsEnum != null) {
                termsEnums.add(TermsEnumIndex(termsEnum, i))
            }
        }

        if (termsEnums.isNotEmpty()) {
            return MultiTermsEnum(subSlices).reset(termsEnums.toTypedArray())
        } else {
            return TermsEnum.EMPTY
        }
    }

    override fun size(): Long {
        return -1
    }

    @get:Throws(IOException::class)
    override val sumTotalTermFreq: Long
        get() {
            var sum: Long = 0
            for (terms in this.subTerms) {
                val v = terms.sumTotalTermFreq
                require(v != -1L)
                sum += v
            }
            return sum
        }

    @get:Throws(IOException::class)
    override val sumDocFreq: Long
        get() {
            var sum: Long = 0
            for (terms in this.subTerms) {
                val v = terms.sumDocFreq
                require(v != -1L)
                sum += v
            }
            return sum
        }

    @get:Throws(IOException::class)
    override val docCount: Int
        get(){
        var sum = 0
        for (terms in this.subTerms) {
            val v = terms.docCount
            require(v != -1)
            sum += v
        }
        return sum
    }

    override fun hasFreqs(): Boolean {
        return hasFreqs
    }

    override fun hasOffsets(): Boolean {
        return hasOffsets
    }

    override fun hasPositions(): Boolean {
        return hasPositions
    }

    override fun hasPayloads(): Boolean {
        return hasPayloads
    }

    companion object {
        /** This method may return null if the field does not exist or if it has no terms.  */
        @Throws(IOException::class)
        fun getTerms(r: IndexReader, field: String?): Terms? {
            val leaves = r.leaves()
            if (leaves.size == 1) {
                return leaves.get(0).reader().terms(field!!)
            }

            val termsPerLeaf: MutableList<Terms> = mutableListOf<Terms>()
            val slicePerLeaf: MutableList<ReaderSlice> = mutableListOf<ReaderSlice>()

            for (leafIdx in leaves.indices) {
                val ctx = leaves.get(leafIdx)
                val subTerms = ctx.reader().terms(field!!)
                if (subTerms != null) {
                    termsPerLeaf.add(subTerms)
                    slicePerLeaf.add(ReaderSlice(ctx.docBase, r.maxDoc(), leafIdx))
                }
            }

            if (termsPerLeaf.isEmpty()) {
                return null
            } else {
                return MultiTerms(
                    termsPerLeaf.toTypedArray(), slicePerLeaf.toTypedArray()
                )
            }
        }

        /**
         * Returns [PostingsEnum] for the specified field and term. This will return null if the
         * field or term does not exist or positions were not indexed.
         *
         * @see .getTermPostingsEnum
         */
        @Throws(IOException::class)
        fun getTermPostingsEnum(r: IndexReader, field: String, term: BytesRef): PostingsEnum? {
            return Companion.getTermPostingsEnum(r, field, term, PostingsEnum.ALL.toInt())
        }

        /**
         * Returns [PostingsEnum] for the specified field and term, with control over whether freqs,
         * positions, offsets or payloads are required. Some codecs may be able to optimize their
         * implementation when offsets and/or payloads are not required. This will return null if the
         * field or term does not exist. See [TermsEnum.postings].
         */
        @Throws(IOException::class)
        fun getTermPostingsEnum(
            r: IndexReader, field: String, term: BytesRef, flags: Int
        ): PostingsEnum? {
            checkNotNull(field)
            checkNotNull(term)
            val terms = getTerms(r, field)
            if (terms != null) {
                val termsEnum = terms.iterator()
                if (termsEnum.seekExact(term)) {
                    return termsEnum.postings(null, flags)
                }
            }
            return null
        }
    }
}
