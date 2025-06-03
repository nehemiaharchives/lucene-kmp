package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntroSorter
import org.gnit.lucenekmp.util.PriorityQueue
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PagedMutable

/**
 * Holds updates of a single DocValues field, for a set of documents within one segment.
 *
 * @lucene.experimental
 */
internal abstract class DocValuesFieldUpdates protected constructor(
    protected val maxDoc: Int,
    val delGen: Long,
    val field: String,
    type: DocValuesType
) : Accountable {
    /**
     * An iterator over documents and their updated values. Only documents with updates are returned
     * by this iterator, and the documents are returned in increasing order.
     */
    internal abstract class Iterator : DocValuesIterator() {
        override fun advanceExact(target: Int): Boolean {
            throw UnsupportedOperationException()
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException()
        }

        override fun cost(): Long {
            throw UnsupportedOperationException()
        }

        abstract override fun nextDoc(): Int // no IOException

        /** Returns a long value for the current document if this iterator is a long iterator.  */
        abstract fun longValue(): Long

        /**
         * Returns a binary value for the current document if this iterator is a binary value iterator.
         */
        abstract fun binaryValue(): BytesRef

        /** Returns delGen for this packet.  */
        abstract fun delGen(): Long

        /** Returns true if this doc has a value  */
        abstract fun hasValue(): Boolean

        companion object {
            /** Wraps the given iterator as a BinaryDocValues instance.  */
            fun asBinaryDocValues(iterator: Iterator): BinaryDocValues {
                return object : BinaryDocValues() {
                    override fun docID(): Int {
                        return iterator.docID()
                    }

                    override fun binaryValue(): BytesRef {
                        return iterator.binaryValue()
                    }

                    override fun advanceExact(target: Int): Boolean {
                        return iterator.advanceExact(target)
                    }

                    override fun nextDoc(): Int {
                        return iterator.nextDoc()
                    }

                    override fun advance(target: Int): Int {
                        return iterator.advance(target)
                    }

                    override fun cost(): Long {
                        return iterator.cost()
                    }
                }
            }

            /** Wraps the given iterator as a NumericDocValues instance.  */
            fun asNumericDocValues(iterator: Iterator): NumericDocValues {
                return object : NumericDocValues() {
                    override fun longValue(): Long {
                        return iterator.longValue()
                    }

                    override fun advanceExact(target: Int): Boolean {
                        throw UnsupportedOperationException()
                    }

                    override fun docID(): Int {
                        return iterator.docID()
                    }

                    override fun nextDoc(): Int {
                        return iterator.nextDoc()
                    }

                    override fun advance(target: Int): Int {
                        return iterator.advance(target)
                    }

                    override fun cost(): Long {
                        return iterator.cost()
                    }
                }
            }
        }
    }

    val type: DocValuesType
    private val bitsPerValue: Int
    var finished: Boolean = false
        private set
    protected var docs: PagedMutable
    protected var size: Int = 0

    init {
        if (type == null) {
            throw NullPointerException("DocValuesType must not be null")
        }
        this.type = type
        bitsPerValue = PackedInts.bitsRequired((maxDoc - 1).toLong()) + SHIFT
        docs = PagedMutable(
            1,
            PAGE_SIZE,
            bitsPerValue,
            PackedInts.DEFAULT
        )
    }

    abstract fun add(doc: Int, value: Long)

    abstract fun add(doc: Int, value: BytesRef)

    /**
     * Adds the value for the given docID. This method prevents conditional calls to [ ][Iterator.longValue] or [Iterator.binaryValue] since the implementation knows if it's
     * a long value iterator or binary value
     */
    abstract fun add(docId: Int, iterator: Iterator)

    /** Returns an [Iterator] over the updated documents and their values.  */ // TODO: also use this for merging, instead of having to write through to disk first
    abstract fun iterator(): Iterator

    /** Freezes internal data structures and sorts updates by docID for efficient iteration.  */
    /*@Synchronized*/
    fun finish() {
        check(!finished) { "already finished" }
        finished = true
        // shrink wrap
        if (size < docs.size()) {
            resize(size)
        }
        if (size > 0) {
            // We need a stable sort but InPlaceMergeSorter performs lots of swaps
            // which hurts performance due to all the packed ints we are using.
            // Another option would be TimSorter, but it needs additional API (copy to
            // temp storage, compare with item in temp storage, etc.) so we instead
            // use quicksort and record ords of each update to guarantee stability.
            val ords: PackedInts.Mutable =
                PackedInts.getMutable(
                    size,
                    PackedInts.bitsRequired((size - 1).toLong()),
                    PackedInts.DEFAULT
                )
            for (i in 0..<size) {
                ords.set(i, i.toLong())
            }
            object : IntroSorter() {
                override fun swap(i: Int, j: Int) {
                    val tmpOrd: Long = ords.get(i)
                    ords.set(i, ords.get(j))
                    ords.set(j, tmpOrd)

                    this@DocValuesFieldUpdates.swap(i, j)
                }

                override fun compare(i: Int, j: Int): Int {
                    // increasing docID order:
                    // NOTE: we can have ties here, when the same docID was updated in the same segment, in
                    // which case we rely on sort being
                    // stable and preserving original order so the last update to that docID wins
                    var cmp: Int = Long.compare(docs.get(i.toLong()) ushr 1, docs.get(j.toLong()) ushr 1)
                    if (cmp == 0) {
                        cmp = (ords.get(i) - ords.get(j)).toInt()
                    }
                    return cmp
                }

                var pivotDoc: Long = 0
                var pivotOrd: Int = 0

                override fun setPivot(i: Int) {
                    pivotDoc = docs.get(i.toLong()) ushr 1
                    pivotOrd = ords.get(i).toInt()
                }

                override fun comparePivot(j: Int): Int {
                    var cmp: Int = Long.compare(pivotDoc, docs.get(j.toLong()) ushr 1)
                    if (cmp == 0) {
                        cmp = pivotOrd - ords.get(j).toInt()
                    }
                    return cmp
                }
            }.sort(0, size)
        }
    }

    /** Returns true if this instance contains any updates.  */
    /*@Synchronized*/
    open fun any(): Boolean {
        return size > 0
    }

    /*@Synchronized*/
    fun size(): Int {
        return size
    }

    /**
     * Adds an update that resets the documents value.
     *
     * @param doc the doc to update
     */
    /*@Synchronized*/
    open fun reset(doc: Int) {
        addInternal(doc, HAS_NO_VALUE_MASK)
    }

    /*@Synchronized*/
    fun add(doc: Int): Int {
        return addInternal(doc, HAS_VALUE_MASK)
    }

    /*@Synchronized*/
    private fun addInternal(doc: Int, hasValueMask: Long): Int {
        check(!finished) { "already finished" }
        assert(doc < maxDoc)

        // TODO: if the Sorter interface changes to take long indexes, we can remove that limitation
        check(size != Int.Companion.MAX_VALUE) { "cannot support more than Integer.MAX_VALUE doc/value entries" }
        // grow the structures to have room for more elements
        if (docs.size() == size.toLong()) {
            grow(size + 1)
        }
        docs.set(size.toLong(), ((doc.toLong()) shl SHIFT) or hasValueMask)
        ++size
        return size - 1
    }

    protected open fun swap(i: Int, j: Int) {
        val tmpDoc: Long = docs.get(j.toLong())
        docs.set(j.toLong(), docs.get(i.toLong()))
        docs.set(i.toLong(), tmpDoc)
    }

    protected open fun grow(size: Int) {
        docs = docs.grow(size.toLong())
    }

    protected open fun resize(size: Int) {
        docs = docs.resize(size.toLong())
    }

    protected fun ensureFinished() {
        check(finished != false) { "call finish first" }
    }

    override fun ramBytesUsed(): Long {
        return (docs.ramBytesUsed()
                + RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                + 2 * Int.SIZE_BYTES + 2
                + Long.SIZE_BYTES
                + RamUsageEstimator.NUM_BYTES_OBJECT_REF)
    }

    // TODO: can't this just be NumericDocValues now  avoid boxing the long value...
    abstract class AbstractIterator(
        private val size: Int,
        private val docs: PagedMutable,
        private val delGen: Long
    ) : Iterator() {
        private var idx: Long = 0 // long so we don't overflow if size == Integer.MAX_VALUE
        private var doc = -1
        private var hasValue = false

        override fun nextDoc(): Int {
            if (idx >= size) {
                return NO_MORE_DOCS.also { doc = it }
            }
            var longDoc: Long = docs.get(idx)
            ++idx
            while (idx < size) {
                // scan forward to last update to this doc
                val nextLongDoc: Long = docs.get(idx)
                if ((longDoc ushr 1) != (nextLongDoc ushr 1)) {
                    break
                }
                longDoc = nextLongDoc
                idx++
            }
            hasValue = (longDoc and HAS_VALUE_MASK) > 0
            if (hasValue) {
                set(idx - 1)
            }
            doc = (longDoc shr SHIFT).toInt()
            return doc
        }

        /**
         * Called when the iterator moved to the next document
         *
         * @param idx the internal index to set the value to
         */
        protected abstract fun set(idx: Long)

        override fun docID(): Int {
            return doc
        }

        override fun delGen(): Long {
            return delGen
        }

        override fun hasValue(): Boolean {
            return hasValue
        }
    }

    companion object {
        protected const val PAGE_SIZE: Int = 1024
        private const val HAS_VALUE_MASK: Long = 1
        private const val HAS_NO_VALUE_MASK: Long = 0

        // we use the first bit of each value to mark if the doc has a value or not
        private const val SHIFT = 1

        /**
         * Merge-sorts multiple iterators, one per delGen, favoring the largest delGen that has updates
         * for a given docID.
         */
        fun mergedIterator(subs: Array<Iterator>): Iterator? {
            if (subs.size == 1) {
                return subs[0]
            }

            val queue: PriorityQueue<Iterator> =
                object : PriorityQueue<Iterator>(subs.size) {
                    override fun lessThan(a: Iterator, b: Iterator): Boolean {
                        // sort by smaller docID
                        var cmp: Int = Int.compare(a.docID(), b.docID())
                        if (cmp == 0) {
                            // then by larger delGen
                            cmp = Long.compare(b.delGen(), a.delGen())

                            // delGens are unique across our subs:
                            assert(cmp != 0)
                        }

                        return cmp < 0
                    }
                }

            for (sub in subs) {
                if (sub.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    queue.add(sub)
                }
            }

            if (queue.size() == 0) {
                return null
            }

            return object : Iterator() {
                private var doc = -1

                override fun nextDoc(): Int {
                    // Advance all sub iterators past current doc
                    while (true) {
                        if (queue.size() == 0) {
                            doc = NO_MORE_DOCS
                            break
                        }
                        val newDoc: Int = queue.top().docID()
                        if (newDoc != doc) {
                            assert(newDoc > doc) { "doc=$doc newDoc=$newDoc" }
                            doc = newDoc
                            break
                        }
                        if (queue.top().nextDoc() == NO_MORE_DOCS) {
                            queue.pop()
                        } else {
                            queue.updateTop()
                        }
                    }
                    return doc
                }

                override fun docID(): Int {
                    return doc
                }

                override fun longValue(): Long {
                    return queue.top().longValue()
                }

                override fun binaryValue(): BytesRef {
                    return queue.top().binaryValue()
                }

                override fun delGen(): Long {
                    throw UnsupportedOperationException()
                }

                override fun hasValue(): Boolean {
                    return queue.top().hasValue()
                }
            }
        }
    }
}
