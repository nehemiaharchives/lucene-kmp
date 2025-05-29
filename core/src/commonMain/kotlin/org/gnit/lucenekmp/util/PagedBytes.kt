package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexInput

/**
 * Represents a logical byte[] as a series of pages. You can write-once into the logical byte[]
 * (append only), using copy, and then retrieve slices (BytesRef) into it using fill.
 *
 * @lucene.internal
 */
// TODO: refactor this, byteblockpool, fst.bytestore, and any
// other "shift/mask big arrays". there are too many of these classes!
class PagedBytes(blockBits: Int) : Accountable {

    private var blocks: Array<ByteArray> = Array(16) { EMPTY_BYTES }
    private var numBlocks: Int

    // TODO: these are unused
    private val blockSize: Int
    private val blockBits: Int
    private val blockMask: Int
    private var didSkipBytes = false
    private var frozen = false
    private var upto: Int
    private var currentBlock: ByteArray? = null
    private val bytesUsedPerBlock: Long

    /**
     * Provides methods to read BytesRefs from a frozen PagedBytes.
     *
     * @see .freeze
     */
    class Reader(pagedBytes: PagedBytes) : Accountable {
        private val blocks: Array<ByteArray> = ArrayUtil.copyOfSubArray<ByteArray>(pagedBytes.blocks, 0, pagedBytes.numBlocks)
        private val blockBits: Int = pagedBytes.blockBits
        private val blockMask: Int = pagedBytes.blockMask
        private val blockSize: Int = pagedBytes.blockSize
        private val bytesUsedPerBlock: Long = pagedBytes.bytesUsedPerBlock

        /**
         * Gets a slice out of [PagedBytes] starting at *start* with a given length. Iff the
         * slice spans across a block border this method will allocate sufficient resources and copy the
         * paged data.
         *
         *
         * Slices spanning more than two blocks are not supported.
         *
         * @lucene.internal
         */
        fun fillSlice(b: BytesRef, start: Long, length: Int) {
            assert(length >= 0) { "length=$length" }
            assert(length <= blockSize + 1) { "length=$length" }
            b.length = length
            if (length == 0) {
                return
            }
            val index = (start shr blockBits).toInt()
            val offset = (start and blockMask.toLong()).toInt()
            if (blockSize - offset >= length) {
                // Within block
                b.bytes = blocks[index]
                b.offset = offset
            } else {
                // Split
                b.bytes = ByteArray(length)
                b.offset = 0
                System.arraycopy(blocks[index], offset, b.bytes, 0, blockSize - offset)
                System.arraycopy(
                    blocks[1 + index], 0, b.bytes, blockSize - offset, length - (blockSize - offset)
                )
            }
        }

        /**
         * Get the byte at the given offset.
         *
         * @lucene.internal
         */
        fun getByte(o: Long): Byte {
            val index = (o shr blockBits).toInt()
            val offset = (o and blockMask.toLong()).toInt()
            return blocks[index][offset]
        }

        /**
         * Reads length as 1 or 2 byte vInt prefix, starting at *start*.
         *
         *
         * **Note:** this method does not support slices spanning across block borders.
         *
         * @lucene.internal
         */
        // TODO: this really needs to be refactored into fieldcacheimpl
        fun fill(b: BytesRef, start: Long) {
            val index = (start shr blockBits).toInt()
            val offset = (start and blockMask.toLong()).toInt()
            b.bytes = blocks[index]
            val block: ByteArray = b.bytes

            if ((block[offset].toInt() and 128) == 0) {
                b.length = block[offset].toInt()
                b.offset = offset + 1
            } else {
                b.length = BitUtil.VH_BE_SHORT.get(block, offset).toInt() and 0x7FFF
                b.offset = offset + 2
                assert(b.length > 0)
            }
        }

        override fun ramBytesUsed(): Long {
            var size: Long = BASE_RAM_BYTES_USED + RamUsageEstimator.shallowSizeOf(blocks)
            if (blocks.isNotEmpty()) {
                size += (blocks.size - 1) * bytesUsedPerBlock
                size += RamUsageEstimator.sizeOf(blocks[blocks.size - 1])
            }
            return size
        }

        override fun toString(): String {
            return "PagedBytes(blocksize=$blockSize)"
        }

        companion object {
            private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(
                Reader::class
            )
        }
    }

