package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.internal.hppc.HashContainers.DEFAULT_EXPECTED_ELEMENTS
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.reflect.cast


/**
 * An array-backed list of `int`.
 *
 *
 * Mostly forked and trimmed from com.carrotsearch.hppc.IntArrayList
 *
 *
 * github: https://github.com/carrotsearch/hppc release 0.10.0
 *
 * @lucene.internal
 */
open class IntArrayList(expectedElements: Int) : Iterable<IntCursor>, Cloneable<IntArrayList>, Accountable {
    /**
     * Internal array for storing the list. The array may be larger than the current size ([ ][.size]).
     */
    var buffer: IntArray

    /** Current number of elements stored in [.buffer].  */
    var elementsCount: Int = 0

    /** New instance with sane defaults.  */
    constructor() : this(DEFAULT_EXPECTED_ELEMENTS)

    /**
     * New instance with sane defaults.
     *
     * @param expectedElements The expected number of elements guaranteed not to cause buffer
     * expansion (inclusive).
     */
    init {
        buffer = IntArray(expectedElements)
    }

    /** Creates a new list from the elements of another list in its iteration order.  */
    constructor(list: IntArrayList) : this(list.size()) {
        addAll(list)
    }

    fun add(e1: Int) {
        ensureBufferSpace(1)
        buffer[elementsCount++] = e1
    }

    /** Add all elements from a range of given array to the list.  */
    fun add(elements: IntArray, start: Int, length: Int) {
        require(length >= 0) { "Length must be >= 0" }

        ensureBufferSpace(length)
        /*java.lang.System.arraycopy(elements, start, buffer, elementsCount, length)*/
        elements.copyInto(
            destination = buffer,
            destinationOffset = elementsCount,
            startIndex = start,
            endIndex = start + length
        )
        elementsCount += length
    }

    /**
     * Vararg-signature method for adding elements at the end of the list.
     *
     *
     * **This method is handy, but costly if used in tight loops (anonymous array passing)**
     */
    /*  */
    fun add(vararg elements: Int) {
        add(elements, 0, elements.size)
    }

    /** Adds all elements from another list.  */
    fun addAll(list: IntArrayList): Int {
        val size = list.size()
        ensureBufferSpace(size)

        for (cursor in list) {
            add(cursor!!.value)
        }

        return size
    }

    /** Adds all elements from another iterable.  */
    fun addAll(iterable: Iterable<out IntCursor>): Int {
        var size = 0
        for (cursor in iterable) {
            add(cursor.value)
            size++
        }
        return size
    }

    fun insert(index: Int, e1: Int) {
        require(index >= 0 && index <= size()) { "Index " + index + " out of bounds [" + 0 + ", " + size() + "]." }

        ensureBufferSpace(1)
        /*java.lang.System.arraycopy(buffer, index, buffer, index + 1, elementsCount - index)*/
        buffer.copyInto(
            destination = buffer,
            destinationOffset = index + 1,
            startIndex = index,
            endIndex = elementsCount
        )
        buffer[index] = e1
        elementsCount++
    }

    fun get(index: Int): Int {
        require(index >= 0 && index < size()) { "Index " + index + " out of bounds [" + 0 + ", " + size() + ")." }

        return buffer[index]
    }

    fun set(index: Int, e1: Int): Int {
        require(index >= 0 && index < size()) { "Index " + index + " out of bounds [" + 0 + ", " + size() + ")." }

        val v = buffer[index]
        buffer[index] = e1
        return v
    }

    /** Removes the element at the specified position in this container and returns it.  */
    fun removeAt(index: Int): Int {
        require(index >= 0 && index < size()) { "Index " + index + " out of bounds [" + 0 + ", " + size() + ")." }

        val v = buffer[index]
        /*java.lang.System.arraycopy(buffer, index + 1, buffer, index, --elementsCount - index)*/
        buffer.copyInto(
            destination = buffer,
            destinationOffset = index,
            startIndex = index + 1,
            endIndex = elementsCount
        )
        elementsCount--
        return v
    }

    /** Removes and returns the last element of this list.  */
    fun removeLast(): Int {
        require(!this.isEmpty) { "List is empty" }

        return buffer[--elementsCount]
    }

