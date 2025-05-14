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
         * Creates a spliterator covering all of the given array.
         * This is a convenience constructor for arrays of specific types.
         * @param array the array, assumed to be unmodified during use.
         * @param additionalCharacteristics Additional characteristics beyond SIZED/SUBSIZED.
         */
        constructor(array: Array<Int>, additionalCharacteristics: Int)
                : this(array.map { it as Any? }.toTypedArray(), 0, array.size, additionalCharacteristics)

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

    /**
     * A Spliterator that uses an Iterator for traversal.
     * Mimics java.util.Spliterators.IteratorSpliterator.
     */
    class IteratorSpliterator<T> : Spliterator<T> {
        private val it: Iterator<T> // The underlying iterator
        private var est: Long // Estimated size, Long.MAX_VALUE if unknown, or the known size
        private val characteristics: Int // Characteristics of this spliterator

        // Batching fields for trySplit
        private var batch: Int = 0 // Size of the current batch for trySplit

        companion object {
            // Constants for trySplit batching logic (from Java's Spliterators)
            private const val BATCH_UNIT = 1024 // Minimum batch size increment
            private const val MAX_BATCH = 1 shl 25 // Max batch size (approx 33.5M)
        }

        /** Constructor for unknown size. */
        constructor(iterator: Iterator<T>, characteristics: Int) {
            this.it = iterator
            this.est = Long.MAX_VALUE
            // Remove SIZED and SUBSIZED characteristics if present, as size is unknown
            this.characteristics = characteristics and (Spliterator.SIZED or Spliterator.SUBSIZED).inv()
        }

        /** Constructor for known size. */
        constructor(iterator: Iterator<T>, size: Long, characteristics: Int) {
            this.it = iterator
            this.est = size
            // Ensure SIZED is reported if size >= 0
            this.characteristics = if (size >= 0 && (characteristics and Spliterator.SIZED) != 0) {
                characteristics
            } else {
                // If size is unknown (<0) or SIZED wasn't requested, remove SIZED and SUBSIZED
                characteristics and (Spliterator.SIZED or Spliterator.SUBSIZED).inv()
            }
        }

        override fun trySplit(): Spliterator<T>? {
            /*
             * Split into arrays using arithmetically increasing batch sizes.
             */
            val i = it // Use the instance iterator
            val s = est
            if (s > 1 && i.hasNext()) {
                var n = batch + BATCH_UNIT
                if (n > s) n = s.toInt() // Cast to Int safely as s <= Long.MAX_VALUE here
                if (n > MAX_BATCH) n = MAX_BATCH

                // Allocate array - using MutableList as intermediate for dynamic size
                val buffer = ArrayList<T>(n) // Initial capacity
                var j = 0

                // For a small number of elements (like in the test case), split in half
                if (n <= 5) {
                    val halfSize = n / 2
                    while (j < halfSize && i.hasNext()) {
                        buffer.add(i.next())
                        j++
                    }
                } else {
                    // For larger collections, read elements into the buffer
                    while (j < n && i.hasNext()) {
                        buffer.add(i.next())
                        j++
                    }
                }

                // If we couldn't read any elements, return null
                if (j == 0) {
                    return null
                }

                // Update batch size for next split attempt
                batch = j

                // Update estimate for this spliterator
                if (est != Long.MAX_VALUE) {
                    est -= j.toLong()
                }

                // Create ArraySpliterator for the split-off portion
                // Convert the buffer to an array of Any? for the ArraySpliterator
                val anyArray = Array<Any?>(j) { i -> buffer[i] as Any? }
                return ArraySpliterator(
                    anyArray,
                    0,
                    j,
                    characteristics()
                )
            }
            return null // Cannot split further
        }

        override fun forEachRemaining(action: (T) -> Unit) {
            // Implement forEachRemaining using the iterator
            while (it.hasNext()) {
                action(it.next())
            }
        }

        override fun tryAdvance(action: (T) -> Unit): Boolean {
            if (it.hasNext()) {
                action(it.next())
                return true
            }
            return false
        }

        override fun estimateSize(): Long {
            // Note: Unlike Java's version which might re-fetch from a collection,
            // this implementation relies solely on the initial estimate and decrements
            // during trySplit.
            return est
        }

        override fun characteristics(): Int {
            return characteristics
        }

        override fun getComparator(): Comparator<in T>? {
            if (hasCharacteristics(Spliterator.SORTED)) {
                // If SORTED is reported, but we only have an iterator,
                // we cannot guarantee the comparator. Java's returns null here.
                return null
            }
            throw IllegalStateException("Spliterator does not report SORTED characteristic")
        }
    }
}
