package org.gnit.lucenekmp.util.bkd

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.DocBaseBitSetIterator
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.LongsRef
import kotlin.math.max
import kotlin.math.min

internal class DocIdsWriter(maxPointsInLeaf: Int) {
    private val scratch: IntArray = IntArray(maxPointsInLeaf)
    private val scratchLongs: LongsRef = LongsRef()

    /**
     * IntsRef to be used to iterate over the scratch buffer. A single instance is reused to avoid
     * re-allocating the object. The ints and length fields need to be reset each use.
     *
     *
     * The main reason for existing is to be able to call the [ ][IntersectVisitor.visit] method rather than the [IntersectVisitor.visit]
     * method. This seems to make a difference in performance, probably due to fewer virtual calls
     * then happening (once per read call rather than once per doc).
     */
    private val scratchIntsRef: IntsRef = IntsRef()

    init {
        // This is here to not rely on the default constructor of IntsRef to set offset to 0
        scratchIntsRef.offset = 0
    }

    @Throws(IOException::class)
    fun writeDocIds(docIds: IntArray, start: Int, count: Int, out: DataOutput) {
        // docs can be sorted either when all docs in a block have the same value
        // or when a segment is sorted
        var strictlySorted = true
        var min = docIds[0]
        var max = docIds[0]
        for (i in 1..<count) {
            val last = docIds[start + i - 1]
            val current = docIds[start + i]
            if (last >= current) {
                strictlySorted = false
            }
            min = min(min, current)
            max = max(max, current)
        }

        val min2max = max - min + 1
        if (strictlySorted) {
            if (min2max == count) {
                // continuous ids, typically happens when segment is sorted
                out.writeByte(CONTINUOUS_IDS)
                out.writeVInt(docIds[start])
                return
            } else if (min2max <= (count shl 4)) {
                require(min2max > count) { "min2max: $min2max, count: $count" }
                // Only trigger bitset optimization when max - min + 1 <= 16 * count in order to avoid
                // expanding too much storage.
                // A field with lower cardinality will have higher probability to trigger this optimization.
                out.writeByte(BITSET_IDS)
                writeIdsAsBitSet(docIds, start, count, out)
                return
            }
        }

        if (min2max <= 0xFFFF) {
            out.writeByte(DELTA_BPV_16)
            for (i in 0..<count) {
                scratch[i] = docIds[start + i] - min
            }
            out.writeVInt(min)
            val halfLen = count ushr 1
            for (i in 0..<halfLen) {
                scratch[i] = scratch[halfLen + i] or (scratch[i] shl 16)
            }
            for (i in 0..<halfLen) {
                out.writeInt(scratch[i])
            }
            if ((count and 1) == 1) {
                out.writeShort(scratch[count - 1].toShort())
            }
        } else {
            if (max <= 0xFFFFFF) {
                out.writeByte(BPV_24)
                // write them the same way we are reading them.
                var i = 0
                while (i < count - 7) {
                    val doc1 = docIds[start + i]
                    val doc2 = docIds[start + i + 1]
                    val doc3 = docIds[start + i + 2]
                    val doc4 = docIds[start + i + 3]
                    val doc5 = docIds[start + i + 4]
                    val doc6 = docIds[start + i + 5]
                    val doc7 = docIds[start + i + 6]
                    val doc8 = docIds[start + i + 7]
                    val l1 =
                        (doc1.toLong() and 0xffffffL) shl 40 or ((doc2.toLong() and 0xffffffL) shl 16) or ((doc3 ushr 8).toLong() and 0xffffL)
                    val l2 =
                        ((doc3.toLong() and 0xffL) shl 56 or ((doc4.toLong() and 0xffffffL) shl 32
                                ) or ((doc5.toLong() and 0xffffffL) shl 8
                                ) or ((doc6 shr 16).toLong() and 0xffL))
                    val l3 =
                        (doc6.toLong() and 0xffffL) shl 48 or ((doc7.toLong() and 0xffffffL) shl 24) or (doc8.toLong() and 0xffffffL)
                    out.writeLong(l1)
                    out.writeLong(l2)
                    out.writeLong(l3)
                    i += 8
                }
                while (i < count) {
                    out.writeShort((docIds[start + i] ushr 8).toShort())
                    out.writeByte(docIds[start + i].toByte())
                    ++i
                }
            } else {
                out.writeByte(BPV_32)
                for (i in 0..<count) {
                    out.writeInt(docIds[start + i])
                }
            }
        }
    }

