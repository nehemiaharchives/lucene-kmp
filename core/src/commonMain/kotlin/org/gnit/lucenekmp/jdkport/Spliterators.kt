package org.gnit.lucenekmp.jdkport

object Spliterators {


    /**
     * A Spliterator designed for use by sources that traverse and split
     * elements maintained in an unmodifiable Array.
     * Mimics java.util.Spliterators.ArraySpliterator.
     */
    class ArraySpliterator<T> : Spliterator<T> {
        // Use Array<Any?> to match Java's Object[] for flexibility,
        // elements will be cast on consumption.
        private val array: Array<Any?>
        private var index: Int        // current index, modified on advance/split
        private val fence: Int        // one past last index
        private val characteristics: Int
        private var estimatedSize: Long // if >= 0, the estimated size; if -1, exact size is fence - index

        /**
         * Creates a spliterator covering all of the given array.
         * Its size is known exactly and it reports SIZED and SUBSIZED.
         * @param array the array, assumed to be unmodified during use.
         * @param additionalCharacteristics Additional characteristics beyond SIZED/SUBSIZED.
         */
        constructor(array: Array<Any?>, additionalCharacteristics: Int)
                : this(array, 0, array.size, additionalCharacteristics)

        /**
         * Creates a spliterator covering the given array and range.
         * Its size is known exactly and it reports SIZED and SUBSIZED.
         * @param array the array, assumed to be unmodified during use.
         * @param origin the least index (inclusive) to cover.
         * @param fence one past the greatest index to cover.
         * @param additionalCharacteristics Additional characteristics beyond SIZED/SUBSIZED.
         */
        constructor(array: Array<Any?>, origin: Int, fence: Int, additionalCharacteristics: Int) {
            this.array = array
            this.index = origin
            this.fence = fence
            // Always add SIZED and SUBSIZED for this constructor
            this.characteristics = additionalCharacteristics or Spliterator.SIZED or Spliterator.SUBSIZED
            this.estimatedSize = -1 // Indicates exact size is known
        }

        /**
         * Private constructor for use by trySplit, potentially without SIZED/SUBSIZED.
         * @param array the array.
         * @param origin the least index (inclusive).
         * @param fence one past the greatest index.
         * @param characteristics characteristics (SIZED/SUBSIZED may be removed).
         * @param estimatedSize the estimated size (non-negative).
         */
        private constructor(array: Array<Any?>, origin: Int, fence: Int, characteristics: Int, estimatedSize: Long) {
            this.array = array
            this.index = origin
            this.fence = fence
            // Characteristics are passed directly (might not include SIZED/SUBSIZED)
            this.characteristics = characteristics
            this.estimatedSize = estimatedSize
        }

        @Suppress("UNCHECKED_CAST")
        override fun trySplit(): Spliterator<T>? {
            val lo = index
            val mid = (lo + fence) ushr 1 // Unsigned shift for average, avoids overflow
            if (lo >= mid) return null // Already too small to split

            // Decide which constructor to use based on whether the original size was exact
            return if (estimatedSize == -1L) {
                // Original size was exact, split retains exact size property
                // Create new spliterator for [lo, mid), update this one to [mid, fence)
                ArraySpliterator<T>(array, lo, mid, characteristics).also { index = mid }
            } else {
                // Original size was an estimate, split the estimate
                val prefixEst = estimatedSize ushr 1
                estimatedSize -= prefixEst
                // Create new spliterator for [lo, mid) with half the estimate, update this one
                ArraySpliterator<T>(array, lo, mid, characteristics, prefixEst).also { index = mid }
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun forEachRemaining(action: (T) -> Unit) {
            val a = array
            val hi = fence
            var i = index
            if (a.size >= hi && i >= 0 && i < hi) {
                index = hi // Consume all elements
                while (i < hi) {
                    // Perform unchecked cast for each element
                    action(a[i] as T)
                    i++
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun tryAdvance(action: (T) -> Unit): Boolean {
            if (index >= 0 && index < fence) {
                // Perform unchecked cast
                val element = array[index++] as T
                action(element)
                return true
            }
            return false
        }

        override fun estimateSize(): Long {
            // If estimatedSize is non-negative, return it, otherwise calculate exact remaining size
            return if (estimatedSize >= 0) estimatedSize else (fence - index).toLong()
        }

        override fun characteristics(): Int {
            return characteristics
        }

        override fun getComparator(): Comparator<in T>? {
            if (hasCharacteristics(Spliterator.SORTED)) {
                // ArraySpliterator itself doesn't know the sorting comparator
                return null
            }
            throw IllegalStateException("Spliterator does not report SORTED characteristic")
        }
    }
}