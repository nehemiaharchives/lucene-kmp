package org.gnit.lucenekmp.util


/**
 * A LSB Radix sorter for unsigned int values.
 *
 * @lucene.internal
 */
class LSBRadixSorter {
    private val histogram = IntArray(HISTOGRAM_SIZE)
    private var buffer = IntArray(0)

    /**
     * Sort `array[0:len]` in place.
     *
     * @param numBits how many bits are required to store any of the values in `array[0:len]`.
     * Pass `32` if unknown.
     */
    fun sort(numBits: Int, array: IntArray, len: Int) {
        if (len < INSERTION_SORT_THRESHOLD) {
            insertionSort(array, 0, len)
            return
        }

        buffer = ArrayUtil.growNoCopy(buffer, len)

        var arr = array

        var buf = buffer

        var shift = 0
        while (shift < numBits) {
            if (sort(arr, len, histogram, shift, buf)) {
                // swap arrays
                val tmp = arr
                arr = buf
                buf = tmp
            }
            shift += 8
        }

        if (array == buf) {
            /*java.lang.System.arraycopy(arr, 0, array, 0, len)*/
            arr.copyInto(
                destination = array,
                destinationOffset = 0,
                startIndex = 0,
                endIndex = len
            )
        }
    }

    companion object {
        private const val INSERTION_SORT_THRESHOLD = 30
        private const val HISTOGRAM_SIZE = 256

        private fun buildHistogram(array: IntArray, len: Int, histogram: IntArray, shift: Int) {
            for (i in 0..<len) {
                val b = (array[i] ushr shift) and 0xFF
                histogram[b] += 1
            }
        }

        private fun sumHistogram(histogram: IntArray) {
            var accum = 0
            for (i in 0..<HISTOGRAM_SIZE) {
                val count = histogram[i]
                histogram[i] = accum
                accum += count
            }
        }

        private fun reorder(array: IntArray, len: Int, histogram: IntArray, shift: Int, dest: IntArray) {
            for (i in 0..<len) {
                val v = array[i]
                val b = (v ushr shift) and 0xFF
                dest[histogram[b]++] = v
            }
        }

        private fun sort(array: IntArray, len: Int, histogram: IntArray, shift: Int, dest: IntArray): Boolean {
            /*java.util.Arrays.fill(histogram, 0)*/
            histogram.fill(0)
            buildHistogram(array, len, histogram, shift)
            if (histogram[0] == len) {
                return false
            }
            sumHistogram(histogram)
            reorder(array, len, histogram, shift, dest)
            return true
        }

        private fun insertionSort(array: IntArray, off: Int, len: Int) {
            var i = off + 1
            val end = off + len
            while (i < end) {
                for (j in i downTo off + 1) {
                    if (array[j - 1] > array[j]) {
                        val tmp = array[j - 1]
                        array[j - 1] = array[j]
                        array[j] = tmp
                    } else {
                        break
                    }
                }
                ++i
            }
        }
    }
}
