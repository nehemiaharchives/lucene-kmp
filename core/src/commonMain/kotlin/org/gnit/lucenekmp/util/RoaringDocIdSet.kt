package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.search.DocIdSet
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.jdkport.assert
import kotlin.math.min

/**
 * [DocIdSet] implementation inspired from http://roaringbitmap.org/
 *
 *
 * The space is divided into blocks of 2^16 bits and each block is encoded independently. In each
 * block, if less than 2^12 bits are set, then documents are simply stored in a short[]. If more
 * than 2^16-2^12 bits are set, then the inverse of the set is encoded in a simple short[].
 * Otherwise a [FixedBitSet] is used.
 *
 * @lucene.internal
 */
class RoaringDocIdSet private constructor(docIdSets: Array<DocIdSet>, cardinality: Int) :
    DocIdSet() {
    /** A builder of [RoaringDocIdSet]s.  */
    class Builder(private val maxDoc: Int) {
        private val sets: Array<DocIdSet>

        private var cardinality = 0
        private var lastDocId: Int
        private var currentBlock: Int
        private var currentBlockCardinality = 0

        // We start by filling the buffer and when it's full we copy the content of
        // the buffer to the FixedBitSet and put further documents in that bitset
        private val buffer: ShortArray
        private var denseBuffer: FixedBitSet? = null

        /** Sole constructor.  */
        init {
            sets = kotlin.arrayOfNulls<DocIdSet>((maxDoc + (1 shl 16) - 1) ushr 16) as Array<DocIdSet>
            lastDocId = -1
            currentBlock = -1
            buffer = ShortArray(MAX_ARRAY_LENGTH)
        }

        private fun flush() {
            assert(currentBlockCardinality <= BLOCK_SIZE)
            if (currentBlockCardinality <= MAX_ARRAY_LENGTH) {
                // Use sparse encoding
                assert(denseBuffer == null)
                if (currentBlockCardinality > 0) {
                    sets[currentBlock] =
                        ShortArrayDocIdSet(
                            ArrayUtil.copyOfSubArray(
                                buffer,
                                0,
                                currentBlockCardinality
                            )
                        )
                }
            } else {
                checkNotNull(denseBuffer)
                assert(denseBuffer!!.cardinality() == currentBlockCardinality)
                if (denseBuffer!!.length() == BLOCK_SIZE
                    && BLOCK_SIZE - currentBlockCardinality < MAX_ARRAY_LENGTH
                ) {
                    // Doc ids are very dense, inverse the encoding
                    val excludedDocs = ShortArray(BLOCK_SIZE - currentBlockCardinality)
                    denseBuffer!!.flip(0, denseBuffer!!.length())
                    var excludedDoc = -1
                    for (i in excludedDocs.indices) {
                        excludedDoc = denseBuffer!!.nextSetBit(excludedDoc + 1)
                        assert(excludedDoc != NO_MORE_DOCS)
                        excludedDocs[i] = excludedDoc.toShort()
                    }
                    assert(
                        excludedDoc + 1 == denseBuffer!!.length()
                                || denseBuffer!!.nextSetBit(excludedDoc + 1) == NO_MORE_DOCS
                    )
                    sets[currentBlock] =
                        NotDocIdSet(BLOCK_SIZE, ShortArrayDocIdSet(excludedDocs))
                } else {
                    // Neither sparse nor super dense, use a fixed bit set
                    sets[currentBlock] =
                        BitDocIdSet(denseBuffer!!, currentBlockCardinality.toLong())
                }
                denseBuffer = null
            }

            cardinality += currentBlockCardinality
            denseBuffer = null
            currentBlockCardinality = 0
        }

        /** Add a new doc-id to this builder. NOTE: doc ids must be added in order.  */
        fun add(docId: Int): Builder {
            require(docId > lastDocId) { "Doc ids must be added in-order, got $docId which is <= lastDocID=$lastDocId" }
            val block = docId ushr 16
            if (block != currentBlock) {
                // we went to a different block, let's flush what we buffered and start from fresh
                flush()
                currentBlock = block
            }

            if (currentBlockCardinality < MAX_ARRAY_LENGTH) {
                buffer[currentBlockCardinality] = docId.toShort()
            } else {
                if (denseBuffer == null) {
                    // the buffer is full, let's move to a fixed bit set
                    val numBits = min(1 shl 16, maxDoc - (block shl 16))
                    denseBuffer = FixedBitSet(numBits)
                    for (doc in buffer) {
                        denseBuffer!!.set(doc.toInt() and 0xFFFF)
                    }
                }
                denseBuffer!!.set(docId and 0xFFFF)
            }

            lastDocId = docId
            currentBlockCardinality += 1
            return this
        }

        /** Add the content of the provided [DocIdSetIterator].  */
        @Throws(IOException::class)
        fun add(disi: DocIdSetIterator): Builder {
            var doc: Int = disi.nextDoc()
            while (doc != NO_MORE_DOCS) {
                add(doc)
                doc = disi.nextDoc()
            }
            return this
        }

        /** Build an instance.  */
        fun build(): RoaringDocIdSet {
            flush()
            return RoaringDocIdSet(sets, cardinality)
        }
    }

    /** [DocIdSet] implementation that can store documents up to 2^16-1 in a short[].  */
    private class ShortArrayDocIdSet(private val docIDs: ShortArray) : DocIdSet() {
        override fun ramBytesUsed(): Long {
            return BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(docIDs)
        }

        override fun iterator(): DocIdSetIterator {
            return object : DocIdSetIterator() {
                var i: Int = -1 // this is the index of the current document in the array
                var doc: Int = -1

                fun docId(i: Int): Int {
                    return docIDs[i].toInt() and 0xFFFF
                }

                override fun nextDoc(): Int {
                    if (++i >= docIDs.size) {
                        return NO_MORE_DOCS.also { doc = it }
                    }
                    return docId(i).also { doc = it }
                }

                override fun docID(): Int {
                    return doc
                }

                override fun cost(): Long {
                    return docIDs.size.toLong()
                }

                override fun advance(target: Int): Int {
                    // binary search
                    var lo = i + 1
                    var hi = docIDs.size - 1
                    while (lo <= hi) {
                        val mid = (lo + hi) ushr 1
                        val midDoc = docId(mid)
                        if (midDoc < target) {
                            lo = mid + 1
                        } else {
                            hi = mid - 1
                        }
                    }
                    if (lo == docIDs.size) {
                        i = docIDs.size
                        return NO_MORE_DOCS.also { doc = it }
                    } else {
                        i = lo
                        return docId(i).also { doc = it }
                    }
                }

                override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
                    if (doc >= upTo) {
                        return
                    }

                    val from = i
                    advance(upTo)
                    val to = i
                    for (i in from..<to) {
                        bitSet.set(docId(i) - offset)
                    }
                }
            }
        }

        companion object {
            private val BASE_RAM_BYTES_USED: Long =
                RamUsageEstimator.shallowSizeOfInstance(ShortArrayDocIdSet::class)
        }
    }

    private val docIdSets: Array<DocIdSet>
    private val cardinality: Int
    private val ramBytesUsed: Long

    init {
        this.docIdSets = docIdSets
        var ramBytesUsed: Long = BASE_RAM_BYTES_USED + RamUsageEstimator.shallowSizeOf(docIdSets)
        for (set in this.docIdSets) {
            if (set != null) {
                ramBytesUsed += set.ramBytesUsed()
            }
        }
        this.ramBytesUsed = ramBytesUsed
        this.cardinality = cardinality
    }

    override fun ramBytesUsed(): Long {
        return ramBytesUsed
    }

    override fun iterator(): DocIdSetIterator {
        if (cardinality == 0) {
            return DocIdSetIterator.empty() /*null*/ // java lucene returns null here but in other places returns an empty iterator, so I will go with this until I face problem for null safety API
        }
        return this.Iterator()
    }

    private inner class Iterator : DocIdSetIterator() {
        var block: Int
        var sub: DocIdSetIterator?
        var doc: Int

        init {
            doc = -1
            block = -1
            sub = empty()
        }

        override fun docID(): Int {
            return doc
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            val subNext: Int = sub!!.nextDoc()
            if (subNext == NO_MORE_DOCS) {
                return firstDocFromNextBlock()
            }
            return ((block shl 16) or subNext).also { doc = it }
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            val targetBlock = target ushr 16
            if (targetBlock != block) {
                block = targetBlock
                if (block >= docIdSets.size) {
                    sub = null
                    return NO_MORE_DOCS.also { doc = it }
                }
                if (docIdSets[block] == null) {
                    return firstDocFromNextBlock()
                }
                sub = docIdSets[block].iterator()
            }
            val subNext: Int = sub!!.advance(target and 0xFFFF)
            if (subNext == NO_MORE_DOCS) {
                return firstDocFromNextBlock()
            }
            return ((block shl 16) or subNext).also { doc = it }
        }

        @Throws(IOException::class)
        fun firstDocFromNextBlock(): Int {
            while (true) {
                block += 1
                if (block >= docIdSets.size) {
                    sub = null
                    return NO_MORE_DOCS.also { doc = it }
                } else if (docIdSets[block] != null) {
                    sub = docIdSets[block].iterator()
                    val subNext: Int = sub!!.nextDoc()
                    assert(subNext != NO_MORE_DOCS)
                    return ((block shl 16) or subNext).also { doc = it }
                }
            }
        }

        @Throws(IOException::class)
        override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
            while (true) {
                val subUpto = upTo - (block shl 16)
                if (subUpto < 0) {
                    break
                }
                val subOffset = offset - (block shl 16)
                sub!!.intoBitSet(subUpto, bitSet, subOffset)
                if (sub!!.docID() == NO_MORE_DOCS) {
                    if (firstDocFromNextBlock() == NO_MORE_DOCS) {
                        break
                    }
                } else {
                    doc = (block shl 16) or sub!!.docID()
                    break
                }
            }
        }

        override fun cost(): Long {
            return cardinality.toLong()
        }
    }

    /** Return the exact number of documents that are contained in this set.  */
    fun cardinality(): Int {
        return cardinality
    }

    override fun toString(): String {
        return "RoaringDocIdSet(cardinality=$cardinality)"
    }

    companion object {
        // Number of documents in a block
        private const val BLOCK_SIZE = 1 shl 16

        // The maximum length for an array, beyond that point we switch to a bitset
        private const val MAX_ARRAY_LENGTH = 1 shl 12
        private val BASE_RAM_BYTES_USED: Long =
            RamUsageEstimator.shallowSizeOfInstance(RoaringDocIdSet::class)
    }
}
