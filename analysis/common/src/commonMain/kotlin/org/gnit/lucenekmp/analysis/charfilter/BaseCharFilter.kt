package org.gnit.lucenekmp.analysis.charfilter

import org.gnit.lucenekmp.analysis.CharFilter
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.ArrayUtil

/**
 * Base utility class for implementing a [CharFilter].
 * Subclasses record mappings via [addOffCorrectMap], then [correct] applies offsets.
 */
abstract class BaseCharFilter(override val input: Reader) : CharFilter(input) {
    private var offsets: IntArray? = null
    private var diffs: IntArray? = null
    private var size = 0

    override fun correct(currentOff: Int): Int {
        val localOffsets = offsets ?: return currentOff
        var index = Arrays.binarySearch(localOffsets, 0, size, currentOff)
        if (index < -1) {
            index = -2 - index
        }
        val diff = if (index < 0) 0 else diffs!![index]
        return currentOff + diff
    }

    protected fun getLastCumulativeDiff(): Int = if (offsets == null) 0 else diffs!![size - 1]

    /**
     * Adds an offset correction mapping at the given output stream offset.
     * Assumes offsets are added in non-decreasing order.
     */
    protected fun addOffCorrectMap(off: Int, cumulativeDiff: Int) {
        if (offsets == null) {
            offsets = IntArray(64)
            diffs = IntArray(64)
        } else if (size == offsets!!.size) {
            offsets = ArrayUtil.grow(offsets!!)
            diffs = ArrayUtil.grow(diffs!!)
        }

        require(size == 0 || off >= offsets!![size - 1]) {
            "Offset #$size($off) is less than the last recorded offset ${offsets!![size - 1]}"
        }

        if (size == 0 || off != offsets!![size - 1]) {
            offsets!![size] = off
            diffs!![size] = cumulativeDiff
            size++
        } else {
            diffs!![size - 1] = cumulativeDiff
        }
    }
}
