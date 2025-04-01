package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.util.ByteBlockPool.DirectTrackingAllocator


/**
 * A simple append only random-access [BytesRef] array that stores full copies of the appended
 * bytes in a [ByteBlockPool].
 *
 *
 * **Note: This class is not Thread-Safe!**
 *
 * @lucene.internal
 * @lucene.experimental
 */
class BytesRefArray(bytesUsed: Counter) : SortableBytesRefArray {
    private val pool: ByteBlockPool = ByteBlockPool(DirectTrackingAllocator(bytesUsed))
    private var offsets = IntArray(1)
    private var lastElement = 0
    private var currentOffset = 0
    private val bytesUsed: Counter

    /** Creates a new [BytesRefArray] with a counter to track allocated bytes  */
    init {
        pool.nextBuffer()
        bytesUsed.addAndGet(RamUsageEstimator.NUM_BYTES_ARRAY_HEADER * Int.SIZE_BYTES.toLong())
        this.bytesUsed = bytesUsed
    }

    /** Clears this [BytesRefArray]  */
    override fun clear() {
        lastElement = 0
        currentOffset = 0
        // TODO: it's trappy that this does not return storage held by int[] offsets array!
        Arrays.fill(offsets, 0)
        pool.reset(false, true) // no need to 0 fill the buffers we control the allocator
    }

    /**
     * Appends a copy of the given [BytesRef] to this [BytesRefArray].
     *
     * @param bytes the bytes to append
     * @return the index of the appended bytes
     */
    override fun append(bytes: BytesRef): Int {
        if (lastElement >= offsets.size) {
            val oldLen = offsets.size
            offsets = ArrayUtil.grow(offsets, offsets.size + 1)
            bytesUsed.addAndGet((offsets.size - oldLen) * Int.SIZE_BYTES.toLong())
        }
        pool.append(bytes)
        offsets[lastElement++] = currentOffset
        currentOffset += bytes.length
        return lastElement - 1
    }

    /**
     * Returns the current size of this [BytesRefArray]
     *
     * @return the current size of this [BytesRefArray]
     */
    override fun size(): Int {
        return lastElement
    }

    /**
     * Returns the *n'th* element of this [BytesRefArray]
     *
     * @param spare a spare [BytesRef] instance
     * @param index the elements index to retrieve
     * @return the *n'th* element of this [BytesRefArray]
     */
    fun get(spare: BytesRefBuilder, index: Int): BytesRef {
        Objects.checkIndex(index, lastElement)
        val offset = offsets[index]
        val length = if (index == lastElement - 1) currentOffset - offset else offsets[index + 1] - offset
        spare.growNoCopy(length)
        spare.setLength(length)
        pool.readBytes(offset.toLong(), spare.bytes(), 0, spare.length())
        return spare.get()
    }

    /**
     * Used only by sort below, to set a [BytesRef] with the specified slice, avoiding copying
     * bytes in the common case when the slice is contained in a single block in the byte block pool.
     */
    private fun setBytesRef(spare: BytesRefBuilder, result: BytesRef, index: Int) {
        Objects.checkIndex(index, lastElement)
        val offset = offsets[index]
        val length: Int
        if (index == lastElement - 1) {
            length = currentOffset - offset
        } else {
            length = offsets[index + 1] - offset
        }
        pool.setBytesRef(spare, result, offset.toLong(), length)
    }

    /**
     * Returns a [SortState] representing the order of elements in this array. This is a
     * non-destructive operation.
     *
     * @param comp The comparator to compare [BytesRef]s. A radix sort optimization is available
     * if the comparator implements [BytesRefComparator]
     * @param stable If the sort needs to be stable
     * @return A [SortState] that could be used in [BytesRefArray.iterator]
     */
    fun sort(comp: Comparator<BytesRef>, stable: Boolean): SortState {
        val orderedEntries = IntArray(size())
        for (i in orderedEntries.indices) {
            orderedEntries[i] = i
        }
        val sorter: StringSorter
        if (stable) {
            sorter =
                object : StableStringSorter(comp) {
                    private val tmp = IntArray(size())

                    override fun get(builder: BytesRefBuilder, result: BytesRef, i: Int) {
                        this@BytesRefArray.setBytesRef(builder, result, orderedEntries[i])
                    }

                    override fun save(i: Int, j: Int) {
                        tmp[j] = orderedEntries[i]
                    }

                    override fun restore(i: Int, j: Int) {
                        /*java.lang.System.arraycopy(tmp, i, orderedEntries, i, j - i)*/
                        tmp.copyInto(
                            destination = orderedEntries,
                            destinationOffset = i,
                            startIndex = i,
                            endIndex = j
                        )
                    }

                    override fun swap(i: Int, j: Int) {
                        val o = orderedEntries[i]
                        orderedEntries[i] = orderedEntries[j]
                        orderedEntries[j] = o
                    }
                }
        } else {
            sorter =
                object : StringSorter(comp) {
                    override fun get(builder: BytesRefBuilder, result: BytesRef, i: Int) {
                        this@BytesRefArray.setBytesRef(builder, result, orderedEntries[i])
                    }

                    override fun swap(i: Int, j: Int) {
                        val o = orderedEntries[i]
                        orderedEntries[i] = orderedEntries[j]
                        orderedEntries[j] = o
                    }
                }
        }

        sorter.sort(0, size())
        return SortState(orderedEntries)
    }

    /** sugar for [.iterator] with a `null` comparator  */
    fun iterator(): BytesRefIterator {
        return iterator(null as SortState?)
    }

    /**
     * Returns a [BytesRefIterator] with point in time semantics. The iterator provides access
     * to all so far appended [BytesRef] instances.
     *
     *
     * If a non `null` [Comparator] is provided the iterator will iterate the byte
     * values in the order specified by the comparator. Otherwise the order is the same as the values
     * were appended.
     *
     *
     * This is a non-destructive operation.
     */
    override fun iterator(comp: Comparator<BytesRef>): BytesRefIterator {
        return iterator(sort(comp, false))
    }

    /**
     * Returns an [IndexedBytesRefIterator] with point in time semantics. The iterator provides
     * access to all so far appended [BytesRef] instances. If a non-null sortState is specified
     * then the iterator will iterate the byte values in the order of the sortState; otherwise, the
     * order is the same as the values were appended.
     */
    fun iterator(sortState: SortState?): IndexedBytesRefIterator {
        val size = size()
        val indices = if (sortState == null) null else sortState.indices
        require(indices == null || indices.size == size) { indices!!.size.toString() + " != " + size }
        val spare = BytesRefBuilder()
        val result = BytesRef()

        return object : IndexedBytesRefIterator {
            var pos: Int = -1
            var ord: Int = 0

            override fun next(): BytesRef? {
                ++pos
                if (pos < size) {
                    ord = if (indices == null) pos else indices[pos]
                    setBytesRef(spare, result, ord)
                    return result
                }
                return null
            }

            override fun ord(): Int {
                return ord
            }
        }
    }

    /** Used to iterate the elements of an array in a given order.  */
    class SortState internal constructor(internal val indices: IntArray) : Accountable {
        override fun ramBytesUsed(): Long {
            return (RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + indices.size * Int.SIZE_BYTES).toLong()
        }
    }

    /**
     * An extension of [BytesRefIterator] that allows retrieving the index of the current
     * element
     */
    interface IndexedBytesRefIterator : BytesRefIterator {
        /**
         * Returns the ordinal position of the element that was returned in the latest call of [ ][.next]. Do not call this method if [.next] is not called yet or the last call
         * returned a null value.
         */
        fun ord(): Int
    }
}
