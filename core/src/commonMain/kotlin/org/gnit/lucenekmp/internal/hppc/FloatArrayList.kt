package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.reflect.cast

/**
 * An array-backed list of `float`.
 *
 *
 * Mostly forked and trimmed from com.carrotsearch.hppc.FloatArrayList
 *
 *
 * github: https://github.com/carrotsearch/hppc release 0.10.0
 *
 * @lucene.internal
 */
class FloatArrayList(expectedElements: Int = HashContainers.DEFAULT_EXPECTED_ELEMENTS) : Iterable<FloatCursor>, Cloneable<FloatArrayList>, Accountable {
    /**
     * Internal array for storing the list. The array may be larger than the current size ([ ][.size]).
     */
    var buffer: FloatArray

    /** Current number of elements stored in [.buffer].  */
    var elementsCount: Int = 0

    /**
     * New instance with sane defaults.
     *
     * @param expectedElements The expected number of elements guaranteed not to cause buffer
     * expansion (inclusive).
     */
    /** New instance with sane defaults.  */
    init {
        buffer = FloatArray(expectedElements)
    }

    /** Creates a new list from the elements of another list in its iteration order.  */
    constructor(list: FloatArrayList) : this(list.size()) {
        addAll(list)
    }

    fun add(e1: Float) {
        ensureBufferSpace(1)
        buffer[elementsCount++] = e1
    }

    /** Add all elements from a range of given array to the list.  */
    fun add(elements: FloatArray, start: Int, length: Int) {
        assert(length >= 0) { "Length must be >= 0" }

        ensureBufferSpace(length)
        System.arraycopy(elements, start, buffer, elementsCount, length)
        elementsCount += length
    }

    /**
     * Vararg-signature method for adding elements at the end of the list.
     *
     *
     * **This method is handy, but costly if used in tight loops (anonymous array passing)**
     */
    /*  */
    fun add(vararg elements: Float) {
        add(elements, 0, elements.size)
    }

    /** Adds all elements from another list.  */
    fun addAll(list: FloatArrayList): Int {
        val size = list.size()
        ensureBufferSpace(size)

        for (cursor in list) {
            add(cursor.value)
        }

        return size
    }

    /** Adds all elements from another iterable.  */
    fun addAll(iterable: Iterable<out FloatCursor>): Int {
        var size = 0
        for (cursor in iterable) {
            add(cursor.value)
            size++
        }
        return size
    }

    fun insert(index: Int, e1: Float) {
        assert(index >= 0 && index <= size()) { "Index " + index + " out of bounds [" + 0 + ", " + size() + "]." }

        ensureBufferSpace(1)
        System.arraycopy(buffer, index, buffer, index + 1, elementsCount - index)
        buffer[index] = e1
        elementsCount++
    }

    fun get(index: Int): Float {
        assert(index >= 0 && index < size()) { "Index " + index + " out of bounds [" + 0 + ", " + size() + ")." }

        return buffer[index]
    }

    fun set(index: Int, e1: Float): Float {
        assert(index >= 0 && index < size()) { "Index " + index + " out of bounds [" + 0 + ", " + size() + ")." }

        val v = buffer[index]
        buffer[index] = e1
        return v
    }

    /** Removes the element at the specified position in this container and returns it.  */
    fun removeAt(index: Int): Float {
        assert(index >= 0 && index < size()) { "Index " + index + " out of bounds [" + 0 + ", " + size() + ")." }

        val v = buffer[index]
        System.arraycopy(buffer, index + 1, buffer, index, --elementsCount - index)
        return v
    }

    /** Removes and returns the last element of this list.  */
    fun removeLast(): Float {
        assert(!this.isEmpty) { "List is empty" }

        return buffer[--elementsCount]
    }

    /**
     * Removes from this list all the elements with indexes between `fromIndex`, inclusive,
     * and `toIndex`, exclusive.
     */
    fun removeRange(fromIndex: Int, toIndex: Int) {
        assert(fromIndex >= 0 && fromIndex <= size()) { "Index " + fromIndex + " out of bounds [" + 0 + ", " + size() + ")." }
        assert(toIndex >= 0 && toIndex <= size()) { "Index " + toIndex + " out of bounds [" + 0 + ", " + size() + "]." }
        assert(fromIndex <= toIndex) { "fromIndex must be <= toIndex: $fromIndex, $toIndex" }

        System.arraycopy(buffer, toIndex, buffer, fromIndex, elementsCount - toIndex)
        val count = toIndex - fromIndex
        elementsCount -= count
    }