    /** 1&lt;&lt;blockBits must be bigger than biggest single BytesRef slice that will be pulled  */
    init {
        assert(blockBits > 0 && blockBits <= 31) { blockBits }
        this.blockSize = 1 shl blockBits
        this.blockBits = blockBits
        blockMask = blockSize - 1
        upto = blockSize
        bytesUsedPerBlock =
            RamUsageEstimator.alignObjectSize((blockSize + RamUsageEstimator.NUM_BYTES_ARRAY_HEADER).toLong())
        numBlocks = 0
    }

    private fun addBlock(block: ByteArray) {
        blocks = ArrayUtil.grow<ByteArray>(blocks, numBlocks + 1)
        blocks[numBlocks++] = block
    }

    /** Read this many bytes from in  */
    @Throws(IOException::class)
    fun copy(`in`: IndexInput, byteCount: Long) {
        var byteCount = byteCount
        while (byteCount > 0) {
            var left = blockSize - upto
            if (left == 0) {
                if (currentBlock != null) {
                    addBlock(currentBlock!!)
                }
                currentBlock = ByteArray(blockSize)
                upto = 0
                left = blockSize
            }
            if (left < byteCount) {
                `in`.readBytes(currentBlock!!, upto, left, false)
                upto = blockSize
                byteCount -= left.toLong()
            } else {
                `in`.readBytes(currentBlock!!, upto, byteCount.toInt(), false)
                upto += byteCount.toInt()
                break
            }
        }
    }

    /**
     * Copy BytesRef in, setting BytesRef out to the result. Do not use this if you will use
     * freeze(true). This only supports bytes.length &lt;= blockSize
     */
    fun copy(bytes: BytesRef, out: BytesRef) {
        var left = blockSize - upto
        if (bytes.length > left || currentBlock == null) {
            if (currentBlock != null) {
                addBlock(currentBlock!!)
                didSkipBytes = true
            }
            currentBlock = ByteArray(blockSize)
            upto = 0
            left = blockSize
            assert(bytes.length <= blockSize)
            // TODO: we could also support variable block sizes
        }

        out.bytes = currentBlock!!
        out.offset = upto
        out.length = bytes.length

        System.arraycopy(bytes.bytes, bytes.offset, currentBlock!!, upto, bytes.length)
        upto += bytes.length
    }

    /** Commits final byte[], trimming it if necessary and if trim=true  */
    fun freeze(trim: Boolean): Reader {
        check(!frozen) { "already frozen" }
        check(!didSkipBytes) { "cannot freeze when copy(BytesRef, BytesRef) was used" }
        if (trim && upto < blockSize) {
            val newBlock = ByteArray(upto)
            System.arraycopy(currentBlock!!, 0, newBlock, 0, upto)
            currentBlock = newBlock
        }
        if (currentBlock == null) {
            currentBlock = EMPTY_BYTES
        }
        addBlock(currentBlock!!)
        frozen = true
        currentBlock = null
        return Reader(this)
    }

    val pointer: Long
        get() {
            return if (currentBlock == null) {
                0
            } else {
                (numBlocks * (blockSize.toLong())) + upto
            }
        }

    override fun ramBytesUsed(): Long {
        var size: Long = BASE_RAM_BYTES_USED + RamUsageEstimator.shallowSizeOf(blocks)
        if (numBlocks > 0) {
            size += (numBlocks - 1) * bytesUsedPerBlock
            size += RamUsageEstimator.sizeOf(blocks[numBlocks - 1])
        }
        if (currentBlock != null) {
            size += RamUsageEstimator.sizeOf(currentBlock!!)
        }
        return size
    }

    /** Copy bytes in, writing the length as a 1 or 2 byte vInt prefix.  */ // TODO: this really needs to be refactored into fieldcacheimpl!
    fun copyUsingLengthPrefix(bytes: BytesRef): Long {
        require(bytes.length < 32768) { "max length is 32767 (got " + bytes.length + ")" }

        if (upto + bytes.length + 2 > blockSize) {
            require(bytes.length + 2 <= blockSize) { "block size " + blockSize + " is too small to store length " + bytes.length + " bytes" }
            if (currentBlock != null) {
                addBlock(currentBlock!!)
            }
            currentBlock = ByteArray(blockSize)
            upto = 0
        }

        val pointer = this.pointer

        if (bytes.length < 128) {
            currentBlock!![upto++] = bytes.length.toByte()
        } else {
            BitUtil.VH_BE_SHORT.set(currentBlock!!, upto, (bytes.length or 0x8000).toShort())
            upto += 2
        }
        System.arraycopy(bytes.bytes, bytes.offset, currentBlock!!, upto, bytes.length)
        upto += bytes.length

        return pointer
    }

