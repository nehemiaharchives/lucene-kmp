package org.gnit.lucenekmp.util


/**
 * Stable radix sorter for variable-length strings.
 *
 * @lucene.internal
 */
abstract class StableMSBRadixSorter(maxLength: Int) : MSBRadixSorter(maxLength) {
    private val fixedStartOffsets: IntArray = IntArray(HISTOGRAM_SIZE)

    /** Save the i-th value into the j-th position in temporary storage.  */
    protected abstract fun save(i: Int, j: Int)

    /** Restore values between i-th and j-th(excluding) in temporary storage into original storage.  */
    protected abstract fun restore(i: Int, j: Int)

    override fun getFallbackSorter(k: Int): Sorter? {
        return object : MergeSorter() {
            override fun save(i: Int, j: Int) {
                this@StableMSBRadixSorter.save(i, j)
            }

            override fun restore(i: Int, j: Int) {
                this@StableMSBRadixSorter.restore(i, j)
            }

            override fun swap(i: Int, j: Int) {
                this@StableMSBRadixSorter.swap(i, j)
            }

            override fun compare(i: Int, j: Int): Int {
                for (o in k..<maxLength) {
                    val b1 = byteAt(i, o)
                    val b2 = byteAt(j, o)
                    if (b1 != b2) {
                        return b1 - b2
                    } else if (b1 == -1) {
                        break
                    }
                }
                return 0
            }
        }
    }

    /**
     * Reorder elements in stable way, since Dutch sort does not guarantee ordering for same values.
     *
     *
     * When this method returns, startOffsets and endOffsets are equal.
     */
    override fun reorder(from: Int, to: Int, startOffsets: IntArray, endOffsets: IntArray, k: Int) {
        /*java.lang.System.arraycopy(startOffsets, 0, fixedStartOffsets, 0, startOffsets.size)*/
        startOffsets.copyInto(
            destination = fixedStartOffsets,
            destinationOffset = 0,
            startIndex = 0,
            endIndex = startOffsets.size
        )
        for (i in 0..<HISTOGRAM_SIZE) {
            val limit = endOffsets[i]
            for (h1 in fixedStartOffsets[i]..<limit) {
                val b = getBucket(from + h1, k)
                val h2: Int = startOffsets[b]++
                save(from + h1, from + h2)
            }
        }
        restore(from, to)
    }

    /** A MergeSorter taking advantage of temporary storage.  */
    abstract class MergeSorter : Sorter() {
        override fun sort(from: Int, to: Int) {
            checkRange(from, to)
            mergeSort(from, to)
        }

        private fun mergeSort(from: Int, to: Int) {
            if (to - from < BINARY_SORT_THRESHOLD) {
                binarySort(from, to)
            } else {
                val mid = (from + to) ushr 1
                mergeSort(from, mid)
                mergeSort(mid, to)
                merge(from, to, mid)
            }
        }

        /** Save the i-th value into the j-th position in temporary storage.  */
        protected abstract fun save(i: Int, j: Int)

        /**
         * Restore values between i-th and j-th(excluding) in temporary storage into original storage.
         */
        protected abstract fun restore(i: Int, j: Int)

        /**
         * We tried to expose this to implementations to get a bulk copy optimization. But it did not
         * bring a noticeable improvement in benchmark as `len` is usually small.
         */
        private fun bulkSave(from: Int, tmpFrom: Int, len: Int) {
            for (i in 0..<len) {
                save(from + i, tmpFrom + i)
            }
        }

        private fun merge(from: Int, to: Int, mid: Int) {
            require(to > mid && mid > from)
            if (compare(mid - 1, mid) <= 0) {
                // already sorted.
                return
            }
            var left = from
            var right = mid
            var index = from
            while (true) {
                val cmp = compare(left, right)
                if (cmp <= 0) {
                    save(left++, index++)
                    if (left == mid) {
                        require(index == right)
                        bulkSave(right, index, to - right)
                        break
                    }
                } else {
                    save(right++, index++)
                    if (right == to) {
                        require(to - index == mid - left)
                        bulkSave(left, index, mid - left)
                        break
                    }
                }
            }
            restore(from, to)
        }
    }
}
