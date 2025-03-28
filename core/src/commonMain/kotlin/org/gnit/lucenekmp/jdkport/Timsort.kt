package org.gnit.lucenekmp.jdkport

object Timsort {
    private const val MIN_MERGE = 32

    /**
     * Sorts the specified array using TimSort with the given comparator.
     */
    fun <T> sort(a: Array<T>, c: Comparator<T>) {
        if (a.size < 2) return
        TimSorter(a, c).sort()
    }

    private class TimSorter<T>(private val a: Array<T>, private val c: Comparator<T>) {
        // Used for galloping (not fully optimized)
        private var minGallop = 7
        // Temp storage for merges; initially allocated to half of the array size
        private var tmp: Array<T?> = arrayOfNulls(a.size / 2)

        // Run stack arrays. We overallocate assuming arrays won’t be huge.
        private val runBase = IntArray(40)
        private val runLen = IntArray(40)
        private var stackSize = 0

        fun sort() {
            val n = a.size
            if (n < 2) return

            val minRun = minRunLength(n)
            var lo = 0
            while (lo < n) {
                // Identify run, make it ascending if necessary.
                var runLength = countRunAndMakeAscending(lo, n)
                // If run is short, extend to minRun using binary insertion sort.
                if (runLength < minRun) {
                    val force = if (n - lo < minRun) n - lo else minRun
                    binarySort(lo, lo + force, lo + runLength)
                    runLength = force
                }
                // Push run onto stack.
                pushRun(lo, runLength)
                mergeCollapse()
                lo += runLength
            }
            mergeForceCollapse()
        }

        /**
         * Computes the minimum run length for an array of size n.
         */
        private fun minRunLength(n: Int): Int {
            var n = n
            var r = 0
            while (n >= MIN_MERGE) {
                r = r or (n and 1)
                n = n shr 1
            }
            return n + r
        }

        /**
         * Identifies a run beginning at index [lo] and makes it ascending.
         * Returns the length of the run.
         */
        private fun countRunAndMakeAscending(lo: Int, hi: Int): Int {
            var runHi = lo + 1
            if (runHi == hi) return 1

            // Determine run direction.
            if (c.compare(a[runHi], a[lo]) < 0) {
                // Descending run: advance until run ends.
                while (runHi < hi && c.compare(a[runHi], a[runHi - 1]) < 0) {
                    runHi++
                }
                // Reverse to make ascending.
                reverseRange(lo, runHi)
            } else {
                // Ascending run.
                while (runHi < hi && c.compare(a[runHi], a[runHi - 1]) >= 0) {
                    runHi++
                }
            }
            return runHi - lo
        }

        /**
         * Reverses the elements in [a] between indices [lo] (inclusive) and [hi] (exclusive).
         */
        private fun reverseRange(lo: Int, hi: Int) {
            var i = lo
            var j = hi - 1
            while (i < j) {
                val t = a[i]
                a[i] = a[j]
                a[j] = t
                i++
                j--
            }
        }

        /**
         * Performs a binary insertion sort on [a] from [lo] (inclusive) to [hi] (exclusive),
         * starting at index [start].
         */
        private fun binarySort(lo: Int, hi: Int, start: Int) {
            var start = start
            if (start == lo) start++
            for (i in start until hi) {
                val pivot = a[i]
                var left = lo
                var right = i
                // Locate insertion point via binary search.
                while (left < right) {
                    val mid = (left + right) ushr 1
                    if (c.compare(pivot, a[mid]) < 0) {
                        right = mid
                    } else {
                        left = mid + 1
                    }
                }
                // Shift elements to make room.
                var j = i
                while (j > left) {
                    a[j] = a[j - 1]
                    j--
                }
                a[left] = pivot
            }
        }

        private fun pushRun(runBaseVal: Int, runLenVal: Int) {
            runBase[stackSize] = runBaseVal
            runLen[stackSize] = runLenVal
            stackSize++
        }

        /**
         * Merges runs on the stack until the invariants are restored.
         */
        private fun mergeCollapse() {
            while (stackSize > 1) {
                var n = stackSize - 2
                if (n >= 1 && runLen[n - 1] <= runLen[n] + runLen[n + 1]) {
                    if (runLen[n - 1] < runLen[n + 1])
                        n--
                    mergeAt(n)
                } else if (runLen[n] <= runLen[n + 1]) {
                    mergeAt(n)
                } else {
                    break
                }
            }
        }

