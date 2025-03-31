package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton.AUTOMATON_TYPE


/**
 * A per-document byte[] with presorted values. This is fundamentally an iterator over the int ord
 * values per document, with random access APIs to resolve an int ord to BytesRef.
 *
 *
 * Per-Document values in a SortedDocValues are deduplicated, dereferenced, and sorted into a
 * dictionary of unique values. A pointer to the dictionary value (ordinal) can be retrieved for
 * each document. Ordinals are dense and in increasing sorted order.
 */
abstract class SortedDocValues
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : DocValuesIterator() {
    /**
     * Returns the ordinal for the current docID. It is illegal to call this method after [ ][.advanceExact] returned `false`.
     *
     * @return ordinal for the document: this is dense, starts at 0, then increments by 1 for the next
     * value in sorted order.
     */
    @Throws(IOException::class)
    abstract fun ordValue(): Int

    /**
     * Retrieves the value for the specified ordinal. The returned [BytesRef] may be re-used
     * across calls to [.lookupOrd] so make sure to [ copy it][BytesRef.deepCopyOf] if you want to keep it around.
     *
     * @param ord ordinal to lookup (must be &gt;= 0 and &lt; [.getValueCount])
     * @see .ordValue
     */
    @Throws(IOException::class)
    abstract fun lookupOrd(ord: Int): BytesRef

    /**
     * Returns the number of unique values.
     *
     * @return number of unique values in this SortedDocValues. This is also equivalent to one plus
     * the maximum ordinal.
     */
    abstract val valueCount: Int

    /**
     * If `key` exists, returns its ordinal, else returns `-insertionPoint-1`, like `Arrays.binarySearch`.
     *
     * @param key Key to look up
     */
    @Throws(IOException::class)
    fun lookupTerm(key: BytesRef): Int {
        var low = 0
        var high = this.valueCount - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val term: BytesRef = lookupOrd(mid)
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
    open fun termsEnum(): TermsEnum? {
        return SortedDocValuesTermsEnum(this)
    }

    /**
     * Returns a [TermsEnum] over the values, filtered by a [CompiledAutomaton] The enum
     * supports [TermsEnum.ord].
     */
    @Throws(IOException::class)
    fun intersect(automaton: CompiledAutomaton): TermsEnum? {
        val `in` = termsEnum()
        when (automaton.type) {
            AUTOMATON_TYPE.NONE -> return TermsEnum.EMPTY
            AUTOMATON_TYPE.ALL -> return `in`
            AUTOMATON_TYPE.SINGLE -> return SingleTermsEnum(`in`!!, automaton.term)
            AUTOMATON_TYPE.NORMAL -> return AutomatonTermsEnum(`in`, automaton)
            else ->         // unreachable
                throw RuntimeException("unhandled case")
        }
    }
}