    /**
     * Removes from this list all the elements with indexes between `fromIndex`, inclusive,
     * and `toIndex`, exclusive.
     */
    fun removeRange(fromIndex: Int, toIndex: Int) {
        require(fromIndex >= 0 && fromIndex <= size()) { "Index " + fromIndex + " out of bounds [" + 0 + ", " + size() + ")." }
        require(toIndex >= 0 && toIndex <= size()) { "Index " + toIndex + " out of bounds [" + 0 + ", " + size() + "]." }
        require(fromIndex <= toIndex) { "fromIndex must be <= toIndex: $fromIndex, $toIndex" }

        /*java.lang.System.arraycopy(buffer, toIndex, buffer, fromIndex, elementsCount - toIndex)*/
        buffer.copyInto(
            destination = buffer,
            destinationOffset = fromIndex,
            startIndex = toIndex,
            endIndex = elementsCount
        )
        val count = toIndex - fromIndex
        elementsCount -= count
    }

    /**
     * Removes the first element that equals `e`, returning whether an element has been
     * removed.
     */
    fun removeElement(e: Int): Boolean {
        return removeFirst(e) != -1
    }

    /**
     * Removes the first element that equals `e1`, returning its deleted position or `
     * -1` if the element was not found.
     */
    fun removeFirst(e1: Int): Int {
        val index = indexOf(e1)
        if (index >= 0) removeAt(index)
        return index
    }

    /**
     * Removes the last element that equals `e1`, returning its deleted position or `
     * -1` if the element was not found.
     */
    fun removeLast(e1: Int): Int {
        val index = lastIndexOf(e1)
        if (index >= 0) removeAt(index)
        return index
    }

    /**
     * Removes all occurrences of `e` from this collection.
     *
     * @param e Element to be removed from this collection, if present.
     * @return The number of removed elements as a result of this call.
     */
    fun removeAll(e: Int): Int {
        var to = 0
        for (from in 0..<elementsCount) {
            if (((e) == (buffer[from]))) {
                continue
            }
            if (to != from) {
                buffer[to] = buffer[from]
            }
            to++
        }
        val deleted = elementsCount - to
        this.elementsCount = to
        return deleted
    }

    fun contains(e1: Int): Boolean {
        return indexOf(e1) >= 0
    }

    fun indexOf(e1: Int): Int {
        for (i in 0..<elementsCount) {
            if (((e1) == (buffer[i]))) {
                return i
            }
        }

        return -1
    }

    fun lastIndexOf(e1: Int): Int {
        for (i in elementsCount - 1 downTo 0) {
            if (((e1) == (buffer[i]))) {
                return i
            }
        }

        return -1
    }

    val isEmpty: Boolean
        get() = elementsCount == 0

    /**
     * Ensure this container can hold at least the given number of elements without resizing its
     * buffers.
     *
     * @param expectedElements The total number of elements, inclusive.
     */
    fun ensureCapacity(expectedElements: Int) {
        if (expectedElements > buffer.size) {
            ensureBufferSpace(expectedElements - size())
        }
    }

    /**
     * Ensures the internal buffer has enough free slots to store `expectedAdditions`.
     * Increases internal buffer size if needed.
     */
    protected fun ensureBufferSpace(expectedAdditions: Int) {
        if (elementsCount + expectedAdditions > buffer.size) {
            this.buffer = ArrayUtil.grow(buffer, elementsCount + expectedAdditions)
        }
    }

    /**
     * Truncate or expand the list to the new size. If the list is truncated, the buffer will not be
     * reallocated (use [.trimToSize] if you need a truncated buffer), but the truncated
     * values will be reset to the default value (zero). If the list is expanded, the elements beyond
     * the current size are initialized with JVM-defaults (zero or `null` values).
     */
    fun resize(newSize: Int) {
        if (newSize <= buffer.size) {
            if (newSize < elementsCount) {
                /*java.util.Arrays.fill(buffer, newSize, elementsCount, 0)*/
                buffer.fill(0, newSize, elementsCount)
            } else {
                /*java.util.Arrays.fill(buffer, elementsCount, newSize, 0)*/
                buffer.fill(0, elementsCount, newSize)
            }
        } else {
            ensureCapacity(newSize)
        }
        this.elementsCount = newSize
    }

