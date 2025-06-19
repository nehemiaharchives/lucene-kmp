package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.search.DocIdSet
import org.gnit.lucenekmp.search.DocIdSetIterator
import kotlin.math.min


internal class IntArrayDocIdSet(docs: IntArray, length: Int) : DocIdSet() {
    private val docs: IntArray
    private val length: Int

    init {
        require(docs[length] == DocIdSetIterator.NO_MORE_DOCS)
        this.docs = docs
        require(
            assertArraySorted(docs, length)
        ) {
            ("IntArrayDocIdSet need docs to be sorted"
                    + Arrays.toString(ArrayUtil.copyOfSubArray(docs, 0, length)))
        }
        this.length = length
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(docs)
    }

    override fun iterator(): DocIdSetIterator {
        return IntArrayDocIdSetIterator(docs, length)
    }

    internal class IntArrayDocIdSetIterator(private val docs: IntArray, private val length: Int) : DocIdSetIterator() {
        private var i = 0
        private var doc = -1

        override fun docID(): Int {
            return doc
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return docs[i++].also { doc = it }
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            var bound = 1
            // given that we use this for small arrays only, this is very unlikely to overflow
            while (i + bound < length && docs[i + bound] < target) {
                bound *= 2
            }
            i = Arrays.binarySearch(docs, i + bound / 2, min(i + bound + 1, length), target)
            if (i < 0) {
                i = -1 - i
            }
            return docs[i++].also { doc = it }
        }

        @Throws(IOException::class)
        override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
            if (doc >= upTo) {
                return
            }

            val from = i - 1
            val to = VectorUtil.findNextGEQ(docs, upTo, from, length)
            var idx = from
            while (idx < to) {
                bitSet.set(docs[idx] - offset)
                idx++
            }
            doc = docs[to]
            i = to + 1
        }

        override fun cost(): Long {
            return length.toLong()
        }
    }

    companion object {
        private val BASE_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(IntArrayDocIdSet::class)

        private fun assertArraySorted(docs: IntArray, length: Int): Boolean {
            for (i in 1..<length) {
                if (docs[i] < docs[i - 1]) {
                    return false
                }
            }
            return true
        }
    }
}