    /** Read `count` integers into `docIDs`.  */
    @Throws(IOException::class)
    fun readInts(`in`: IndexInput, count: Int, docIDs: IntArray) {
        val bpv: Byte = `in`.readByte()
        when (bpv) {
            CONTINUOUS_IDS -> readContinuousIds(`in`, count, docIDs)
            BITSET_IDS -> readBitSet(`in`, count, docIDs)
            DELTA_BPV_16 -> readDelta16(`in`, count, docIDs)
            BPV_24 -> readInts24(`in`, count, docIDs)
            BPV_32 -> readInts32(`in`, count, docIDs)
            LEGACY_DELTA_VINT -> readLegacyDeltaVInts(`in`, count, docIDs)
            else -> throw IOException("Unsupported number of bits per value: $bpv")
        }
    }

    @Throws(IOException::class)
    private fun readBitSetIterator(`in`: IndexInput, count: Int): DocIdSetIterator {
        val offsetWords: Int = `in`.readVInt()
        val longLen: Int = `in`.readVInt()
        scratchLongs.longs = ArrayUtil.growNoCopy(scratchLongs.longs, longLen)
        `in`.readLongs(scratchLongs.longs, 0, longLen)
        // make ghost bits clear for FixedBitSet.
        if (longLen < scratchLongs.length) {
            Arrays.fill(scratchLongs.longs, longLen, scratchLongs.longs.size, 0)
        }
        scratchLongs.length = longLen
        val bitSet = FixedBitSet(scratchLongs.longs, longLen shl 6)
        return DocBaseBitSetIterator(bitSet, count.toLong(), offsetWords shl 6)
    }

    @Throws(IOException::class)
    private fun readBitSet(`in`: IndexInput, count: Int, docIDs: IntArray) {
        val iterator: DocIdSetIterator = readBitSetIterator(`in`, count)
        var docId: Int
        var pos = 0
        while ((iterator.nextDoc().also { docId = it }) != DocIdSetIterator.NO_MORE_DOCS) {
            docIDs[pos++] = docId
        }
        require(pos == count) { "pos: $pos, count: $count" }
    }

    /**
     * Read `count` integers and feed the result directly to [ ][IntersectVisitor.visit].
     */
    @Throws(IOException::class)
    fun readInts(`in`: IndexInput, count: Int, visitor: IntersectVisitor) {
        val bpv: Byte = `in`.readByte()
        when (bpv) {
            CONTINUOUS_IDS -> readContinuousIds(`in`, count, visitor)
            BITSET_IDS -> readBitSet(`in`, count, visitor)
            DELTA_BPV_16 -> readDelta16(`in`, count, visitor)
            BPV_24 -> readInts24(`in`, count, visitor)
            BPV_32 -> readInts32(`in`, count, visitor)
            LEGACY_DELTA_VINT -> readLegacyDeltaVInts(`in`, count, visitor)
            else -> throw IOException("Unsupported number of bits per value: $bpv")
        }
    }

    @Throws(IOException::class)
    private fun readBitSet(`in`: IndexInput, count: Int, visitor: IntersectVisitor) {
        val bitSetIterator: DocIdSetIterator = readBitSetIterator(`in`, count)
        visitor.visit(bitSetIterator)
    }

    @Throws(IOException::class)
    private fun readDelta16(`in`: IndexInput, count: Int, visitor: IntersectVisitor) {
        readDelta16(`in`, count, scratch)
        scratchIntsRef.ints = scratch
        scratchIntsRef.length = count
        visitor.visit(scratchIntsRef)
    }

    @Throws(IOException::class)
    private fun readInts24(`in`: IndexInput, count: Int, visitor: IntersectVisitor) {
        readInts24(`in`, count, scratch)
        scratchIntsRef.ints = scratch
        scratchIntsRef.length = count
        visitor.visit(scratchIntsRef)
    }

    @Throws(IOException::class)
    private fun readInts32(`in`: IndexInput, count: Int, visitor: IntersectVisitor) {
        `in`.readInts(scratch, 0, count)
        scratchIntsRef.ints = scratch
        scratchIntsRef.length = count
        visitor.visit(scratchIntsRef)
    }

