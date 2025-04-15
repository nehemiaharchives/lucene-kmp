package org.gnit.lucenekmp.codecs.lucene90

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.KnnVectorValues.DocIndexIterator
import org.gnit.lucenekmp.jdkport.bitCount
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.math.pow

/**
 * Disk-based implementation of a [DocIdSetIterator] which can return the index of the current
 * document, i.e. the ordinal of the current document among the list of documents that this iterator
 * can return. This is useful to implement sparse doc values by only having to encode values for
 * documents that actually have a value.
 *
 *
 * Implementation-wise, this [DocIdSetIterator] is inspired of [ roaring bitmaps][RoaringDocIdSet] and encodes ranges of `65536` documents independently and picks between 3
 * encodings depending on the density of the range:
 *
 *
 *  * `ALL` if the range contains 65536 documents exactly,
 *  * `DENSE` if the range contains 4096 documents or more; in that case documents are
 * stored in a bit set,
 *  * `SPARSE` otherwise, and the lower 16 bits of the doc IDs are stored in a [       ][DataInput.readShort].
 *
 *
 *
 * Only ranges that contain at least one value are encoded.
 *
 *
 * This implementation uses 6 bytes per document in the worst-case, which happens in the case
 * that all ranges contain exactly one document.
 *
 *
 * To avoid O(n) lookup time complexity, with n being the number of documents, two lookup tables
 * are used: A lookup table for block offset and index, and a rank structure for DENSE block index
 * lookups.
 *
 *
 * The lookup table is an array of `int`-pairs, with a pair for each block. It allows for
 * direct jumping to the block, as opposed to iteration from the current position and forward one
 * block at a time.
 *
 *
 * Each int-pair entry consists of 2 logical parts:
 *
 *
 * The first 32 bit int holds the index (number of set bits in the blocks) up to just before the
 * wanted block. The maximum number of set bits is the maximum number of documents, which is less
 * than 2^31.
 *
 *
 * The next int holds the offset in bytes into the underlying slice. As there is a maximum of
 * 2^16 blocks, it follows that the maximum size of any block must not exceed 2^15 bytes to avoid
 * overflow (2^16 bytes if the int is treated as unsigned). This is currently the case, with the
 * largest block being DENSE and using 2^13 + 36 bytes.
 *
 *
 * The cache overhead is numDocs/1024 bytes.
 *
 *
 * Note: There are 4 types of blocks: ALL, DENSE, SPARSE and non-existing (0 set bits). In the
 * case of non-existing blocks, the entry in the lookup table has index equal to the previous entry
 * and offset equal to the next non-empty block.
 *
 *
 * The block lookup table is stored at the end of the total block structure.
 *
 *
 * The rank structure for DENSE blocks is an array of byte-pairs with an entry for each sub-block
 * (default 512 bits) out of the 65536 bits in the outer DENSE block.
 *
 *
 * Each rank-entry states the number of set bits within the block up to the bit before the bit
 * positioned at the start of the sub-block. Note that that the rank entry of the first sub-block is
 * always 0 and that the last entry can at most be 65536-2 = 65634 and thus will always fit into an
 * byte-pair of 16 bits.
 *
 *
 * The rank structure for a given DENSE block is stored at the beginning of the DENSE block. This
 * ensures locality and keeps logistics simple.
 *
 * @lucene.internal
 */
