package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton


/**
 * Access to the terms in a specific field. See [Fields].
 *
 * @lucene.experimental
 */
abstract class Terms
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() {
    /** Returns an iterator that will step through all terms. This method will not return null.  */
    @Throws(IOException::class)
    abstract fun iterator(): TermsEnum

    /**
     * Returns a TermsEnum that iterates over all terms and documents that are accepted by the
     * provided [CompiledAutomaton]. If the `startTerm` is provided then the returned
     * enum will only return terms `> startTerm`, but you still must call next() first to get to
     * the first term. Note that the provided `startTerm` must be accepted by the
     * automaton.
     *
     *
     * This is an expert low-level API and will only work for `NORMAL` compiled automata. To
     * handle any compiled automata you should instead use [CompiledAutomaton.getTermsEnum]
     * instead.
     *
     *
     * **NOTE**: the returned TermsEnum cannot seek.
     */
    @Throws(IOException::class)
    open fun intersect(compiled: CompiledAutomaton, startTerm: BytesRef?): TermsEnum {
        // TODO: could we factor out a common interface b/w
        // CompiledAutomaton and FST  Then we could pass FST there too,
        // and likely speed up resolving terms to deleted docs ... but
        // AutomatonTermsEnum makes this tricky because of its on-the-fly cycle
        // detection

        // TODO: eventually we could support seekCeil/Exact on
        // the returned enum, instead of only being able to seek
        // at the start

        val termsEnum: TermsEnum = iterator()

        require(compiled.type === CompiledAutomaton.AUTOMATON_TYPE.NORMAL) { "please use CompiledAutomaton.getTermsEnum instead" }

        if (startTerm == null) {
            return AutomatonTermsEnum(termsEnum, compiled)
        } else {
            return object : AutomatonTermsEnum(termsEnum, compiled) {
                @Throws(IOException::class)
                protected override fun nextSeekTerm(term: BytesRef?): BytesRef? {
                    var term: BytesRef? = term
                    if (term == null) {
                        term = startTerm
                    }
                    return super.nextSeekTerm(term)!!
                }
            }
        }
    }

    /**
     * Returns the number of terms for this field, or -1 if this measure isn't stored by the codec.
     * Note that, just like other term measures, this measure does not take deleted documents into
     * account.
     */
    @Throws(IOException::class)
    abstract fun size(): Long

    @Throws(IOException::class)
    abstract fun getSumTotalTermFreq(): Long

    @Throws(IOException::class)
    abstract fun getSumDocFreq(): Long

    @Throws(IOException::class)
    abstract fun getDocCount(): Int

    /**
     * Returns true if documents in this field store per-document term frequency ([ ][PostingsEnum.freq]).
     */
    abstract fun hasFreqs(): Boolean

    /** Returns true if documents in this field store offsets.  */
    abstract fun hasOffsets(): Boolean

    /** Returns true if documents in this field store positions.  */
    abstract fun hasPositions(): Boolean

    /** Returns true if documents in this field store payloads.  */
    abstract fun hasPayloads(): Boolean

    @get:Throws(IOException::class)
    open val min: BytesRef?
        /**
         * Returns the smallest term (in lexicographic order) in the field. Note that, just like other
         * term measures, this measure does not take deleted documents into account. This returns null
         * when there are no terms.
         */
        get() = iterator().next()!!

    fun getMin() = min

    @get:Throws(IOException::class)
    open val max: BytesRef?
        /**
         * Returns the largest term (in lexicographic order) in the field. Note that, just like other term
         * measures, this measure does not take deleted documents into account. This returns null when
         * there are no terms.
         */
        get() {
            val size = size()

            if (size == 0L) {
                // empty: only possible from a FilteredTermsEnum...
                return null
            } else if (size >= 0) {
                // try to seek-by-ord
                try {
                    val iterator: TermsEnum = iterator()
                    iterator.seekExact(size - 1)
                    return iterator.term()
                } catch (ignored: UnsupportedOperationException) {
                    // ok
                }
            }

            // otherwise: binary search
            val iterator: TermsEnum = iterator()
            val v: BytesRef? = iterator.next()
            if (v == null) {
                // empty: only possible from a FilteredTermsEnum...
                return v
            }

            val scratch = BytesRefBuilder()
            scratch.append(0.toByte())

            // Iterates over digits:
            while (true) {
                var low = 0
                var high = 256

                // Binary search current digit to find the highest
                // digit before END:
                while (low != high) {
                    val mid = (low + high) ushr 1
                    scratch.setByteAt(scratch.length() - 1, mid.toByte())
                    if (iterator.seekCeil(scratch.get()) === TermsEnum.SeekStatus.END) {
                        // Scratch was too high
                        if (mid == 0) {
                            scratch.setLength(scratch.length() - 1)
                            return scratch.get()
                        }
                        high = mid
                    } else {
                        // Scratch was too low; there is at least one term
                        // still after it:
                        if (low == mid) {
                            break
                        }
                        low = mid
                    }
                }

                // Recurse to next digit:
                scratch.setLength(scratch.length() + 1)
                scratch.grow(scratch.length())
            }
        }

    fun getMax() = max

    /** Expert: returns additional information about this Terms instance for debugging purposes.  */
    open fun getStats(): Any
         {
            val sb = StringBuilder()
            sb.append("impl=").append(this::class.simpleName)
            sb.append(",size=").append(size())
            sb.append(",docCount=").append(this.getDocCount())
            sb.append(",sumTotalTermFreq=").append(this.getSumTotalTermFreq())
            sb.append(",sumDocFreq=").append(this.getSumDocFreq())
            return sb.toString()
        }

    companion object {
        /**
         * Returns the [Terms] index for this field, or [.EMPTY] if it has none.
         *
         * @return terms instance, or an empty instance if `field` does not exist in this reader
         * @throws IOException if an I/O error occurs.
         */
        @Throws(IOException::class)
        fun getTerms(reader: LeafReader, field: String): Terms {
            val terms = reader.terms(field)
            if (terms == null) {
                return EMPTY
            }
            return terms
        }

        /** Zero-length array of [Terms].  */
        val EMPTY_ARRAY: Array<Terms> = emptyArray()

        /** An empty [Terms] which returns no terms  */
        private val EMPTY: Terms = object : Terms() {
            @Throws(IOException::class)
            override fun iterator(): TermsEnum {
                return TermsEnum.EMPTY
            }

            @Throws(IOException::class)
            override fun size(): Long {
                return 0
            }

            @Throws(IOException::class)
            override fun getSumTotalTermFreq(): Long {
                return 0
            }

            @Throws(IOException::class)
            override fun getSumDocFreq(): Long {
                return 0
            }

            @Throws(IOException::class)
            override fun getDocCount(): Int {
                return 0
            }

            override fun hasFreqs(): Boolean {
                return false
            }

            override fun hasOffsets(): Boolean {
                return false
            }

            override fun hasPositions(): Boolean {
                return false
            }

            override fun hasPayloads(): Boolean {
                return false
            }
        }
    }
}
