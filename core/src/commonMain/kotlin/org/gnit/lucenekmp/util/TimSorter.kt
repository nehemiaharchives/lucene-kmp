package org.gnit.lucenekmp.util

import kotlin.math.max
import kotlin.math.min

/**
 * [Sorter] implementation based on the [TimSort](http://svn.python.org/projects/python/trunk/Objects/listsort.txt) algorithm. It
 * sorts small arrays with a binary sort.
 *
 *
 * This algorithm is stable. It's especially good at sorting partially-sorted arrays.
 *
 *
 * **NOTE**:There are a few differences with the original implementation:
 *
 *
 *  * <a id="maxTempSlots"></a>The extra amount of memory to perform merges is configurable. This
 * allows small merges to be very fast while large merges will be performed in-place (slightly
 * slower). You can make sure that the fast merge routine will always be used by having `
 * maxTempSlots` equal to half of the length of the slice of data to sort.
 *  * Only the fast merge routine can gallop (the one that doesn't run in-place) and it only
 * gallops on the longest slice.
 *
 *
 * @lucene.internal
 */
abstract class TimSorter protected constructor(val maxTempSlots: Int) : Sorter() {
    var minRun: Int = 0
    var to: Int = 0
    var stackSize: Int = 0
    var runEnds: IntArray

    /**
     * Create a new [TimSorter].
     *
     * @param maxTempSlots the [maximum amount of extra memory to run
 * merges](#maxTempSlots)
     */
    init {
        runEnds = IntArray(1 + STACKSIZE)
    }

    fun runLen(i: Int): Int {
        val off = stackSize - i
        return runEnds[off] - runEnds[off - 1]
    }

    fun runBase(i: Int): Int {
        return runEnds[stackSize - i - 1]
    }

    fun runEnd(i: Int): Int {
        return runEnds[stackSize - i]
    }

    fun setRunEnd(i: Int, runEnd: Int) {
        runEnds[stackSize - i] = runEnd
    }

    fun pushRunLen(len: Int) {
        runEnds[stackSize + 1] = runEnds[stackSize] + len
        ++stackSize
    }

    /** Compute the length of the next run, make the run sorted and return its length.  */
    fun nextRun(): Int {
        val runBase = runEnd(0)
        require(runBase < to)
        if (runBase == to - 1) {
            return 1
        }
        var o = runBase + 2
        if (compare(runBase, runBase + 1) > 0) {
            // run must be strictly descending
            while (o < to && compare(o - 1, o) > 0) {
                ++o
            }
            reverse(runBase, o)
        } else {
            // run must be non-descending
            while (o < to && compare(o - 1, o) <= 0) {
                ++o
            }
        }
        val runHi = max(o, min(to, runBase + minRun))
        binarySort(runBase, runHi, o)
        return runHi - runBase
    }

    fun ensureInvariants() {
        while (stackSize > 1) {
            val runLen0 = runLen(0)
            val runLen1 = runLen(1)

            if (stackSize > 2) {
                val runLen2 = runLen(2)

                if (runLen2 <= runLen1 + runLen0) {
                    // merge the smaller of 0 and 2 with 1
                    if (runLen2 < runLen0) {
                        mergeAt(1)
                    } else {
                        mergeAt(0)
                    }
                    continue
                }
            }

            if (runLen1 <= runLen0) {
                mergeAt(0)
                continue
            }

            break
        }
    }

    fun exhaustStack() {
        while (stackSize > 1) {
            mergeAt(0)
        }
    }

    fun reset(from: Int, to: Int) {
        stackSize = 0
        runEnds.fill(0) /*java.util.Arrays.fill(runEnds, 0)*/
        runEnds[0] = from
        this.to = to
        val length = to - from
        this.minRun = if (length <= THRESHOLD) length else minRun(length)
    }

    fun mergeAt(n: Int) {
        require(stackSize >= 2)
        merge(runBase(n + 1), runBase(n), runEnd(n))
        for (j in n + 1 downTo 1) {
            setRunEnd(j, runEnd(j - 1))
        }
        --stackSize
    }

    fun merge(lo: Int, mid: Int, hi: Int) {
        var lo = lo
        var hi = hi
        if (compare(mid - 1, mid) <= 0) {
            return
        }
        lo = upper2(lo, mid, mid)
        hi = lower2(mid, hi, mid - 1)

        if (hi - mid <= mid - lo && hi - mid <= maxTempSlots) {
            mergeHi(lo, mid, hi)
        } else if (mid - lo <= maxTempSlots) {
            mergeLo(lo, mid, hi)
        } else {
            mergeInPlace(lo, mid, hi)
        }
    }

    override fun sort(from: Int, to: Int) {
        checkRange(from, to)
        if (to - from <= 1) {
            return
        }
        reset(from, to)
        do {
            ensureInvariants()
            pushRunLen(nextRun())
        } while (runEnd(0) < to)
        exhaustStack()
        require(runEnd(0) == to)
    }

