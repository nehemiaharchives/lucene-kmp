package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.ByteOrder
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.jdkport.getLast
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros
import org.gnit.lucenekmp.jdkport.pollFirst
import kotlin.jvm.JvmOverloads
import kotlin.math.max
import kotlin.math.min
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.UnicodeUtil

/** A [DataOutput] storing data in a list of [ByteBuffer]s.  */
class ByteBuffersDataOutput @JvmOverloads constructor(
    minBitsPerBlock: Int = DEFAULT_MIN_BITS_PER_BLOCK,
    maxBitsPerBlock: Int = DEFAULT_MAX_BITS_PER_BLOCK,
    blockAllocate: (Int) -> ByteBuffer = ALLOCATE_BB_ON_HEAP,
    blockReuse: (ByteBuffer) -> Unit = NO_REUSE
) : DataOutput(), Accountable {
    /**
     * An implementation of a [ByteBuffer] allocation and recycling policy. The blocks are
     * recycled if exactly the same size is requested, otherwise they're released to be GCed.
     */
    class ByteBufferRecycler(delegate: (Int) -> ByteBuffer) {
        private val reuse: ArrayDeque<ByteBuffer> = ArrayDeque<ByteBuffer>()
        private val delegate: (Int) -> ByteBuffer = requireNotNull(delegate)

        fun allocate(size: Int): ByteBuffer {
            while (!reuse.isEmpty()) {
                val bb: ByteBuffer = reuse.removeFirst()
                // If we don't have a buffer of exactly the requested size, discard it.
                if (bb.remaining() == size) {
                    return bb
                }
            }

            return delegate(size)
        }

        fun reuse(buffer: ByteBuffer) {
            buffer.rewind()
            reuse.addLast(buffer)
        }
    }

    /** Maximum block size: `2^bits`.  */
    private val maxBitsPerBlock: Int

    /** [ByteBuffer] supplier.  */
    private val blockAllocate: (Int) -> ByteBuffer

    /** [ByteBuffer] recycler on [.reset].  */
    private val blockReuse: (ByteBuffer) -> Unit

    /** Current block size: `2^bits`.  */
    private var blockBits: Int

    /** Blocks storing data.  */
    private val blocks: ArrayDeque<ByteBuffer> = ArrayDeque<ByteBuffer>()

    /** Cumulative RAM usage across all blocks.  */
    private var ramBytesUsed: Long = 0

    /** The current-or-next write block.  */
    private var currentBlock: ByteBuffer = EMPTY

    /**
     * Create a new output, suitable for writing a file of around `expectedSize` bytes.
     *
     *
     * Memory allocation will be optimized based on the `expectedSize` hint, so that there is
     * less overhead for larger files.
     *
     * @param expectedSize estimated size of the output file
     */
    constructor(expectedSize: Long) : this(
        computeBlockSizeBitsFor(expectedSize),
        DEFAULT_MAX_BITS_PER_BLOCK,
        ALLOCATE_BB_ON_HEAP,
        NO_REUSE
    )

    override fun writeByte(b: Byte) {
        if (!currentBlock.hasRemaining()) {
            appendBlock()
        }
        currentBlock.put(b)
    }

    override fun writeBytes(src: ByteArray, offset: Int, length: Int) {
        var offset = offset
        var length = length
        require(length >= 0)
        while (length > 0) {
            if (!currentBlock.hasRemaining()) {
                appendBlock()
            }

            val chunk = min(currentBlock.remaining(), length)
            currentBlock.put(src, offset, chunk)
            length -= chunk
            offset += chunk
        }
    }

    override fun writeBytes(b: ByteArray, length: Int) {
        writeBytes(b, 0, length)
    }

    fun writeBytes(b: ByteArray) {
        writeBytes(b, 0, b.size)
    }

    fun writeBytes(buffer: ByteBuffer) {
        var buffer: ByteBuffer = buffer
        buffer = buffer.duplicate()
        var length: Int = buffer.remaining()
        while (length > 0) {
            if (!currentBlock.hasRemaining()) {
                appendBlock()
            }

            val chunk = min(currentBlock.remaining(), length)
            buffer.limit(buffer.position + chunk)
            currentBlock.put(buffer)

            length -= chunk
        }
    }

    /**
     * Return a list of read-only view of [ByteBuffer] blocks over the current content written
     * to the output.
     */
    fun toBufferList(): MutableList<ByteBuffer> {
        val result: MutableList<ByteBuffer> =
            ArrayList<ByteBuffer>(max(blocks.size, 1))
        if (blocks.isEmpty()) {
            result.add(EMPTY)
        } else {
            for (bb in blocks) {
                var bb: ByteBuffer = bb
                bb = bb.asReadOnlyBuffer().flip().order(ByteOrder.LITTLE_ENDIAN)
                result.add(bb)
            }
        }
        return result
    }

    /**
     * Returns a list of writeable blocks over the (source) content buffers.
     *
     *
     * This method returns the raw content of source buffers that may change over the lifetime of
     * this object (blocks can be recycled or discarded, for example). Most applications should favor
     * calling [.toBufferList] which returns a read-only *view* over the content of the
     * source buffers.
     *
     *
     * The difference between [.toBufferList] and [.toWriteableBufferList] is that
     * read-only view of source buffers will always return `false` from [ ][ByteBuffer.hasArray] (which sometimes may be required to avoid double copying).
     */
    fun toWriteableBufferList(): MutableList<ByteBuffer> {
        val result: ArrayList<ByteBuffer> =
            ArrayList<ByteBuffer>(max(blocks.size, 1))
        if (blocks.isEmpty()) {
            result.add(EMPTY)
        } else {
            for (bb in blocks) {
                var bb: ByteBuffer = bb
                bb = bb.duplicate().flip()
                result.add(bb)
            }
        }
        return result
    }

    /**
     * Return a [ByteBuffersDataInput] for the set of current buffers ([.toBufferList]).
     */
    fun toDataInput(): ByteBuffersDataInput {
        return ByteBuffersDataInput(toBufferList())
    }

    /**
     * Return a contiguous array with the current content written to the output. The returned array is
     * always a copy (can be mutated).
     *
     *
     * If the [.size] of the underlying buffers exceeds maximum size of Java array, an
     * [RuntimeException] will be thrown.
     */
    fun toArrayCopy(): ByteArray {
        if (blocks.isEmpty()) {
            return EMPTY_BYTE_ARRAY
        }

        // We could try to detect single-block, array-based ByteBuffer here
        // and use Arrays.copyOfRange, but I don't think it's worth the extra
        // instance checks.
        val size = size()
        if (size > Int.Companion.MAX_VALUE) {
            throw RuntimeException("Data exceeds maximum size of a single byte array: $size")
        }

        val arr = ByteArray(Math.toIntExact(size()))
        var offset = 0
        for (bb in toBufferList()) {
            val len: Int = bb.remaining()
            bb.get(arr, offset, len)
            offset += len
        }
        return arr
    }

    @Throws(IOException::class)
    override fun copyBytes(input: DataInput, numBytes: Long) {

        require(numBytes >= 0) { "numBytes must be non-negative: $numBytes" }
        if (numBytes == 0L) return

        var remaining = numBytes
        val tempBuffer = ByteArray(min(COPY_BUFFER_SIZE, remaining.toInt()))

        while (remaining > 0) {
            // Ensure we have a block with space available
            if (!currentBlock.hasRemaining()) {
                appendBlock()
            }

            // Calculate how much we can copy in this iteration
            val toCopy = min(remaining.toInt(), min(tempBuffer.size, currentBlock.remaining()))

            // Read from input into our temporary buffer
            input.readBytes(tempBuffer, 0, toCopy)

            // Write from temporary buffer to the current block
            currentBlock.put(tempBuffer, 0, toCopy)

            remaining -= toCopy
        }
    }

    /** Copy the current content of this object into another [DataOutput].  */
    @Throws(IOException::class)
    fun copyTo(output: DataOutput) {
        for (bb in blocks) {

            // Create a read-only view and flip it for reading
            val readView = bb.asReadOnlyBuffer().flip()

            // Use ByteBuffersDataInput to copy the bytes
            output.copyBytes(ByteBuffersDataInput(mutableListOf(readView)), readView.remaining().toLong())
        }
    }

    /**
     * @return The number of bytes written to this output so far.
     */
    fun size(): Long {
        var size: Long = 0
        val blockCount: Int = blocks.size
        if (blockCount >= 1) {
            val fullBlockSize = (blockCount - 1L) * blockSize()
            val lastBlockSize = blocks.getLast().position.toLong()
            size = fullBlockSize + lastBlockSize
        }
        return size
    }

    override fun toString(): String {
        return "${size()} bytes, block size: ${blockSize()}, blocks: ${blocks.size}"
    }

    // Specialized versions of writeXXX methods that break execution into
    // fast/ slow path if the result would fall on the current block's
    // boundary.
    //
    // We also remove the IOException from methods because it (theoretically)
    // cannot be thrown from byte buffers.
    override fun writeShort(v: Short) {
        try {
            if (currentBlock.remaining() >= Short.SIZE_BYTES) {
                currentBlock.putShort(v)
            } else {
                super.writeShort(v)
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeInt(v: Int) {
        try {
            if (currentBlock.remaining() >= Int.SIZE_BYTES) {
                currentBlock.putInt(v)
            } else {
                super.writeInt(v)
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeLong(v: Long) {
        try {
            if (currentBlock.remaining() >= Long.SIZE_BYTES) {
                currentBlock.putLong(v)
            } else {
                super.writeLong(v)
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    /**
     * Expert: Creates a new output with custom parameters.
     *
     * @param minBitsPerBlock minimum bits per block
     * @param maxBitsPerBlock maximum bits per block
     * @param blockAllocate block allocator
     * @param blockReuse block recycler
     */
    /** Creates a new output with all defaults.  */
    init {
        require(minBitsPerBlock >= LIMIT_MIN_BITS_PER_BLOCK) {
            "minBitsPerBlock ($minBitsPerBlock) too small, must be at least $LIMIT_MIN_BITS_PER_BLOCK"
        }
        require(maxBitsPerBlock <= LIMIT_MAX_BITS_PER_BLOCK) {
            "maxBitsPerBlock ($maxBitsPerBlock) too large, must not exceed $LIMIT_MAX_BITS_PER_BLOCK"
        }
        require(minBitsPerBlock <= maxBitsPerBlock) {
            "minBitsPerBlock ($minBitsPerBlock) cannot exceed maxBitsPerBlock ($maxBitsPerBlock)"
        }
        this.maxBitsPerBlock = maxBitsPerBlock
        this.blockBits = minBitsPerBlock
        this.blockAllocate = requireNotNull(
            blockAllocate
        ) { "Block allocator must not be null." }
        this.blockReuse = requireNotNull(
            blockReuse
        ) { "Block reuse must not be null." }
    }

    override fun writeString(s: String) {

        if (s.isEmpty()) {
            writeVInt(0)
            return
        }

        // Calculate UTF-8 byte length
        val byteLen = UnicodeUtil.calcUTF16toUTF8Length(s, 0, s.length)
        writeVInt(byteLen)

        if (currentBlock.remaining() >= byteLen) {
            // Have enough space, directly encode into buffer
            // Create a temporary byte array
            val bytes = ByteArray(byteLen)
            UnicodeUtil.UTF16toUTF8(s, 0, s.length, bytes)

            // Write the bytes to the buffer
            currentBlock.put(bytes)
        } else {
            // Not enough space, need to handle buffer expansion
            // Create a temporary byte array
            val bytes = ByteArray(byteLen)
            UnicodeUtil.UTF16toUTF8(s, 0, s.length, bytes)

            // Write bytes in chunks
            var bytesWritten = 0
            while (bytesWritten < byteLen) {
                val remaining = currentBlock.remaining()
                if (remaining == 0) {
                    // Get new buffer
                    appendBlock()
                }

                val bytesToWrite = minOf(remaining, byteLen - bytesWritten)
                currentBlock.put(bytes, bytesWritten, bytesToWrite)
                bytesWritten += bytesToWrite
            }
        }
    }

    override fun writeMapOfStrings(map: MutableMap<String, String>) {
        try {
            super.writeMapOfStrings(map)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun writeSetOfStrings(set: MutableSet<String>) {
        try {
            super.writeSetOfStrings(set)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun ramBytesUsed(): Long {
        // Return a rough estimation for allocated blocks. Note that we do not make
        // any special distinction for direct memory buffers.
        require(
            ramBytesUsed
                    == blocks.sumOf { obj: ByteBuffer -> obj.capacity.toLong() }
                    + blocks.size.toLong() * RamUsageEstimator.NUM_BYTES_OBJECT_REF
        )
        return ramBytesUsed
    }

    /**
     * This method resets this object to a clean (zero-size) state and publishes any currently
     * allocated buffers for reuse to the reuse strategy provided in the constructor.
     *
     *
     * Sharing byte buffers for reads and writes is dangerous and will very likely lead to
     * hard-to-debug issues, use with great care.
     */
    fun reset() {
        if (blockReuse !== NO_REUSE) {
            blocks.forEach(blockReuse)
        }
        blocks.clear()
        ramBytesUsed = 0
        currentBlock = EMPTY
    }

    private fun blockSize(): Int {
        return 1 shl blockBits
    }

    private fun appendBlock() {
        if (blocks.size >= MAX_BLOCKS_BEFORE_BLOCK_EXPANSION && blockBits < maxBitsPerBlock) {
            rewriteToBlockSize(blockBits + 1)
            if (blocks.getLast().hasRemaining()) {
                return
            }
        }

        val requiredBlockSize = 1 shl blockBits
        currentBlock = blockAllocate(requiredBlockSize).order(ByteOrder.LITTLE_ENDIAN)
        require(currentBlock.capacity == requiredBlockSize)
        blocks.add(currentBlock)
        ramBytesUsed += RamUsageEstimator.NUM_BYTES_OBJECT_REF + currentBlock.capacity
    }

    private fun rewriteToBlockSize(targetBlockBits: Int) {
        require(targetBlockBits <= maxBitsPerBlock)

        // We copy over data blocks to an output with one-larger block bit size.
        // We also discard references to blocks as we're copying to allow GC to
        // clean up partial results in case of memory pressure.
        val cloned =
            ByteBuffersDataOutput(targetBlockBits, targetBlockBits, blockAllocate, NO_REUSE)
        while (true) {
            val block = blocks.pollFirst() ?: break
            block.flip()
            cloned.writeBytes(block)
            if (blockReuse !== NO_REUSE) {
                blockReuse(block)
            }
        }

        require(blocks.isEmpty())
        this.blockBits = targetBlockBits
        blocks.addAll(cloned.blocks)
        ramBytesUsed = cloned.ramBytesUsed
    }

    /** Writes a long string in chunks  */
    @Throws(IOException::class)
    private fun writeLongString(byteLen: Int, s: String) {
        val buf =
            ByteArray(min(byteLen, UnicodeUtil.MAX_UTF8_BYTES_PER_CHAR * MAX_CHARS_PER_WINDOW))
        var i = 0
        val end = s.length
        while (i < end) {
            // do one fewer chars than MAX_CHARS_PER_WINDOW in case we run into an unpaired surrogate
            // below and need to increase the step to cover the lower surrogate as well
            var step = min(end - i, MAX_CHARS_PER_WINDOW - 1)
            if (i + step < end && Character.isHighSurrogate(s[i + step - 1])) {
                step++
            }
            val upTo: Int = UnicodeUtil.UTF16toUTF8(s, i, step, buf)
            writeBytes(buf, 0, upTo)
            i += step
        }
    }

    companion object {
        private val EMPTY: ByteBuffer = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN)

        private val EMPTY_BYTE_ARRAY = byteArrayOf()

        val ALLOCATE_BB_ON_HEAP: (Int) -> ByteBuffer =
            { capacity: Int -> ByteBuffer.allocate(capacity) }

        /** A singleton instance of "no-reuse" buffer strategy.  */
        val NO_REUSE: (ByteBuffer) -> Unit =
            { _: ByteBuffer ->
                throw RuntimeException("reset() is not allowed on this buffer.")
            }

        /** Default `minBitsPerBlock`  */
        const val DEFAULT_MIN_BITS_PER_BLOCK: Int = 10 // 1024 B

        /** Default `maxBitsPerBlock`  */
        const val DEFAULT_MAX_BITS_PER_BLOCK: Int = 26 //   64 MB

        /** Smallest `minBitsPerBlock` allowed  */
        const val LIMIT_MIN_BITS_PER_BLOCK: Int = 1

        /** Largest `maxBitsPerBlock` allowed  */
        const val LIMIT_MAX_BITS_PER_BLOCK: Int = 31

        /**
         * Maximum number of blocks at the current [.blockBits] block size before we increase the
         * block size (and thus decrease the number of blocks).
         */
        const val MAX_BLOCKS_BEFORE_BLOCK_EXPANSION: Int = 100

        private const val MAX_CHARS_PER_WINDOW = 1024

        /** kotlin version original const*/
        private const val COPY_BUFFER_SIZE = 8192

        /**
         * @return Returns a new [ByteBuffersDataOutput] with the [.reset] capability.
         */
        // TODO: perhaps we can move it out to an utility class (as a supplier of preconfigured
        // instances)
        fun newResettableInstance(): ByteBuffersDataOutput {
            val reuser =
                ByteBufferRecycler(ALLOCATE_BB_ON_HEAP)
            return ByteBuffersDataOutput(
                DEFAULT_MIN_BITS_PER_BLOCK,
                DEFAULT_MAX_BITS_PER_BLOCK,
                { size: Int -> reuser.allocate(size) },
                { buffer: ByteBuffer -> reuser.reuse(buffer) })
        }

        private fun computeBlockSizeBitsFor(bytes: Long): Int {
            val powerOfTwo: Long = BitUtil.nextHighestPowerOfTwo(bytes / MAX_BLOCKS_BEFORE_BLOCK_EXPANSION)
            if (powerOfTwo == 0L) {
                return DEFAULT_MIN_BITS_PER_BLOCK
            }

            var blockBits: Int = Long.numberOfTrailingZeros(powerOfTwo)
            blockBits = min(blockBits, DEFAULT_MAX_BITS_PER_BLOCK)
            blockBits = max(blockBits, DEFAULT_MIN_BITS_PER_BLOCK)
            return blockBits
        }
    }
}