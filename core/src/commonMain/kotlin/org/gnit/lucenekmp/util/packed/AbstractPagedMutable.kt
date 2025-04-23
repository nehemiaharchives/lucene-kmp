package org.gnit.lucenekmp.util.packed

import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.LongValues
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.packed.PackedInts.checkBlockSize
import org.gnit.lucenekmp.util.packed.PackedInts.numBlocks
import kotlin.jvm.JvmOverloads
import kotlin.math.min

/**
 * Base implementation for [PagedMutable] and [PagedGrowableWriter].
 *
 * @lucene.internal
 */
abstract class AbstractPagedMutable<T : AbstractPagedMutable<T>> internal constructor(
    val bitsPerValue: Int,
    val size: Long,
    pageSize: Int
) : LongValues(), Accountable {
    val pageShift: Int = checkBlockSize(pageSize, MIN_BLOCK_SIZE, MAX_BLOCK_SIZE)
    val pageMask: Int = pageSize - 1
    val subMutables: Array<PackedInts.Mutable?>

    init {
        val numPages: Int = numBlocks(size, pageSize)
        subMutables = kotlin.arrayOfNulls<PackedInts.Mutable>(numPages)
    }

    protected fun fillPages() {
        val numPages: Int = numBlocks(size, pageSize())
        for (i in 0..<numPages) {
            // do not allocate for more entries than necessary on the last page
            val valueCount = if (i == numPages - 1) lastPageSize(size) else pageSize()
            subMutables[i] = newMutable(valueCount, bitsPerValue)
        }
    }

    protected abstract fun newMutable(valueCount: Int, bitsPerValue: Int): PackedInts.Mutable

    fun lastPageSize(size: Long): Int {
        val sz = indexInPage(size)
        return if (sz == 0) pageSize() else sz
    }

    fun pageSize(): Int {
        return pageMask + 1
    }

    /** The number of values.  */
    fun size(): Long {
        return size
    }

    fun pageIndex(index: Long): Int {
        return (index ushr pageShift).toInt()
    }

    fun indexInPage(index: Long): Int {
        return index.toInt() and pageMask
    }

    override fun get(index: Long): Long {
        require(index >= 0 && index < size) { "index=$index size=$size" }
        val pageIndex = pageIndex(index)
        val indexInPage = indexInPage(index)
        return subMutables[pageIndex]!!.get(indexInPage)
    }

    /** Set value at `index`.  */
    fun set(index: Long, value: Long) {
        require(index >= 0 && index < size)
        val pageIndex = pageIndex(index)
        val indexInPage = indexInPage(index)
        subMutables[pageIndex]!!.set(indexInPage, value)
    }

    protected open fun baseRamBytesUsed(): Long {
        return (RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                + RamUsageEstimator.NUM_BYTES_OBJECT_REF
                + Long.SIZE_BYTES
                + 3 * Int.SIZE_BYTES).toLong()
    }

    override fun ramBytesUsed(): Long {
        var bytesUsed: Long = RamUsageEstimator.alignObjectSize(baseRamBytesUsed())
        bytesUsed += RamUsageEstimator.alignObjectSize(RamUsageEstimator.shallowSizeOf(subMutables))
        for (gw in subMutables) {
            bytesUsed += gw!!.ramBytesUsed()
        }
        return bytesUsed
    }

    protected abstract fun newUnfilledCopy(newSize: Long): T

    /**
     * Create a new copy of size `newSize` based on the content of this buffer. This method
     * is much more efficient than creating a new instance and copying values one by one.
     */
    fun resize(newSize: Long): T {
        val copy = newUnfilledCopy(newSize)
        val numCommonPages = min(copy.subMutables.size, subMutables.size)
        val copyBuffer = LongArray(1024)
        for (i in copy.subMutables.indices) {
            val valueCount = if (i == copy.subMutables.size - 1) lastPageSize(newSize) else pageSize()
            val bpv = if (i < numCommonPages) subMutables[i]!!.getBitsPerValue() else this.bitsPerValue
            copy.subMutables[i] = newMutable(valueCount, bpv)
            if (i < numCommonPages) {
                val copyLength: Int = min(valueCount, subMutables[i]!!.size())
                PackedInts.copy(subMutables[i]!!, 0, copy.subMutables[i]!!, 0, copyLength, copyBuffer)
            }
        }
        return copy
    }

    /** Similar to [ArrayUtil.grow].  */
    /** Similar to [ArrayUtil.grow].  */
    @JvmOverloads
    fun grow(minSize: Long = size() + 1): T {
        require(minSize >= 0)
        if (minSize <= size()) {
            val result = this as T
            return result
        }
        var extra = minSize ushr 3
        if (extra < 3) {
            extra = 3
        }
        val newSize = minSize + extra
        return resize(newSize)
    }

    override fun toString(): String {
        return this::class.simpleName + "(size=" + size() + ",pageSize=" + pageSize() + ")"
    }

    companion object {
        const val MIN_BLOCK_SIZE: Int = 1 shl 6
        const val MAX_BLOCK_SIZE: Int = 1 shl 30
    }
}
