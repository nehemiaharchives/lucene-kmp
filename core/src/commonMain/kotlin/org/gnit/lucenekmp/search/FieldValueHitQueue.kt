package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.PriorityQueue

/**
 * Expert: A hit queue for sorting by hits by terms in more than one field.
 *
 * @lucene.experimental
 * @since 2.9
 * @see IndexSearcher.search
 */
abstract class FieldValueHitQueue<T : FieldValueHitQueue.Entry>
private constructor(
    /** Stores the sort criteria being used.  */
    // When we get here, fields.length is guaranteed to be > 0, therefore no
    // need to check it again.

    // All these are required by this class's API - need to return arrays.
    // Therefore, even in the case of a single comparator, create an array
    // anyway.
    protected val fields: Array<SortField>, size: Int) :
    PriorityQueue<T>(size) {
    /** Extension of ScoreDoc to also store the [FieldComparator] slot.  */
    open class Entry(var slot: Int, doc: Int) : ScoreDoc(doc, Float.NaN) {
        override fun toString(): String {
            return "slot:" + slot + " " + super.toString()
        }
    }

    /**
     * An implementation of [FieldValueHitQueue] which is optimized in case there is just one
     * comparator.
     */
    private class OneComparatorFieldValueHitQueue<T : Entry>(fields: Array<SortField>, size: Int) : FieldValueHitQueue<T>(fields, size) {
        private val oneReverseMul: Int
        private val oneComparator: FieldComparator<*>

        init {
            assert(fields.size == 1)
            oneComparator = comparators[0]
            oneReverseMul = reverseMul[0]
        }

        /**
         * Returns whether `hitA` is less relevant than `hitB`.
         *
         * @param hitA Entry
         * @param hitB Entry
         * @return `true` if document `hitA` should be sorted after document
         * `hitB`.
         */
        override fun lessThan(hitA: T, hitB: T): Boolean {
            assert(hitA !== hitB)
            assert(hitA.slot != hitB.slot)

            val c: Int = oneReverseMul * oneComparator.compare(hitA.slot, hitB.slot)
            if (c != 0) {
                return c > 0
            }

            // avoid random sort order that could lead to duplicates (bug #31241):
            return hitA.doc > hitB.doc
        }
    }

    /**
     * An implementation of [FieldValueHitQueue] which is optimized in case there is more than
     * one comparator.
     */
    private class MultiComparatorsFieldValueHitQueue<T : Entry>(fields: Array<SortField>, size: Int) : FieldValueHitQueue<T>(fields, size) {
        override fun lessThan(hitA: T, hitB: T): Boolean {
            assert(hitA !== hitB)
            assert(hitA.slot != hitB.slot)

            val numComparators = comparators.size
            for (i in 0..<numComparators) {
                val c: Int = reverseMul[i] * comparators[i].compare(hitA.slot, hitB.slot)
                if (c != 0) {
                    // Short circuit
                    return c > 0
                }
            }

            // avoid random sort order that could lead to duplicates (bug #31241):
            return hitA.doc > hitB.doc
        }
    }

    /*fun getComparators(): Array<FieldComparator<*>> {
        return comparators
    }*/

    @Throws(IOException::class)
    fun getComparators(context: LeafReaderContext): Array<LeafFieldComparator> {
        val comparators: Array<LeafFieldComparator> =
            kotlin.arrayOfNulls<LeafFieldComparator>(this.comparators.size) as Array<LeafFieldComparator>
        for (i in comparators.indices) {
            comparators[i] = this.comparators[i].getLeafComparator(context)
        }
        return comparators
    }

    protected val comparators: Array<FieldComparator<*>>
    val reverseMul: IntArray

    // prevent instantiation and extension.
    init {
        val numComparators = fields.size
        comparators = kotlin.arrayOfNulls<FieldComparator<*>>(numComparators) as Array<FieldComparator<*>>
        reverseMul = IntArray(numComparators)
        for (i in 0..<numComparators) {
            val field: SortField = fields[i]
            reverseMul[i] = if (field.reverse) -1 else 1
            comparators[i] =
                field.getComparator(
                    size,
                    if (i == 0)
                        (if (numComparators > 1) Pruning.GREATER_THAN else Pruning.GREATER_THAN_OR_EQUAL_TO)
                    else
                        Pruning.NONE
                )
        }
    }

    abstract override fun lessThan(a: T, b: T): Boolean

    /**
     * Given a queue Entry, creates a corresponding FieldDoc that contains the values used to sort the
     * given document. These values are not the raw values out of the index, but the internal
     * representation of them. This is so the given search hit can be collated by a MultiSearcher with
     * other search hits.
     *
     * @param entry The Entry used to create a FieldDoc
     * @return The newly created FieldDoc
     * @see IndexSearcher.search
     */
    fun fillFields(entry: Entry): FieldDoc {
        val n = comparators.size
        val fields = kotlin.arrayOfNulls<Any>(n)
        for (i in 0..<n) {
            fields[i] = comparators[i].value(entry.slot)
        }
        // if (maxscore > 1.0f) doc.score /= maxscore;   // normalize scores
        return FieldDoc(entry.doc, entry.score, fields)
    }

    /** Returns the SortFields being used by this hit queue.  */
    /*fun getFields(): Array<SortField> {
        return fields
    }*/

    companion object {
        /**
         * Creates a hit queue sorted by the given list of fields.
         *
         *
         * **NOTE**: The instances returned by this method pre-allocate a full array of length
         * `numHits`.
         *
         * @param fields SortField array we are sorting by in priority order (highest priority first);
         * cannot be `null` or empty
         * @param size The number of hits to retain. Must be greater than zero.
         */
        fun <T : Entry> create(
            fields: Array<SortField>, size: Int
        ): FieldValueHitQueue<T> {
            require(fields.isNotEmpty()) { "Sort must contain at least one field" }

            return if (fields.size == 1) {
                OneComparatorFieldValueHitQueue(fields, size)
            } else {
                MultiComparatorsFieldValueHitQueue(fields, size)
            }
        }
    }
}