    /**
     * Removes the first element that equals `e`, returning whether an element has been
     * removed.
     */
    fun removeElement(e: Float): Boolean {
        return removeFirst(e) != -1
    }

    /**
     * Removes the first element that equals `e1`, returning its deleted position or `
     * -1` if the element was not found.
     */
    fun removeFirst(e1: Float): Int {
        val index = indexOf(e1)
        if (index >= 0) removeAt(index)
        return index
    }

    /**
     * Removes the last element that equals `e1`, returning its deleted position or `
     * -1` if the element was not found.
     */
    fun removeLast(e1: Float): Int {
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
    fun removeAll(e: Float): Int {
        var to = 0
        for (from in 0..elementsCount) {
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

    fun contains(e1: Float): Boolean {
        return indexOf(e1) >= 0
    }

    fun indexOf(e1: Float): Int {
        for (i in 0..<elementsCount) {
            if (((e1) == (buffer[i]))) {
                return i
            }
        }

        return -1
    }

    fun lastIndexOf(e1: Float): Int {
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
                Arrays.fill(buffer, newSize, elementsCount, 0f)
            } else {
                Arrays.fill(buffer, elementsCount, newSize, 0f)
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
        Arrays.fill(buffer, 0, elementsCount, 0f)
        this.elementsCount = 0
    }

    /** Sets the number of stored elements to zero and releases the internal storage array.  */
    fun release() {
        this.buffer = EMPTY_ARRAY
        this.elementsCount = 0
    }

    /** The returned array is sized to match exactly the number of elements of the stack.  */
    fun toArray(): FloatArray {
        return ArrayUtil.copyOfSubArray(buffer, 0, elementsCount)
    }

    /**
     * Clone this object. The returned clone will reuse the same hash function and array resizing
     * strategy.
     */
    override fun clone(): FloatArrayList {
        val newInstance = FloatArrayList(this.size())
        newInstance.addAll(this)
        return newInstance
    }

    override fun hashCode(): Int {
        var h = 1
        val max = elementsCount
        for (i in 0..max) {
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

    /** Compare index-aligned elements against another [FloatArrayList].  */
    protected fun equalElements(other: FloatArrayList): Boolean {
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

    /** Sorts the elements in this list and returns this list.  */
    fun sort(): FloatArrayList {
        Arrays.sort(buffer, 0, elementsCount)
        return this
    }

    /** Reverses the elements in this list and returns this list.  */
    fun reverse(): FloatArrayList {
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

    /** An iterator implementation for [FloatArrayList.iterator].  */
    internal class ValueIterator(buffer: FloatArray, size: Int) : MutableIterator<FloatCursor> {
        private val cursor: FloatCursor

        private val buffer: FloatArray
        private val size: Int

        init {
            this.cursor = FloatCursor()
            this.cursor.index = -1
            this.size = size
            this.buffer = buffer
        }

        fun fetch(): FloatCursor {
            if (cursor.index + 1 == size) return done()

            cursor.value = buffer[++cursor.index]
            return cursor
        }

        private fun done(): FloatCursor{
            TODO("Not yet implemented")
        }

        override fun remove() {
            TODO("Not yet implemented")
        }

        override fun next(): FloatCursor {
            TODO("Not yet implemented")
        }

        override fun hasNext(): Boolean {
            TODO("Not yet implemented")
        }
    }

    override fun iterator(): MutableIterator<FloatCursor> {
        return ValueIterator(buffer, size())
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(FloatArrayList::class)

        /** An immutable empty buffer (array).  */
        val EMPTY_ARRAY: FloatArray = FloatArray(0)

        /**
         * Create a list from a variable number of arguments or an array of `int`. The elements
         * are copied from the argument to the internal buffer.
         */
        /*  */
        fun from(vararg elements: Float): FloatArrayList {
            val list = FloatArrayList(elements.size)
            list.add(*elements)
            return list
        }
    }
}