    /**
     * Input that transparently iterates over pages
     *
     * @lucene.internal
     */
    inner class PagedBytesDataInput internal constructor() : DataInput() {
        private var currentBlockIndex = 0
        private var currentBlockUpto = 0
        private var currentBlock: ByteArray

        init {
            currentBlock = blocks[0]
        }

        override fun clone(): PagedBytesDataInput {
            val clone: PagedBytesDataInput = this@PagedBytes.dataInput
            clone.position = this.position
            return clone
        }

        var position: Long
            /** Returns the current byte position.  */
            get() = currentBlockIndex.toLong() * blockSize + currentBlockUpto
            /** Seek to a position previously obtained from [.getPosition].  */
            set(pos) {
                currentBlockIndex = (pos shr blockBits).toInt()
                currentBlock = blocks[currentBlockIndex]
                currentBlockUpto = (pos and blockMask.toLong()).toInt()
            }

        override fun readByte(): Byte {
            if (currentBlockUpto == blockSize) {
                nextBlock()
            }
            return currentBlock[currentBlockUpto++]
        }

        override fun readBytes(b: ByteArray, offset: Int, len: Int) {
            var offset = offset
            assert(b.size >= offset + len)
            val offsetEnd = offset + len
            while (true) {
                val blockLeft = blockSize - currentBlockUpto
                val left = offsetEnd - offset
                if (blockLeft < left) {
                    System.arraycopy(currentBlock, currentBlockUpto, b, offset, blockLeft)
                    nextBlock()
                    offset += blockLeft
                } else {
                    // Last block
                    System.arraycopy(currentBlock, currentBlockUpto, b, offset, left)
                    currentBlockUpto += left
                    break
                }
            }
        }

        override fun skipBytes(numBytes: Long) {
            require(numBytes >= 0) { "numBytes must be >= 0, got $numBytes" }
            val skipTo = this.position + numBytes
            this.position = skipTo
        }

        private fun nextBlock() {
            currentBlockIndex++
            currentBlockUpto = 0
            currentBlock = blocks[currentBlockIndex]
        }
    }

    /**
     * Output that transparently spills to new pages as necessary
     *
     * @lucene.internal
     */
    inner class PagedBytesDataOutput : DataOutput() {
        override fun writeByte(b: Byte) {
            if (upto == blockSize) {
                if (currentBlock != null) {
                    addBlock(currentBlock!!)
                }
                currentBlock = ByteArray(blockSize)
                upto = 0
            }
            currentBlock!![upto++] = b
        }

        override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
            var offset = offset
            assert(
                b.size >= offset + length
            ) { "b.length=" + b.size + " offset=" + offset + " length=" + length }
            if (length == 0) {
                return
            }

            if (upto == blockSize) {
                if (currentBlock != null) {
                    addBlock(currentBlock!!)
                }
                currentBlock = ByteArray(blockSize)
                upto = 0
            }

            val offsetEnd = offset + length
            while (true) {
                val left = offsetEnd - offset
                val blockLeft = blockSize - upto
                if (blockLeft < left) {
                    System.arraycopy(b, offset, currentBlock!!, upto, blockLeft)
                    addBlock(currentBlock!!)
                    currentBlock = ByteArray(blockSize)
                    upto = 0
                    offset += blockLeft
                } else {
                    // Last block
                    System.arraycopy(b, offset, currentBlock!!, upto, left)
                    upto += left
                    break
                }
            }
        }

        val position: Long
            /** Return the current byte position.  */
            get() = this@PagedBytes.pointer
    }

    val dataInput: PagedBytesDataInput
        /** Returns a DataInput to read values from this PagedBytes instance.  */
        get() {
            check(frozen) { "must call freeze() before getDataInput" }
            return PagedBytesDataInput()
        }

    val dataOutput: PagedBytesDataOutput
        /**
         * Returns a DataOutput that you may use to write into this PagedBytes instance. If you do this,
         * you should not call the other writing methods (eg, copy); results are undefined.
         */
        get() {
            check(!frozen) { "cannot get DataOutput after freeze()" }
            return PagedBytesDataOutput()
        }

    companion object {
        private val BASE_RAM_BYTES_USED: Long =
            RamUsageEstimator.shallowSizeOfInstance(PagedBytes::class)
        private val EMPTY_BYTES = ByteArray(0)
    }
}
