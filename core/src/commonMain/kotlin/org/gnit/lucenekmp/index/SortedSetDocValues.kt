package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton.AUTOMATON_TYPE.*

/**
 * A multi-valued version of [SortedDocValues].
 *
 *
 * Per-Document values in a SortedSetDocValues are deduplicated, dereferenced, and sorted into a
 * dictionary of unique values. A pointer to the dictionary value (ordinal) can be retrieved for
 * each document. Ordinals are dense and in increasing sorted order.
 */
abstract class SortedSetDocValues
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : DocValuesIterator() {
    /**
     * Returns the next ordinal for the current document. It is illegal to call this method after
     * [.advanceExact] returned `false`. It is illegal to call this more than [ ][.docValueCount] times for the currently-positioned doc.
     *
     * @return next ordinal for the document. ordinals are dense, start at 0, then increment by 1 for
     * the next value in sorted order.
     */
    @Throws(IOException::class)
    abstract fun nextOrd(): Long

    /**
     * Retrieves the number of unique ords for the current document. This must always be greater than
     * zero. It is illegal to call this method after [.advanceExact] returned `false`.
     */
    abstract fun docValueCount(): Int

    /**
     * Retrieves the value for the specified ordinal. The returned [BytesRef] may be re-used
     * across calls to lookupOrd so make sure to [copy it][BytesRef.deepCopyOf] if you
     * want to keep it around.
     *
     * @param ord ordinal to lookup
     * @see .nextOrd
     */
    @Throws(IOException::class)
    abstract fun lookupOrd(ord: Long): BytesRef?

    /**
     * Returns the number of unique values.
     *
     * @return number of unique values in this SortedDocValues. This is also equivalent to one plus
     * the maximum ordinal.
     */
    abstract val valueCount: Long

    /**
     * If `key` exists, returns its ordinal, else returns `-insertionPoint-1`, like `Arrays.binarySearch`.
     *
     * @param key Key to look up
     */
    @Throws(IOException::class)
    open fun lookupTerm(key: BytesRef): Long {
        var low: Long = 0
        var high = this.valueCount - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val term: BytesRef = lookupOrd(mid)!!
            val cmp = term.compareTo(key)

            if (cmp < 0) {
                low = mid + 1
            } else if (cmp > 0) {
                high = mid - 1
            } else {
                return mid // key found
            }
        }

        return -(low + 1) // key not found.
    }

    /**
     * Returns a [TermsEnum] over the values. The enum supports [TermsEnum.ord] and
     * [TermsEnum.seekExact].
     */
    @Throws(IOException::class)
    open fun termsEnum(): TermsEnum {
        return SortedSetDocValuesTermsEnum(this)
    }

    /**
     * Returns a [TermsEnum] over the values, filtered by a [CompiledAutomaton] The enum
     * supports [TermsEnum.ord].
     */
    @Throws(IOException::class)
    open fun intersect(automaton: CompiledAutomaton): TermsEnum {
        val `in` = termsEnum()
        when (automaton.type) {
            NONE -> return TermsEnum.EMPTY
            ALL -> return `in`
            SINGLE -> return SingleTermsEnum(`in`!!, automaton.term)
            NORMAL -> return AutomatonTermsEnum(`in`, automaton)
            else ->         // unreachable
                throw RuntimeException("unhandled case")
        }
    }
}