        /**
         * Repeatedly merges remaining runs to finish sorting.
         */
        private fun mergeForceCollapse() {
            while (stackSize > 1) {
                var n = stackSize - 2
                if (n > 0 && runLen[n - 1] < runLen[n + 1])
                    n--
                mergeAt(n)
            }
        }

        /**
         * Merges the runs at positions [i] and [i+1].
         */
        private fun mergeAt(i: Int) {
            val base1 = runBase[i]
            val len1 = runLen[i]
            val base2 = runBase[i + 1]
            val len2 = runLen[i + 1]
            runLen[i] = len1 + len2
            if (i == stackSize - 3) {
                runBase[i + 1] = runBase[i + 2]
                runLen[i + 1] = runLen[i + 2]
            }
            stackSize--

            // Locate the first element in run1 that should be merged.
            val k = gallopRight(a[base2], base1, len1)
            val newBase1 = base1 + k
            val newLen1 = len1 - k
            if (newLen1 == 0) return

            // Locate the last element in run2 that should be merged.
            val newLen2 = gallopLeft(a[newBase1 + newLen1 - 1], base2, len2)
            if (newLen2 == 0) return

            // Merge remaining runs; choose strategy based on run sizes.
            if (newLen1 <= newLen2)
                mergeLo(newBase1, newLen1, base2, newLen2)
            else
                mergeHi(newBase1, newLen1, base2, newLen2)
        }

        /**
         * Performs a gallop (binary search) to find the first index in a run
         * where [key] should be inserted.
         */
        private fun gallopRight(key: T, base: Int, len: Int): Int {
            var lo = 0
            var hi = len
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (c.compare(key, a[base + mid]) < 0) {
                    hi = mid
                } else {
                    lo = mid + 1
                }
            }
            return lo
        }

        /**
         * Performs a gallop (binary search) to find the first index in a run
         * where [key] should be inserted (searching from the left).
         */
        private fun gallopLeft(key: T, base: Int, len: Int): Int {
            var lo = 0
            var hi = len
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (c.compare(a[base + mid], key) < 0) {
                    lo = mid + 1
                } else {
                    hi = mid
                }
            }
            return lo
        }

        /**
         * Merges two runs where the first run is smaller.
         */
        private fun mergeLo(base1: Int, len1: Int, base2: Int, len2: Int) {
            // Copy first run into temporary array.
            tmp = ensureCapacity(len1)
            for (i in 0 until len1) {
                tmp[i] = a[base1 + i]
            }
            var i = 0
            var j = base2
            var dest = base1
            var remaining1 = len1
            var remaining2 = len2

            while (true) {
                if (c.compare(tmp[i]!!, a[j]) <= 0) {
                    a[dest++] = tmp[i]!!
                    i++
                    remaining1--
                    if (remaining1 == 0) break
                } else {
                    a[dest++] = a[j]
                    j++
                    remaining2--
                    if (remaining2 == 0) break
                }
            }
            // Copy any remaining elements from tmp.
            for (k in 0 until remaining1) {
                a[dest + k] = tmp[i + k]!!
            }
        }

        /**
         * Merges two runs where the second run is smaller.
         */
        private fun mergeHi(base1: Int, len1: Int, base2: Int, len2: Int) {
            // Copy second run into temporary array.
            tmp = ensureCapacity(len2)
            for (i in 0 until len2) {
                tmp[i] = a[base2 + i]
            }
            var i = base1 + len1 - 1
            var j = len2 - 1
            var dest = base2 + len2 - 1
            var remaining1 = len1
            var remaining2 = len2

            while (true) {
                if (c.compare(a[i], tmp[j]!!) > 0) {
                    a[dest--] = a[i]
                    i--
                    remaining1--
                    if (remaining1 == 0) break
                } else {
                    a[dest--] = tmp[j]!!
                    j--
                    remaining2--
                    if (remaining2 == 0) break
                }
            }
            // Copy any remaining elements from tmp.
            for (k in 0 until remaining2) {
                a[dest - k] = tmp[j - k]!!
            }
        }

        /**
         * Ensures that our temporary array has at least [minCapacity] elements.
         */
        private fun ensureCapacity(minCapacity: Int): Array<T?> {
            if (tmp.size < minCapacity) {
                var newSize = if (tmp.size == 0) 1 else tmp.size
                while (newSize < minCapacity) {
                    newSize *= 2
                }
                tmp = arrayOfNulls(newSize)
            }
            return tmp
        }
    }
}