    override fun doRotate(lo: Int, mid: Int, hi: Int) {
        var lo = lo
        var mid = mid
        val len1 = mid - lo
        val len2 = hi - mid
        if (len1 == len2) {
            while (mid < hi) {
                swap(lo++, mid++)
            }
        } else if (len2 < len1 && len2 <= maxTempSlots) {
            save(mid, len2)
            run {
                var i = lo + len1 - 1
                var j = hi - 1
                while (i >= lo) {
                    copy(i, j)
                    --i
                    --j
                }
            }
            var i = 0
            var j = lo
            while (i < len2) {
                restore(i, j)
                ++i
                ++j
            }
        } else if (len1 <= maxTempSlots) {
            save(lo, len1)
            run {
                var i = mid
                var j = lo
                while (i < hi) {
                    copy(i, j)
                    ++i
                    ++j
                }
            }
            var i = 0
            var j = lo + len2
            while (j < hi) {
                restore(i, j)
                ++i
                ++j
            }
        } else {
            reverse(lo, mid)
            reverse(mid, hi)
            reverse(lo, hi)
        }
    }

    fun mergeLo(lo: Int, mid: Int, hi: Int) {
        require(compare(lo, mid) > 0)
        val len1 = mid - lo
        save(lo, len1)
        copy(mid, lo)
        var i = 0
        var j = mid + 1
        var dest = lo + 1
        outer@ while (true) {
            var count = 0
            while (count < MIN_GALLOP) {
                if (i >= len1 || j >= hi) {
                    break@outer
                } else if (compareSaved(i, j) <= 0) {
                    restore(i++, dest++)
                    count = 0
                } else {
                    copy(j++, dest++)
                    ++count
                }
            }
            // galloping...
            val next = lowerSaved3(j, hi, i)
            while (j < next) {
                copy(j++, dest)
                ++dest
            }
            restore(i++, dest++)
        }
        while (i < len1) {
            restore(i++, dest)
            ++dest
        }
        require(j == dest)
    }

    fun mergeHi(lo: Int, mid: Int, hi: Int) {
        require(compare(mid - 1, hi - 1) > 0)
        val len2 = hi - mid
        save(mid, len2)
        copy(mid - 1, hi - 1)
        var i = mid - 2
        var j = len2 - 1
        var dest = hi - 2
        outer@ while (true) {
            var count = 0
            while (count < MIN_GALLOP) {
                if (i < lo || j < 0) {
                    break@outer
                } else if (compareSaved(j, i) >= 0) {
                    restore(j--, dest--)
                    count = 0
                } else {
                    copy(i--, dest--)
                    ++count
                }
            }
            // galloping
            val next = upperSaved3(lo, i + 1, j)
            while (i >= next) {
                copy(i--, dest--)
            }
            restore(j--, dest--)
        }
        while (j >= 0) {
            restore(j--, dest)
            --dest
        }
        require(i == dest)
    }

    fun lowerSaved(from: Int, to: Int, `val`: Int): Int {
        var from = from
        var len = to - from
        while (len > 0) {
            val half = len ushr 1
            val mid = from + half
            if (compareSaved(`val`, mid) > 0) {
                from = mid + 1
                len = len - half - 1
            } else {
                len = half
            }
        }
        return from
    }

    fun upperSaved(from: Int, to: Int, `val`: Int): Int {
        var from = from
        var len = to - from
        while (len > 0) {
            val half = len ushr 1
            val mid = from + half
            if (compareSaved(`val`, mid) < 0) {
                len = half
            } else {
                from = mid + 1
                len = len - half - 1
            }
        }
        return from
    }

    // faster than lowerSaved when val is at the beginning of [from:to[
    fun lowerSaved3(from: Int, to: Int, `val`: Int): Int {
        var f = from
        var t = f + 1
        while (t < to) {
            if (compareSaved(`val`, t) <= 0) {
                return lowerSaved(f, t, `val`)
            }
            val delta = t - f
            f = t
            t += delta shl 1
        }
        return lowerSaved(f, to, `val`)
    }

    // faster than upperSaved when val is at the end of [from:to[
    fun upperSaved3(from: Int, to: Int, `val`: Int): Int {
        var f = to - 1
        var t = to
        while (f > from) {
            if (compareSaved(`val`, f) >= 0) {
                return upperSaved(f, t, `val`)
            }
            val delta = t - f
            t = f
            f -= delta shl 1
        }
        return upperSaved(from, t, `val`)
    }

    /** Copy data from slot `src` to slot `dest`.  */
    protected abstract fun copy(src: Int, dest: Int)

    /**
     * Save all elements between slots `i` and `i+len` into the temporary
     * storage.
     */
    protected abstract fun save(i: Int, len: Int)

    /** Restore element `j` from the temporary storage into slot `i`.  */
    protected abstract fun restore(i: Int, j: Int)

    /**
     * Compare element `i` from the temporary storage with element `j` from the
     * slice to sort, similarly to [.compare].
     */
    protected abstract fun compareSaved(i: Int, j: Int): Int

    companion object {
        const val MINRUN: Int = 32
        const val THRESHOLD: Int = 64
        const val STACKSIZE: Int = 49 // depends on MINRUN
        const val MIN_GALLOP: Int = 7

        /** Minimum run length for an array of length `length`.  */
        fun minRun(length: Int): Int {
            require(length >= MINRUN)
            var n = length
            var r = 0
            while (n >= 64) {
                r = r or (n and 1)
                n = n ushr 1
            }
            val minRun = n + r
            require(minRun >= MINRUN && minRun <= THRESHOLD)
            return minRun
        }
    }
}