    fun size(): Int {
        return elementsCount
    }

    /** Trim the internal buffer to the current size.  */
    fun trimToSize() {
        if (size() != this.buffer.size) {
            this.buffer = toArray()
        }
    }

    /**
     * Sets the number of stored elements to zero. Releases and initializes the internal storage array
     * to default values. To clear the list without cleaning the buffer, simply set the [ ][.elementsCount] field to zero.
     */
    fun clear() {
        /*java.util.Arrays.fill(buffer, 0, elementsCount, 0)*/
        buffer.fill(0, 0, elementsCount)
        this.elementsCount = 0
    }

    /** Sets the number of stored elements to zero and releases the internal storage array.  */
    fun release() {
        this.buffer = EMPTY_ARRAY
        this.elementsCount = 0
    }

    /** The returned array is sized to match exactly the number of elements of the stack.  */
    fun toArray(): IntArray {
        return ArrayUtil.copyOfSubArray(buffer, 0, elementsCount)
    }

    /**
     * Clone this object. The returned clone will reuse the same hash function and array resizing
     * strategy.
     */
    override fun clone(): IntArrayList {
        val newInstance = IntArrayList(this.size())
        newInstance.addAll(this)
        return newInstance
    }

    override fun hashCode(): Int {
        var h = 1
        val max = elementsCount
        for (i in 0..<max) {
            h = 31 * h + BitMixer.mix(this.buffer[i])
        }
        return h
    }

    /**
     * Returns `true` only if the other object is an instance of the same class and with
     * the same elements.
     */
    override fun equals(obj: Any?): Boolean {
        return (this === obj)
                || (obj != null && this::class == obj::class && equalElements(this::class.cast(obj)))
    }

    /** Compare index-aligned elements against another [IntArrayList].  */
    fun equalElements(other: IntArrayList): Boolean {
        val max = size()
        if (other.size() != max) {
            return false
        }

        for (i in 0..<max) {
            if ((get(i)) != (other.get(i))) {
                return false
            }
        }

        return true
    }

    /** Convert the contents of this list to a human-friendly string.  */
    override fun toString(): String {
        return this.toArray().contentToString()
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(buffer)
    }

    /**
     * Returns a lazy sequence of all elements currently stored in the list.
     */
    fun stream(): Sequence<Int> = buffer.asSequence().take(elementsCount)

    /** Sorts the elements in this list and returns this list.  */
    fun sort(): IntArrayList {
        Arrays.sort(buffer, 0, elementsCount)

        return this
    }

    /** Reverses the elements in this list and returns this list.  */
    fun reverse(): IntArrayList {
        var i = 0
        val mid = elementsCount shr 1
        var j = elementsCount - 1
        while (i < mid) {
            val tmp = buffer[i]
            buffer[i] = buffer[j]
            buffer[j] = tmp
            i++
            j--
        }
        return this
    }

    /** An iterator implementation for [IntArrayList.iterator].  */
    internal class ValueIterator(buffer: IntArray, size: Int) : AbstractIterator<IntCursor>() {
        private val cursor: IntCursor

        private val buffer: IntArray
        private val size: Int

        init {
            this.cursor = IntCursor()
            this.cursor.index = -1
            this.size = size
            this.buffer = buffer
        }

        override fun fetch(): IntCursor? {
            if (cursor.index + 1 == size) return done()

            cursor.value = buffer[++cursor.index]
            return cursor
        }
    }

    override fun iterator(): MutableIterator<IntCursor> {
        return ValueIterator(buffer, size()) as MutableIterator<IntCursor>
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(IntArrayList::class)

        /** An immutable empty buffer (array).  */
        val EMPTY_ARRAY: IntArray = IntArray(0)

        /**
         * Create a list from a variable number of arguments or an array of `int`. The elements
         * are copied from the argument to the internal buffer.
         */
        /*  */
        fun from(vararg elements: Int): IntArrayList {
            val list = IntArrayList(elements.size)
            list.add(*elements)
            return list
        }
    }
}
