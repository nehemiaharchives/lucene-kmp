package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.util.ArrayUtil

internal open class ParallelPostingsArray(val size: Int) {
    val textStarts: IntArray = IntArray(size) // maps term ID to the terms's text start in the bytesHash
    val addressOffset: IntArray = IntArray(size) // maps term ID to current stream address
    val byteStarts: IntArray = IntArray(size) // maps term ID to stream start offset in the byte pool

    open fun bytesPerPosting(): Int {
        return BYTES_PER_POSTING
    }

    open fun newInstance(size: Int): ParallelPostingsArray {
        return ParallelPostingsArray(size)
    }

    fun grow(): ParallelPostingsArray {
        val newSize: Int = ArrayUtil.oversize(size + 1, bytesPerPosting())
        val newArray = newInstance(newSize)
        copyTo(newArray, size)
        return newArray
    }

    open fun copyTo(toArray: ParallelPostingsArray, numToCopy: Int) {
        System.arraycopy(textStarts, 0, toArray.textStarts, 0, numToCopy)
        System.arraycopy(addressOffset, 0, toArray.addressOffset, 0, numToCopy)
        System.arraycopy(byteStarts, 0, toArray.byteStarts, 0, numToCopy)
    }

    companion object {
        const val BYTES_PER_POSTING: Int = 3 * Int.SIZE_BYTES
    }
}