    companion object {
        private const val CONTINUOUS_IDS: Byte = -2
        private const val BITSET_IDS: Byte = -1
        private const val DELTA_BPV_16: Byte = 16
        private const val BPV_24: Byte = 24
        private const val BPV_32: Byte = 32

        // These signs are legacy, should no longer be used in the writing side.
        private const val LEGACY_DELTA_VINT: Byte = 0

        @Throws(IOException::class)
        private fun writeIdsAsBitSet(docIds: IntArray, start: Int, count: Int, out: DataOutput) {
            val min = docIds[start]
            val max = docIds[start + count - 1]

            val offsetWords = min shr 6
            val offsetBits = offsetWords shl 6
            val totalWordCount: Int = FixedBitSet.bits2words(max - offsetBits + 1)
            var currentWord: Long = 0
            var currentWordIndex = 0

            out.writeVInt(offsetWords)
            out.writeVInt(totalWordCount)
            // build bit set streaming
            for (i in 0..<count) {
                val index = docIds[start + i] - offsetBits
                val nextWordIndex = index shr 6
                require(currentWordIndex <= nextWordIndex)
                if (currentWordIndex < nextWordIndex) {
                    out.writeLong(currentWord)
                    currentWord = 0L
                    currentWordIndex++
                    while (currentWordIndex < nextWordIndex) {
                        currentWordIndex++
                        out.writeLong(0L)
                    }
                }
                currentWord = currentWord or (1L shl index)
            }
            out.writeLong(currentWord)
            require(currentWordIndex + 1 == totalWordCount)
        }

        @Throws(IOException::class)
        private fun readContinuousIds(`in`: IndexInput, count: Int, docIDs: IntArray) {
            val start: Int = `in`.readVInt()
            for (i in 0..<count) {
                docIDs[i] = start + i
            }
        }

        @Throws(IOException::class)
        private fun readLegacyDeltaVInts(`in`: IndexInput, count: Int, docIDs: IntArray) {
            var doc = 0
            for (i in 0..<count) {
                doc += `in`.readVInt()
                docIDs[i] = doc
            }
        }

        @Throws(IOException::class)
        private fun readDelta16(`in`: IndexInput, count: Int, docIDs: IntArray) {
            val min: Int = `in`.readVInt()
            val halfLen = count ushr 1
            `in`.readInts(docIDs, 0, halfLen)
            for (i in 0..<halfLen) {
                val l = docIDs[i]
                docIDs[i] = (l ushr 16) + min
                docIDs[halfLen + i] = (l and 0xFFFF) + min
            }
            if ((count and 1) == 1) {
                docIDs[count - 1] = Short.toUnsignedInt(`in`.readShort()) + min
            }
        }

        @Throws(IOException::class)
        private fun readInts24(`in`: IndexInput, count: Int, docIDs: IntArray) {
            var i = 0
            while (i < count - 7) {
                val l1: Long = `in`.readLong()
                val l2: Long = `in`.readLong()
                val l3: Long = `in`.readLong()
                docIDs[i] = (l1 ushr 40).toInt()
                docIDs[i + 1] = (l1 ushr 16).toInt() and 0xffffff
                docIDs[i + 2] = (((l1 and 0xffffL) shl 8) or (l2 ushr 56)).toInt()
                docIDs[i + 3] = (l2 ushr 32).toInt() and 0xffffff
                docIDs[i + 4] = (l2 ushr 8).toInt() and 0xffffff
                docIDs[i + 5] = (((l2 and 0xffL) shl 16) or (l3 ushr 48)).toInt()
                docIDs[i + 6] = (l3 ushr 24).toInt() and 0xffffff
                docIDs[i + 7] = l3.toInt() and 0xffffff
                i += 8
            }
            while (i < count) {
                docIDs[i] =
                    (Short.toUnsignedInt(`in`.readShort()) shl 8) or Byte.toUnsignedInt(`in`.readByte())
                ++i
            }
        }

        @Throws(IOException::class)
        private fun readInts32(`in`: IndexInput, count: Int, docIDs: IntArray) {
            `in`.readInts(docIDs, 0, count)
        }

        @Throws(IOException::class)
        private fun readContinuousIds(`in`: IndexInput, count: Int, visitor: IntersectVisitor) {
            val start: Int = `in`.readVInt()
            visitor.visit(DocIdSetIterator.range(start, start + count))
        }

        @Throws(IOException::class)
        private fun readLegacyDeltaVInts(`in`: IndexInput, count: Int, visitor: IntersectVisitor) {
            var doc = 0
            for (i in 0..<count) {
                doc += `in`.readVInt()
                visitor.visit(doc)
            }
        }
    }
}