class IndexedDISI internal constructor(
    blockSlice: IndexInput,
    jumpTable: RandomAccessInput?,
    jumpTableEntryCount: Int,
    denseRankPower: Byte,
    cost: Long
) : DocIdSetIterator() {
    // Members are pkg-private to avoid synthetic accessors when accessed from the `Method` enum
    /** The slice that stores the [DocIdSetIterator].  */
    val slice: IndexInput

    val jumpTableEntryCount: Int
    val denseRankPower: Byte
    val jumpTable: RandomAccessInput? // Skip blocks of 64K bits
    val denseRankTable: ByteArray?
    val cost: Long

    /**
     * This constructor always creates a new blockSlice and a new jumpTable from in, to ensure that
     * operations are independent from the caller. See [.IndexedDISI] for re-use of blockSlice and jumpTable.
     *
     * @param in backing data.
     * @param offset starting offset for blocks in the backing data.
     * @param length the number of bytes holding blocks and jump-table in the backing data.
     * @param jumpTableEntryCount the number of blocks covered by the jump-table. This must match the
     * number returned by [.writeBitSet].
     * @param denseRankPower the number of docIDs covered by each rank entry in DENSE blocks,
     * expressed as `2^denseRankPower`. This must match the power given in [     ][.writeBitSet]
     * @param cost normally the number of logical docIDs.
     */
    constructor(
        `in`: IndexInput,
        offset: Long,
        length: Long,
        jumpTableEntryCount: Int,
        denseRankPower: Byte,
        cost: Long
    ) : this(
        createBlockSlice(`in`, "docs", offset, length, jumpTableEntryCount),
        createJumpTable(`in`, offset, length, jumpTableEntryCount),
        jumpTableEntryCount,
        denseRankPower,
        cost
    )

    var block: Int = -1
    var blockEnd: Long = 0
    var denseBitmapOffset: Long = -1 // Only used for DENSE blocks
    var nextBlockIndex: Int = -1
    var method: Method? = null

    var doc: Int = -1
    var index: Int = -1

    // SPARSE variables
    var exists: Boolean = false
    var nextExistDocInBlock: Int = -1

    // DENSE variables
    var word: Long = 0
    var wordIndex: Int = -1

    // number of one bits encountered so far, including those of `word`
    var numberOfOnes: Int = 0

    // Used with rank for jumps inside of DENSE as they are absolute instead of relative
    var denseOrigoIndex: Int = 0

    // ALL variables
    var gap: Int = 0

    /**
     * This constructor allows to pass the slice and jumpTable directly in case it helps reuse. see
     * eg. Lucene80 norms producer's merge instance.
     *
     * @param blockSlice data blocks, normally created by [.createBlockSlice].
     * @param jumpTable table holding jump-data for block-skips, normally created by [     ][.createJumpTable].
     * @param jumpTableEntryCount the number of blocks covered by the jump-table. This must match the
     * number returned by [.writeBitSet].
     * @param denseRankPower the number of docIDs covered by each rank entry in DENSE blocks,
     * expressed as `2^denseRankPower`. This must match the power given in [     ][.writeBitSet]
     * @param cost normally the number of logical docIDs.
     */
    init {
        require(!((denseRankPower < 7 || denseRankPower > 15) && denseRankPower.toInt() != -1)) {
            ("Acceptable values for denseRankPower are 7-15 (every 128-32768 docIDs). "
                    + "The provided power was "
                    + denseRankPower
                    + " (every "
                    + 2.0.pow(denseRankPower.toDouble()).toInt() + " docIDs). ")
        }

        this.slice = blockSlice
        this.jumpTable = jumpTable
        // Prefetch the first pages of data. Following pages are expected to get prefetched through
        // read-ahead.
        if (slice.length() > 0) {
            slice.prefetch(0, 1)
        }
        if (jumpTable != null && jumpTable.length() > 0) {
            jumpTable.prefetch(0, 1)
        }
        this.jumpTableEntryCount = jumpTableEntryCount
        this.denseRankPower = denseRankPower
        val rankIndexShift = denseRankPower - 7
        this.denseRankTable =
            if (denseRankPower.toInt() == -1) null else ByteArray(DENSE_BLOCK_LONGS shr rankIndexShift)
        this.cost = cost
    }

    override fun docID(): Int {
        return doc
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        val targetBlock = target and -0x10000
        if (block < targetBlock) {
            advanceBlock(targetBlock)
        }
        if (block == targetBlock) {
            if (method!!.advanceWithinBlock(this, target)) {
                return doc
            }
            readBlockHeader()
        }
        val found = method!!.advanceWithinBlock(this, block)
        require(found)
        return doc
    }

    @Throws(IOException::class)
    fun advanceExact(target: Int): Boolean {
        val targetBlock = target and -0x10000
        if (block < targetBlock) {
            advanceBlock(targetBlock)
        }
        val found = block == targetBlock && method!!.advanceExactWithinBlock(this, target)
        this.doc = target
        return found
    }

    @Throws(IOException::class)
    private fun advanceBlock(targetBlock: Int) {
        val blockIndex = targetBlock shr 16
        // If the destination block is 2 blocks or more ahead, we use the jump-table.
        if (jumpTable != null && blockIndex >= (block shr 16) + 2) {
            // If the jumpTableEntryCount is exceeded, there are no further bits. Last entry is always
            // NO_MORE_DOCS
            val inRangeBlockIndex =
                if (blockIndex < jumpTableEntryCount) blockIndex else jumpTableEntryCount - 1
            val index: Int = jumpTable.readInt(inRangeBlockIndex * Int.SIZE_BYTES.toLong() * 2)
            val offset: Int =
                jumpTable.readInt(inRangeBlockIndex * Int.SIZE_BYTES.toLong() * 2 + Int.SIZE_BYTES)
            this.nextBlockIndex = index - 1 // -1 to compensate for the always-added 1 in readBlockHeader
            slice.seek(offset.toLong())
            readBlockHeader()
            return
        }

        // Fallback to iteration of blocks
        do {
            slice.seek(blockEnd)
            readBlockHeader()
        } while (block < targetBlock)
    }

    @Throws(IOException::class)
    private fun readBlockHeader() {
        block = Short.toUnsignedInt(slice.readShort()) shl 16
        require(block >= 0)
        val numValues: Int = 1 + Short.toUnsignedInt(slice.readShort())
        index = nextBlockIndex
        nextBlockIndex = index + numValues
        if (numValues <= MAX_ARRAY_LENGTH) {
            method = Method.SPARSE
            blockEnd = slice.getFilePointer() + (numValues shl 1)
            nextExistDocInBlock = -1
        } else if (numValues == BLOCK_SIZE) {
            method = Method.ALL
            blockEnd = slice.getFilePointer()
            gap = block - index - 1
        } else {
            method = Method.DENSE
            denseBitmapOffset = slice.getFilePointer() + (denseRankTable?.size ?: 0)
            blockEnd = denseBitmapOffset + (1 shl 13)
            // Performance consideration: All rank (default 128 * 16 bits) are loaded up front. This
            // should be fast with the
            // reusable byte[] buffer, but it is still wasted if the DENSE block is iterated in small
            // steps.
            // If this results in too great a performance regression, a heuristic strategy might work
            // where the rank data
            // are loaded on first in-block advance, if said advance is > X docIDs. The hope being that a
            // small first
            // advance means that subsequent advances will be small too.
            // Another alternative is to maintain an extra slice for DENSE rank, but IndexedDISI is
            // already slice-heavy.
            if (denseRankPower.toInt() != -1) {
                slice.readBytes(denseRankTable!!, 0, denseRankTable.size)
            }
            wordIndex = -1
            numberOfOnes = index + 1
            denseOrigoIndex = numberOfOnes
        }
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        return advance(doc + 1)
    }

    fun index(): Int {
        return index
    }

    override fun cost(): Long {
        return cost
    }

    enum class Method {
        SPARSE {
            @Throws(IOException::class)
            override fun advanceWithinBlock(disi: IndexedDISI, target: Int): Boolean {
                val targetInBlock = target and 0xFFFF
                // TODO: binary search
                while (disi.index < disi.nextBlockIndex) {
                    val doc: Int = Short.toUnsignedInt(disi.slice.readShort())
                    disi.index++
                    if (doc >= targetInBlock) {
                        disi.doc = disi.block or doc
                        disi.exists = true
                        disi.nextExistDocInBlock = doc
                        return true
                    }
                }
                return false
            }

            @Throws(IOException::class)
            override fun advanceExactWithinBlock(disi: IndexedDISI, target: Int): Boolean {
                val targetInBlock = target and 0xFFFF
                // TODO: binary search
                if (disi.nextExistDocInBlock > targetInBlock) {
                    require(!disi.exists)
                    return false
                }
                if (target == disi.doc) {
                    return disi.exists
                }
                while (disi.index < disi.nextBlockIndex) {
                    val doc: Int = Short.toUnsignedInt(disi.slice.readShort())
                    disi.index++
                    if (doc >= targetInBlock) {
                        disi.nextExistDocInBlock = doc
                        if (doc != targetInBlock) {
                            disi.index--
                            disi.slice.seek(disi.slice.getFilePointer() - Short.SIZE_BYTES)
                            break
                        }
                        disi.exists = true
                        return true
                    }
                }
                disi.exists = false
                return false
            }
        },
        DENSE {
            @Throws(IOException::class)
            override fun advanceWithinBlock(disi: IndexedDISI, target: Int): Boolean {
                val targetInBlock = target and 0xFFFF
                val targetWordIndex = targetInBlock ushr 6

                // If possible, skip ahead using the rank cache
                // If the distance between the current position and the target is < rank-longs
                // there is no sense in using rank
                if (disi.denseRankPower.toInt() != -1
                    && targetWordIndex - disi.wordIndex >= (1 shl (disi.denseRankPower - 6))
                ) {
                    rankSkip(disi, targetInBlock)
                }

                for (i in disi.wordIndex + 1..targetWordIndex) {
                    disi.word = disi.slice.readLong()
                    disi.numberOfOnes += Long.bitCount(disi.word)
                }
                disi.wordIndex = targetWordIndex

                val leftBits = disi.word ushr target
                if (leftBits != 0L) {
                    disi.doc = target + Long.numberOfTrailingZeros(leftBits)
                    disi.index = disi.numberOfOnes - Long.bitCount(leftBits)
                    return true
                }

                // There were no set bits at the wanted position. Move forward until one is reached
                while (++disi.wordIndex < 1024) {
                    // This could use the rank cache to skip empty spaces >= 512 bits, but it seems
                    // unrealistic
                    // that such blocks would be DENSE
                    disi.word = disi.slice.readLong()
                    if (disi.word != 0L) {
                        disi.index = disi.numberOfOnes
                        disi.numberOfOnes += Long.bitCount(disi.word)
                        disi.doc =
                            disi.block or (disi.wordIndex shl 6) or Long.numberOfTrailingZeros(disi.word)
                        return true
                    }
                }
                // No set bits in the block at or after the wanted position.
                return false
            }

            @Throws(IOException::class)
            override fun advanceExactWithinBlock(disi: IndexedDISI, target: Int): Boolean {
                val targetInBlock = target and 0xFFFF
                val targetWordIndex = targetInBlock ushr 6

                // If possible, skip ahead using the rank cache
                // If the distance between the current position and the target is < rank-longs
                // there is no sense in using rank
                if (disi.denseRankPower.toInt() != -1
                    && targetWordIndex - disi.wordIndex >= (1 shl (disi.denseRankPower - 6))
                ) {
                    rankSkip(disi, targetInBlock)
                }

                for (i in disi.wordIndex + 1..targetWordIndex) {
                    disi.word = disi.slice.readLong()
                    disi.numberOfOnes += Long.bitCount(disi.word)
                }
                disi.wordIndex = targetWordIndex

                val leftBits = disi.word ushr target
                disi.index = disi.numberOfOnes - Long.bitCount(leftBits)
                return (leftBits and 1L) != 0L
            }
        },
        ALL {
            override fun advanceWithinBlock(disi: IndexedDISI, target: Int): Boolean {
                disi.doc = target
                disi.index = target - disi.gap
                return true
            }

            override fun advanceExactWithinBlock(disi: IndexedDISI, target: Int): Boolean {
                disi.index = target - disi.gap
                return true
            }
        };

        /**
         * Advance to the first doc from the block that is equal to or greater than `target`.
         * Return true if there is such a doc and false otherwise.
         */
        @Throws(IOException::class)
        abstract fun advanceWithinBlock(disi: IndexedDISI, target: Int): Boolean

        /**
         * Advance the iterator exactly to the position corresponding to the given `target` and
         * return whether this document exists.
         */
        @Throws(IOException::class)
        abstract fun advanceExactWithinBlock(disi: IndexedDISI, target: Int): Boolean
    }

    companion object {
        // jump-table time/space trade-offs to consider:
        // The block offsets and the block indexes could be stored in more compressed form with
        // two PackedInts or two MonotonicDirectReaders.
        // The DENSE ranks (default 128 shorts = 256 bytes) could likewise be compressed. But as there is
        // at least 4096 set bits in DENSE blocks, there will be at least one rank with 2^12 bits, so it
        // is doubtful if there is much to gain here.
        private const val BLOCK_SIZE = 65536 // The number of docIDs that a single block represents

        private const val DENSE_BLOCK_LONGS: Int = BLOCK_SIZE / Long.SIZE_BITS // 1024
        const val DEFAULT_DENSE_RANK_POWER: Byte = 9 // Every 512 docIDs / 8 longs

        const val MAX_ARRAY_LENGTH: Int = (1 shl 12) - 1

        @Throws(IOException::class)
        private fun flush(
            block: Int, buffer: FixedBitSet, cardinality: Int, denseRankPower: Byte, out: IndexOutput
        ) {
            require(block >= 0 && block < BLOCK_SIZE)
            out.writeShort(block.toShort())
            require(cardinality > 0 && cardinality <= BLOCK_SIZE)
            out.writeShort((cardinality - 1).toShort())
            if (cardinality > MAX_ARRAY_LENGTH) {
                if (cardinality != BLOCK_SIZE) { // all docs are set
                    if (denseRankPower.toInt() != -1) {
                        val rank = createRank(buffer, denseRankPower)
                        out.writeBytes(rank, rank.size)
                    }
                    for (word in buffer.bits) {
                        out.writeLong(word)
                    }
                }
            } else {
                val it = BitSetIterator(buffer, cardinality.toLong())
                run {
                    var doc: Int = it.nextDoc()
                    while (doc != NO_MORE_DOCS) {
                        out.writeShort(doc.toShort())
                        doc = it.nextDoc()
                    }
                }
            }
        }

        // Creates a DENSE rank-entry (the number of set bits up to a given point) for the buffer.
        // One rank-entry for every {@code 2^denseRankPower} bits, with each rank-entry using 2 bytes.
        // Represented as a byte[] for fast flushing and mirroring of the retrieval representation.
        private fun createRank(buffer: FixedBitSet, denseRankPower: Byte): ByteArray {
            val longsPerRank = 1 shl (denseRankPower - 6)
            val rankMark = longsPerRank - 1
            val rankIndexShift = denseRankPower - 7 // 6 for the long (2^6) + 1 for 2 bytes/entry
            val rank = ByteArray(DENSE_BLOCK_LONGS shr rankIndexShift)
            val bits: LongArray = buffer.bits
            var bitCount = 0
            for (word in 0..<DENSE_BLOCK_LONGS) {
                if ((word and rankMark) == 0) { // Every longsPerRank longs
                    rank[word shr rankIndexShift] = (bitCount shr 8).toByte()
                    rank[(word shr rankIndexShift) + 1] = (bitCount and 0xFF).toByte()
                }
                bitCount += Long.bitCount(bits[word])
            }
            return rank
        }

        /**
         * Writes the docIDs from it to out, in logical blocks, one for each 65536 docIDs in monotonically
         * increasing gap-less order. DENSE blocks uses [.DEFAULT_DENSE_RANK_POWER] of 9 (every 512
         * docIDs / 8 longs). The caller must keep track of the number of jump-table entries (returned by
         * this method) as well as the denseRankPower (9 for this method) and provide them when
         * constructing an IndexedDISI for reading.
         *
         * @param it the document IDs.
         * @param out destination for the blocks.
         * @throws IOException if there was an error writing to out.
         * @return the number of jump-table entries following the blocks, -1 for no entries. This should
         * be stored in meta and used when creating an instance of IndexedDISI.
         */
        @Throws(IOException::class)
        fun writeBitSet(it: DocIdSetIterator, out: IndexOutput): Short {
            return writeBitSet(it, out, DEFAULT_DENSE_RANK_POWER)
        }

        /**
         * Writes the docIDs from it to out, in logical blocks, one for each 65536 docIDs in monotonically
         * increasing gap-less order. The caller must keep track of the number of jump-table entries
         * (returned by this method) as well as the denseRankPower and provide them when constructing an
         * IndexedDISI for reading.
         *
         * @param it the document IDs.
         * @param out destination for the blocks.
         * @param denseRankPower for [Method.DENSE] blocks, a rank will be written every `2^denseRankPower` docIDs. Values &lt; 7 (every 128 docIDs) or &gt; 15 (every 32768 docIDs)
         * disables DENSE rank. Recommended values are 8-12: Every 256-4096 docIDs or 4-64 longs.
         * [.DEFAULT_DENSE_RANK_POWER] is 9: Every 512 docIDs. This should be stored in meta and
         * used when creating an instance of IndexedDISI.
         * @throws IOException if there was an error writing to out.
         * @return the number of jump-table entries following the blocks, -1 for no entries. This should
         * be stored in meta and used when creating an instance of IndexedDISI.
         */
        @Throws(IOException::class)
        fun writeBitSet(it: DocIdSetIterator, out: IndexOutput, denseRankPower: Byte): Short {
            val origo: Long = out.filePointer // All jumps are relative to the origo
            require(!((denseRankPower < 7 || denseRankPower > 15) && denseRankPower.toInt() != -1)) {
                ("Acceptable values for denseRankPower are 7-15 (every 128-32768 docIDs). "
                        + "The provided power was "
                        + denseRankPower
                        + " (every "
                        + 2.0.pow(denseRankPower.toDouble()).toInt() + " docIDs)")
            }
            var totalCardinality = 0
            var blockCardinality = 0
            val buffer: FixedBitSet = FixedBitSet(1 shl 16)
            var jumps = IntArray(ArrayUtil.oversize(1, Int.SIZE_BYTES * 2))
            var prevBlock = -1
            var jumpBlockIndex = 0

            run {
                var doc: Int = it.nextDoc()
                while (doc != NO_MORE_DOCS) {
                    val block = doc ushr 16
                    if (prevBlock != -1 && block != prevBlock) {
                        // Track offset+index from previous block up to current
                        jumps =
                            addJumps(
                                jumps,
                                out.filePointer - origo,
                                totalCardinality,
                                jumpBlockIndex,
                                prevBlock + 1
                            )
                        jumpBlockIndex = prevBlock + 1
                        // Flush block
                        flush(prevBlock, buffer, blockCardinality, denseRankPower, out)
                        // Reset for next block
                        buffer.clear()
                        totalCardinality += blockCardinality
                        blockCardinality = 0
                    }
                    buffer.set(doc and 0xFFFF)
                    blockCardinality++
                    prevBlock = block
                    doc = it.nextDoc()
                }
            }
            if (blockCardinality > 0) {
                jumps =
                    addJumps(
                        jumps, out.filePointer - origo, totalCardinality, jumpBlockIndex, prevBlock + 1
                    )
                totalCardinality += blockCardinality
                flush(prevBlock, buffer, blockCardinality, denseRankPower, out)
                buffer.clear()
                prevBlock++
            }
            val lastBlock =
                if (prevBlock == -1) 0 else prevBlock // There will always be at least 1 block (NO_MORE_DOCS)
            // Last entry is a SPARSE with blockIndex == 32767 and the single entry 65535, which becomes the
            // docID NO_MORE_DOCS
            // To avoid creating 65K jump-table entries, only a single entry is created pointing to the
            // offset of the
            // NO_MORE_DOCS block, with the jumpBlockIndex set to the logical EMPTY block after all real
            // blocks.
            jumps =
                addJumps(jumps, out.filePointer - origo, totalCardinality, lastBlock, lastBlock + 1)
            buffer.set(NO_MORE_DOCS and 0xFFFF)
            flush(NO_MORE_DOCS ushr 16, buffer, 1, denseRankPower, out)
            // offset+index jump-table stored at the end
            return flushBlockJumps(jumps, lastBlock + 1, out)
        }

        // Adds entries to the offset & index jump-table for blocks
        private fun addJumps(jumps: IntArray, offset: Long, index: Int, startBlock: Int, endBlock: Int): IntArray {
            var jumps = jumps
            require(
                offset < Int.Companion.MAX_VALUE
            ) { "Logically the offset should not exceed 2^30 but was >= Integer.MAX_VALUE" }
            jumps = ArrayUtil.grow(jumps, (endBlock + 1) * 2)
            for (b in startBlock..<endBlock) {
                jumps[b * 2] = index
                jumps[b * 2 + 1] = offset.toInt()
            }
            return jumps
        }

        // Flushes the offset & index jump-table for blocks. This should be the last data written to out
        // This method returns the blockCount for the blocks reachable for the jump_table or -1 for no
        // jump-table
        @Throws(IOException::class)
        private fun flushBlockJumps(jumps: IntArray, blockCount: Int, out: IndexOutput): Short {
            var blockCount = blockCount
            if (blockCount
                == 2
            ) { // Jumps with a single real entry + NO_MORE_DOCS is just wasted space so we ignore
                // that
                blockCount = 0
            }
            for (i in 0..<blockCount) {
                out.writeInt(jumps[i * 2]) // index
                out.writeInt(jumps[i * 2 + 1]) // offset
            }
            // As there are at most 32k blocks, the count is a short
            // The jumpTableOffset will be at lastPos - (blockCount * Long.BYTES)
            return blockCount.toShort()
        }

        /**
         * Helper method for using [.IndexedDISI].
         * Creates a disiSlice for the IndexedDISI data blocks, without the jump-table.
         *
         * @param slice backing data, holding both blocks and jump-table.
         * @param sliceDescription human readable slice designation.
         * @param offset relative to the backing data.
         * @param length full length of the IndexedDISI, including blocks and jump-table data.
         * @param jumpTableEntryCount the number of blocks covered by the jump-table.
         * @return a jumpTable containing the block jump-data or null if no such table exists.
         * @throws IOException if a RandomAccessInput could not be created from slice.
         */
        @Throws(IOException::class)
        fun createBlockSlice(
            slice: IndexInput, sliceDescription: String, offset: Long, length: Long, jumpTableEntryCount: Int
        ): IndexInput {
            val jumpTableBytes =
                (if (jumpTableEntryCount < 0) 0 else jumpTableEntryCount * Int.SIZE_BYTES * 2).toLong()
            return slice.slice(sliceDescription, offset, length - jumpTableBytes)
        }

        /**
         * Helper method for using [.IndexedDISI].
         * Creates a RandomAccessInput covering only the jump-table data or null.
         *
         * @param slice backing data, holding both blocks and jump-table.
         * @param offset relative to the backing data.
         * @param length full length of the IndexedDISI, including blocks and jump-table data.
         * @param jumpTableEntryCount the number of blocks covered by the jump-table.
         * @return a jumpTable containing the block jump-data or null if no such table exists.
         * @throws IOException if a RandomAccessInput could not be created from slice.
         */
        @Throws(IOException::class)
        fun createJumpTable(
            slice: IndexInput, offset: Long, length: Long, jumpTableEntryCount: Int
        ): RandomAccessInput? {
            if (jumpTableEntryCount <= 0) {
                return null
            } else {
                val jumpTableBytes: Int = jumpTableEntryCount * Int.SIZE_BYTES * 2
                return slice.randomAccessSlice(offset + length - jumpTableBytes, jumpTableBytes.toLong())
            }
        }

        /**
         * Returns an iterator that delegates to the IndexedDISI. Advancing this iterator will advance the
         * underlying IndexedDISI, and vice-versa.
         */
        fun asDocIndexIterator(disi: IndexedDISI): DocIndexIterator {
            // can we replace with fromDISI
            return object : DocIndexIterator() {
                override fun docID(): Int {
                    return disi.docID()
                }

                override fun index(): Int {
                    return disi.index()
                }

                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    return disi.nextDoc()
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    return disi.advance(target)
                }

                override fun cost(): Long {
                    return disi.cost()
                }
            }
        }

        /**
         * If the distance between the current position and the target is > 8 words, the rank cache will
         * be used to guarantee a worst-case of 1 rank-lookup and 7 word-read-and-count-bits operations.
         * Note: This does not guarantee a skip up to target, only up to nearest rank boundary. It is the
         * responsibility of the caller to iterate further to reach target.
         *
         * @param disi standard DISI.
         * @param targetInBlock lower 16 bits of the target
         * @throws IOException if a DISI seek failed.
         */
        @Throws(IOException::class)
        private fun rankSkip(disi: IndexedDISI, targetInBlock: Int) {
            require(disi.denseRankPower >= 0) { disi.denseRankPower }
            // Resolve the rank as close to targetInBlock as possible (maximum distance is 8 longs)
            // Note: rankOrigoOffset is tracked on block open, so it is absolute (e.g. don't add origo)
            val rankIndex =
                targetInBlock shr disi.denseRankPower.toInt() // Default is 9 (8 longs: 2^3 * 2^6 = 512 docIDs)

            val rank =
                ((disi.denseRankTable!![rankIndex shl 1].toInt() and 0xFF) shl 8
                        or (disi.denseRankTable[(rankIndex shl 1) + 1].toInt() and 0xFF))

            // Position the counting logic just after the rank point
            val rankAlignedWordIndex = rankIndex shl disi.denseRankPower.toInt() shr 6
            disi.slice.seek(disi.denseBitmapOffset + rankAlignedWordIndex * Long.SIZE_BYTES.toLong())
            val rankWord: Long = disi.slice.readLong()
            val denseNOO: Int = rank + Long.bitCount(rankWord)

            disi.wordIndex = rankAlignedWordIndex
            disi.word = rankWord
            disi.numberOfOnes = disi.denseOrigoIndex + denseNOO
        }
    }
}
