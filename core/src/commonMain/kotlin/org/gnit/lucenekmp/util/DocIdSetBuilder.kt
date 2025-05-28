package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.search.DocIdSet
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.packed.PackedInts
import kotlin.jvm.JvmRecord
import kotlin.math.max
import kotlin.math.min


/**
 * A builder of [DocIdSet]s. At first it uses a sparse structure to gather documents, and then
 * upgrades to a non-sparse bit set once enough hits match.
 *
 *
 * To add documents, you first need to call [.grow] in order to reserve space, and then
 * call [BulkAdder.add] on the returned [BulkAdder].
 *
 * @lucene.internal
 */
class DocIdSetBuilder internal constructor(private val maxDoc: Int, docCount: Int, valueCount: Long) {
    /**
     * Utility class to efficiently add many docs in one go.
     *
     * @see DocIdSetBuilder.grow
     */
    interface BulkAdder {
        fun add(doc: Int)

        fun add(docs: IntsRef)

        @Throws(IOException::class)
        fun add(iterator: DocIdSetIterator)
    }

    @JvmRecord
    private data class FixedBitSetAdder(val bitSet: FixedBitSet) : BulkAdder {
        override fun add(doc: Int) {
            bitSet!!.set(doc)
        }

        override fun add(docs: IntsRef) {
            for (i in 0..<docs.length) {
                bitSet!!.set(docs.ints[docs.offset + i])
            }
        }

        @Throws(IOException::class)
        override fun add(iterator: DocIdSetIterator) {
            bitSet.or(iterator)
        }
    }

    private class Buffer {
        var array: IntArray
        var length: Int

        internal constructor(length: Int) {
            this.array = IntArray(length)
            this.length = 0
        }

        internal constructor(array: IntArray, length: Int) {
            this.array = array
            this.length = length
        }
    }

    @JvmRecord
    private data class BufferAdder(val buffer: Buffer) : BulkAdder {
        override fun add(doc: Int) {
            buffer!!.array[buffer.length++] = doc
        }

        override fun add(docs: IntsRef) {
            /*java.lang.System.arraycopy(docs.ints, docs.offset, buffer!!.array, buffer.length, docs.length)*/
            docs.ints.copyInto(
                destination = buffer!!.array,
                destinationOffset = buffer.length,
                startIndex = docs.offset,
                endIndex = docs.offset + docs.length
            )
            buffer.length += docs.length
        }

        @Throws(IOException::class)
        override fun add(iterator: DocIdSetIterator) {
            var docID: Int
            while ((iterator.nextDoc().also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                add(docID)
            }
        }
    }

    private val threshold: Int

    // pkg-private for testing
    val multivalued: Boolean
    var numValuesPerDoc: Double = 0.0

    private var buffers: MutableList<Buffer>? = null
    private var totalAllocated = 0 // accumulated size of the allocated buffers

    private var bitSet: FixedBitSet?

    private var counter: Long = -1
    private lateinit var adder: BulkAdder

    /** Create a builder that can contain doc IDs between `0` and `maxDoc`.  */
    constructor(maxDoc: Int) : this(maxDoc, -1, -1)

    /**
     * Create a [DocIdSetBuilder] instance that is optimized for accumulating docs that match
     * the given [Terms].
     */
    constructor(maxDoc: Int, terms: Terms) : this(maxDoc, terms.docCount, terms.sumDocFreq)

    /**
     * Create a [DocIdSetBuilder] instance that is optimized for accumulating docs that match
     * the given [PointValues].
     */
    constructor(maxDoc: Int, values: PointValues) : this(maxDoc, values.docCount, values.size())

    init {
        this.multivalued = docCount < 0 || docCount.toLong() != valueCount
        if (docCount <= 0 || valueCount < 0) {
            // assume one value per doc, this means the cost will be overestimated
            // if the docs are actually multi-valued
            this.numValuesPerDoc = 1.0
        } else {
            // otherwise compute from index stats
            this.numValuesPerDoc = valueCount.toDouble() / docCount
        }

        require(numValuesPerDoc >= 1) { "valueCount=$valueCount docCount=$docCount" }

        // For ridiculously small sets, we'll just use a sorted int[]
        // maxDoc >>> 7 is a good value if you want to save memory, lower values
        // such as maxDoc >>> 11 should provide faster building but at the expense
        // of using a full bitset even for quite sparse data
        this.threshold = maxDoc ushr 7

        this.bitSet = null
    }

    /**
     * Add the content of the provided [DocIdSetIterator] to this builder. NOTE: if you need to
     * build a [DocIdSet] out of a single [DocIdSetIterator], you should rather use [ ].
     */
    @Throws(IOException::class)
    fun add(iter: DocIdSetIterator) {
        val cost = kotlin.math.min(Int.Companion.MAX_VALUE.toLong(), iter.cost()).toInt()
        val adder = grow(cost)
        if (bitSet != null) {
            bitSet!!.or(iter)
            return
        }
        for (i in 0..<cost) {
            val doc: Int = iter.nextDoc()
            if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                return
            }
            adder.add(doc)
        }
        var doc: Int = iter.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            grow(1).add(doc)
            doc = iter.nextDoc()
        }
    }

    /**
     * Reserve space and return a [BulkAdder] object that can be used to add up to `numDocs` documents.
     */
    fun grow(numDocs: Int): BulkAdder {
        if (bitSet == null) {
            if (totalAllocated.toLong() + numDocs <= threshold) {
                ensureBufferCapacity(numDocs)
            } else {
                upgradeToBitSet()
                counter += numDocs.toLong()
            }
        } else {
            counter += numDocs.toLong()
        }
        return adder!!
    }

    private fun ensureBufferCapacity(numDocs: Int) {
        if (buffers!!.isEmpty()) {
            addBuffer(additionalCapacity(numDocs))
            return
        }

        val current = buffers!!.get(buffers!!.size - 1)
        if (current.array.size - current.length >= numDocs) {
            // current buffer is large enough
            return
        }
        if (current.length < current.array.size - (current.array.size ushr 3)) {
            // current buffer is less than 7/8 full, resize rather than waste space
            growBuffer(current, additionalCapacity(numDocs))
        } else {
            addBuffer(additionalCapacity(numDocs))
        }
    }

    private fun additionalCapacity(numDocs: Int): Int {
        // exponential growth: the new array has a size equal to the sum of what
        // has been allocated so far
        var c = totalAllocated
        // but is also >= numDocs + 1 so that we can store the next batch of docs
        // (plus an empty slot so that we are more likely to reuse the array in build())
        c = max(numDocs + 1, c)
        // avoid cold starts
        c = max(32, c)
        // do not go beyond the threshold
        c = min(threshold - totalAllocated, c)
        return c
    }

    private fun addBuffer(len: Int): Buffer {
        val buffer = Buffer(len)
        buffers!!.add(buffer)
        adder = BufferAdder(buffer)
        totalAllocated += buffer.array.size
        return buffer
    }

    private fun growBuffer(buffer: Buffer, additionalCapacity: Int) {
        buffer.array = ArrayUtil.growExact(buffer.array, buffer.array.size + additionalCapacity)
        totalAllocated += additionalCapacity
    }

    private fun upgradeToBitSet() {
        require(bitSet == null)
        val bitSet = FixedBitSet(maxDoc)
        var counter: Long = 0
        for (buffer in buffers!!) {
            val array = buffer.array
            val length = buffer.length
            counter += length.toLong()
            for (i in 0..<length) {
                bitSet.set(array[i])
            }
        }
        this.bitSet = bitSet
        this.counter = counter
        this.buffers = mutableListOf()
        this.adder = FixedBitSetAdder(bitSet)
    }

    /** Build a [DocIdSet] from the accumulated doc IDs.  */
    fun build(): DocIdSet {
        try {
            if (bitSet != null) {
                require(counter >= 0)
                val cost: Long = kotlin.math.round(counter.toDouble() / numValuesPerDoc).toLong()
                return BitDocIdSet(bitSet!!, cost)
            } else {
                val concatenated = concat(buffers!!)
                val sorter = LSBRadixSorter()
                sorter.sort(PackedInts.bitsRequired(maxDoc - 1L), concatenated.array, concatenated.length)
                val l: Int
                if (multivalued) {
                    l = dedup(concatenated.array, concatenated.length)
                } else {
                    require(noDups(concatenated.array, concatenated.length))
                    l = concatenated.length
                }
                require(l <= concatenated.length)
                concatenated.array[l] = DocIdSetIterator.NO_MORE_DOCS
                return IntArrayDocIdSet(concatenated.array, l)
            }
        } finally {
            this.buffers = null
            this.bitSet = null
        }
    }

    companion object {
        /**
         * Concatenate the buffers in any order, leaving at least one empty slot in the end NOTE: this
         * method might reuse one of the arrays
         */
        private fun concat(buffers: MutableList<Buffer>): Buffer {
            var totalLength = 0
            var largestBuffer: Buffer? = null
            for (buffer in buffers) {
                totalLength += buffer.length
                if (largestBuffer == null || buffer.array.size > largestBuffer.array.size) {
                    largestBuffer = buffer
                }
            }
            if (largestBuffer == null) {
                return Buffer(1)
            }
            var docs = largestBuffer.array
            if (docs.size < totalLength + 1) {
                docs = ArrayUtil.growExact(docs, totalLength + 1)
            }
            totalLength = largestBuffer.length
            for (buffer in buffers) {
                if (buffer !== largestBuffer) {
                    /*java.lang.System.arraycopy(buffer.array, 0, docs, totalLength, buffer.length)*/
                    buffer.array.copyInto(
                        destination = docs,
                        destinationOffset = totalLength,
                        startIndex = 0,
                        endIndex = buffer.length
                    )
                    totalLength += buffer.length
                }
            }
            return Buffer(docs, totalLength)
        }

        private fun dedup(arr: IntArray, length: Int): Int {
            if (length == 0) {
                return 0
            }
            var l = 1
            var previous = arr[0]
            for (i in 1..<length) {
                val value = arr[i]
                require(value >= previous)
                if (value != previous) {
                    arr[l++] = value
                    previous = value
                }
            }
            return l
        }

        private fun noDups(a: IntArray, len: Int): Boolean {
            for (i in 1..<len) {
                require(a[i - 1] < a[i])
            }
            return true
        }
    }
}
