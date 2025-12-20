package org.gnit.lucenekmp.store

import okio.EOFException
import okio.IOException
import org.gnit.lucenekmp.jdkport.BufferUnderflowException
import org.gnit.lucenekmp.jdkport.toHexString
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.ByteOrder
import org.gnit.lucenekmp.jdkport.FloatBuffer
import org.gnit.lucenekmp.jdkport.LongBuffer
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.GroupVIntUtil
import org.gnit.lucenekmp.util.RamUsageEstimator

import kotlin.math.min


/**
 * A [DataInput] implementing [RandomAccessInput] and reading data from a list of [ ]s.
 */
class ByteBuffersDataInput(buffers: MutableList<ByteBuffer>) : DataInput(), Accountable, RandomAccessInput {
    private val blocks: Array<ByteBuffer>
    private val floatBuffers: Array<FloatBuffer?>
    private val longBuffers: Array<LongBuffer?>
    private var blockBits = 0
    private var blockMask = 0
    private val length: Long
    private val offset: Long

    private var pos: Long

    /**
     * Read data from a set of contiguous buffers. All data buffers except for the last one must have
     * an identical remaining number of bytes in the buffer (that is a power of two). The last buffer
     * can be of an arbitrary remaining length.
     */
    init {
        ensureAssumptions(buffers)

        this.blocks = buffers.toTypedArray<ByteBuffer>()
        for (i in blocks.indices) {
            blocks[i] = blocks[i].asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        }
        // pre-allocate these arrays and create the view buffers lazily
        this.floatBuffers = kotlin.arrayOfNulls<FloatBuffer>(blocks.size * Float.SIZE_BYTES)
        this.longBuffers = kotlin.arrayOfNulls<LongBuffer>(blocks.size * Long.SIZE_BYTES)
        if (blocks.size == 1) {
            this.blockBits = 32
            this.blockMask = 0.inv()
        } else {
            val blockBytes = determineBlockPage(buffers)
            this.blockBits = Int.numberOfTrailingZeros(blockBytes)
            this.blockMask = (1 shl blockBits) - 1
        }

        var length: Long = 0
        for (block in blocks) {
            length += block.remaining().toLong()
        }
        this.length = length

        // The initial "position" of this stream is shifted by the position of the first block.
        this.offset = blocks[0].position.toLong()
        this.pos = offset
    }

    override fun ramBytesUsed(): Long {
        // Return a rough estimation for allocated blocks. Note that we do not make
        // any special distinction for what the type of buffer is (direct vs. heap-based).
        return (RamUsageEstimator.NUM_BYTES_OBJECT_REF.toLong() * blocks.size
                + blocks.sumOf { buf -> buf.capacity.toLong() })
    }

    override fun readByte(): Byte {
        try {
            val block: ByteBuffer = blocks[blockIndex(pos)]
            val v: Byte = block.get(blockOffset(pos))
            pos++
            return v
        } catch (e: IndexOutOfBoundsException) {
            if (pos >= length()) {
                throw EOFException()
            } else {
                throw e // Something is wrong.
            }
        }
    }

    /**
     * Reads exactly `len` bytes into the given buffer. The buffer must have enough remaining
     * limit.
     *
     *
     * If there are fewer than `len` bytes in the input, [EOFException] is thrown.
     */
    @Throws(EOFException::class)
    fun readBytes(buffer: ByteBuffer, len: Int) {
        var len = len
        try {
            while (len > 0) {
                val block: ByteBuffer = blocks[blockIndex(pos)].duplicate()
                val blockOffset = blockOffset(pos)
                block.position(blockOffset)
                val chunk = min(len, block.remaining())
                if (chunk == 0) {
                    throw EOFException()
                }

                // Update pos early on for EOF detection on output buffer, then try to get buffer content.
                pos += chunk.toLong()
                block.limit(blockOffset + chunk)
                buffer.put(block)

                len -= chunk
            }
        } catch (e: BufferUnderflowException) {
            if (pos >= length()) {
                throw EOFException()
            } else {
                throw e // Something is wrong.
            }
        } catch (e: IndexOutOfBoundsException) {
            if (pos >= length()) {
                throw EOFException()
            } else {
                throw e
            }
        }
    }

    override fun readBytes(arr: ByteArray, off: Int, len: Int) {
        var off = off
        var len = len
        try {
            while (len > 0) {
                val block: ByteBuffer = blocks[blockIndex(pos)].duplicate()
                block.position(blockOffset(pos))
                val chunk = min(len, block.remaining())
                if (chunk == 0) {
                    throw EOFException()
                }

                // Update pos early on for EOF detection, then try to get buffer content.
                pos += chunk.toLong()
                block.get(arr, off, chunk)

                len -= chunk
                off += chunk
            }
        } catch (e: BufferUnderflowException) {
            if (pos >= length()) {
                throw EOFException()
            } else {
                throw e // Something is wrong.
            }
        } catch (e: IndexOutOfBoundsException) {
            if (pos >= length()) {
                throw EOFException()
            } else {
                throw e
            }
        }
    }

    @Throws(IOException::class)
    override fun readShort(): Short {
        val blockOffset = blockOffset(pos)
        if (blockOffset + Short.SIZE_BYTES <= blockMask) {
            val v: Short = blocks[blockIndex(pos)].getShort(blockOffset)
            pos += Short.SIZE_BYTES.toLong()
            return v
        } else {
            return super.readShort()
        }
    }

    @Throws(IOException::class)
    override fun readInt(): Int {
        val blockOffset = blockOffset(pos)
        if (blockOffset + Int.SIZE_BYTES <= blockMask) {
            val v: Int = blocks[blockIndex(pos)].getInt(blockOffset)
            pos += Int.SIZE_BYTES.toLong()
            return v
        } else {
            return super.readInt()
        }
    }

    @Throws(IOException::class)
    override fun readLong(): Long {
        val blockOffset = blockOffset(pos)
        if (blockOffset + Long.SIZE_BYTES <= blockMask) {
            val v: Long = blocks[blockIndex(pos)].getLong(blockOffset)
            pos += Long.SIZE_BYTES.toLong()
            return v
        } else {
            return super.readLong()
        }
    }

    @Throws(IOException::class)
    override fun readGroupVInt(dst: IntArray, offset: Int) {
        val block: ByteBuffer = blocks[blockIndex(pos)]
        val blockOffset = blockOffset(pos)
        // We MUST save the return value to local variable, could not use pos += readGroupVInt(...).
        // because `pos +=` in java will move current value(not address) of pos to register first,
        // then call the function, but we will update pos value in function via readByte(), then
        // `pos +=` will use an old pos value plus return value, thereby missing 1 byte.
        val len: Int =
            GroupVIntUtil.readGroupVInt(
                this,
                (block.limit - blockOffset).toLong(),
                { p -> block.getInt(p.toInt()) },
                blockOffset.toLong(),
                dst,
                offset
            )
        pos += len.toLong()
    }

    override fun length(): Long {
        return length
    }

    override fun readByte(pos: Long): Byte {
        var pos = pos
        pos += offset
        return blocks[blockIndex(pos)].get(blockOffset(pos))
    }

    @Throws(IOException::class)
    override fun readBytes(pos: Long, bytes: ByteArray, offset: Int, len: Int) {
        var offset = offset
        var len = len
        var absPos = this.offset + pos
        try {
            while (len > 0) {
                val block: ByteBuffer = blocks[blockIndex(absPos)]
                val blockPosition = blockOffset(absPos)
                // Respect the buffer limit: slices may have limit < capacity.
                val chunk = min(len, block.limit - blockPosition)
                if (chunk == 0) {
                    throw EOFException()
                }

                // Update pos early on for EOF detection, then try to get buffer content.
                block.get(blockPosition, bytes, offset, chunk)

                absPos += chunk.toLong()
                len -= chunk
                offset += chunk
            }
        } catch (e: BufferUnderflowException) {
            // absPos is absolute (includes this.offset), but length() is relative to this input.
            if (absPos - this.offset >= length()) {
                throw EOFException()
            } else {
                throw e // Something is wrong.
            }
        } catch (e: IndexOutOfBoundsException) {
            if (absPos - this.offset >= length()) {
                throw EOFException()
            } else {
                throw e
            }
        }
    }

    override fun readShort(pos: Long): Short {
        val absPos = offset + pos
        val blockOffset = blockOffset(absPos)
        return if (blockOffset + Short.SIZE_BYTES <= blockMask) {
            blocks[blockIndex(absPos)].getShort(blockOffset)
        } else {
            ((readByte(pos).toInt() and 0xFF) or ((readByte(pos + 1).toInt() and 0xFF) shl 8)).toShort()
        }
    }

    override fun readInt(pos: Long): Int {
        val absPos = offset + pos
        val blockOffset = blockOffset(absPos)
        return if (blockOffset + Int.SIZE_BYTES <= blockMask) {
            blocks[blockIndex(absPos)].getInt(blockOffset)
        } else {
            (((readByte(pos).toInt() and 0xFF)
                    or ((readByte(pos + 1).toInt() and 0xFF) shl 8
                    ) or ((readByte(pos + 2).toInt() and 0xFF) shl 16
                    ) or (readByte(pos + 3).toInt() shl 24)))
        }
    }

    override fun readLong(pos: Long): Long {
        val absPos = offset + pos
        val blockOffset = blockOffset(absPos)
        if (blockOffset + Long.SIZE_BYTES <= blockMask) {
            return blocks[blockIndex(absPos)].getLong(blockOffset)
        } else {
            val b1 = readByte(pos)
            val b2 = readByte(pos + 1)
            val b3 = readByte(pos + 2)
            val b4 = readByte(pos + 3)
            val b5 = readByte(pos + 4)
            val b6 = readByte(pos + 5)
            val b7 = readByte(pos + 6)
            val b8 = readByte(pos + 7)
            return ((b8.toLong() and 0xFFL) shl 56 or ((b7.toLong() and 0xFFL) shl 48
                    ) or ((b6.toLong() and 0xFFL) shl 40
                    ) or ((b5.toLong() and 0xFFL) shl 32
                    ) or ((b4.toLong() and 0xFFL) shl 24
                    ) or ((b3.toLong() and 0xFFL) shl 16
                    ) or ((b2.toLong() and 0xFFL) shl 8
                    ) or (b1.toLong() and 0xFFL))
        }
    }

    override fun readFloats(arr: FloatArray, off: Int, len: Int) {
        var off = off
        var len = len
        try {
            while (len > 0) {
                val floatBuffer: FloatBuffer = getFloatBuffer(pos)
                floatBuffer.position(blockOffset(pos) shr 2)
                val chunk = min(len, floatBuffer.remaining())
                if (chunk == 0) {
                    // read a single float spanning the boundary between two buffers
                    arr[off] = Float.intBitsToFloat(readInt(pos - offset))
                    off++
                    len--
                    pos += Float.SIZE_BYTES.toLong()
                    continue
                }

                // Update pos early on for EOF detection, then try to get buffer content.
                pos += (chunk shl 2).toLong()
                floatBuffer.get(arr, off, chunk)

                len -= chunk
                off += chunk
            }
        } catch (e: BufferUnderflowException) {
            if (pos - offset + Float.SIZE_BYTES > length()) {
                throw EOFException()
            } else {
                throw e // Something is wrong.
            }
        } catch (e: IndexOutOfBoundsException) {
            if (pos - offset + Float.SIZE_BYTES > length()) {
                throw EOFException()
            } else {
                throw e
            }
        }
    }

    override fun readLongs(arr: LongArray, off: Int, len: Int) {
        // Use the safe DataInput implementation to avoid alignment and buffer-view pitfalls.
        super.readLongs(arr, off, len)
    }

    private fun getFloatBuffer(pos: Long): FloatBuffer {
        // This creates a separate FloatBuffer for each observed combination of ByteBuffer/alignment
        val bufferIndex = blockIndex(pos)
        val alignment = pos.toInt() and 0x3
        val floatBufferIndex: Int = bufferIndex * Float.SIZE_BYTES + alignment
        if (floatBuffers[floatBufferIndex] == null) {
            val dup: ByteBuffer = blocks[bufferIndex].duplicate()
            dup.position(alignment)
            floatBuffers[floatBufferIndex] = dup.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        }
        return floatBuffers[floatBufferIndex]!!
    }

    private fun getLongBuffer(pos: Long): LongBuffer {
        // This creates a separate LongBuffer for each observed combination of ByteBuffer/alignment
        val bufferIndex = blockIndex(pos)
        val alignment = pos.toInt() and 0x7
        val longBufferIndex: Int = bufferIndex * Long.SIZE_BYTES + alignment
        if (longBuffers[longBufferIndex] == null) {
            val dup: ByteBuffer = blocks[bufferIndex].duplicate()
            dup.position(alignment)
            longBuffers[longBufferIndex] = dup.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
        }
        return longBuffers[longBufferIndex]!!
    }

    fun position(): Long {
        return pos - offset
    }

    @Throws(EOFException::class)
    fun seek(position: Long) {
        this.pos = position + offset
        if (position > length()) {
            this.pos = length()
            throw EOFException()
        }
    }

    @Throws(IOException::class)
    override fun skipBytes(numBytes: Long) {
        require(numBytes >= 0) { "numBytes must be >= 0, got $numBytes" }
        val skipTo = position() + numBytes
        seek(skipTo)
    }

    fun slice(offset: Long, length: Long): ByteBuffersDataInput {
        require(!((length or offset) < 0 || length > this.length - offset)) {
            "slice(offset=$offset, length=$length) is out of bounds: $this"
        }

        return ByteBuffersDataInput(
            sliceBufferList(
                this.blocks.toMutableList(),
                offset,
                length
            )
        )
    }

    override fun toString(): String {
        val offsetInfo = if (offset == 0L) "" else " [offset: ${offset}]"
        return "${length()} bytes, block size: ${blockSize()}, blocks: ${blocks.size}, position: ${position()}$offsetInfo"
    }

    private fun blockIndex(pos: Long): Int {
        return Math.toIntExact(pos shr blockBits)
    }

    private fun blockOffset(pos: Long): Int {
        return pos.toInt() and blockMask
    }

    private fun blockSize(): Int {
        return 1 shl blockBits
    }

    fun isLoaded(): Boolean {
        TODO() // had to inherit from RandomAccessInput
    }

    companion object {
        private fun isPowerOfTwo(v: Int): Boolean {
            return (v and (v - 1)) == 0
        }

        private fun ensureAssumptions(buffers: MutableList<ByteBuffer>) {
            require(!buffers.isEmpty()) { "Buffer list must not be empty." }

            if (buffers.size == 1) {
                // Special case of just a single buffer, conditions don't apply.
            } else {
                val blockPage = determineBlockPage(buffers)

                // First buffer decides on block page length.
                require(isPowerOfTwo(blockPage)) {
                    ("The first buffer must have power-of-two position() + remaining(): 0x"
                            + Int.toHexString(blockPage))
                }

                // Any block from 2..last-1 should have the same page size.
                var i = 1
                val last = buffers.size - 1
                while (i < last) {
                    val buffer: ByteBuffer = buffers[i]
                    require(buffer.position == 0) { "All buffers except for the first one must have position() == 0: $buffer" }
                    require(!(i != last && buffer.remaining() != blockPage)) {
                        ("Intermediate buffers must share an identical remaining() power-of-two block size: 0x"
                                + Int.toHexString(blockPage))
                    }
                    i++
                }
            }
        }

        fun determineBlockPage(buffers: MutableList<ByteBuffer>): Int {
            val first: ByteBuffer = buffers[0]
            val blockPage: Int = Math.toIntExact(first.position.toLong() + first.remaining())
            return blockPage
        }

        private fun sliceBufferList(
            buffers: MutableList<ByteBuffer>, offset: Long, length: Long
        ): MutableList<ByteBuffer> {
            ensureAssumptions(buffers)

            if (buffers.size == 1) {
                val cloned: ByteBuffer =
                    buffers[0].asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
                cloned.position(Math.toIntExact(cloned.position + offset))
                cloned.limit(Math.toIntExact(cloned.position + length))
                return mutableListOf(cloned)
            } else {
                val absStart: Long = buffers[0].position + offset
                val absEnd = absStart + length

                val blockBytes = determineBlockPage(buffers)
                val blockBits: Int = Int.numberOfTrailingZeros(blockBytes)
                val blockMask = (1L shl blockBits) - 1

                val endOffset: Int = Math.toIntExact(absEnd and blockMask)

                val cloned: MutableList<ByteBuffer> = buffers
                    .subList(
                        Math.toIntExact(absStart / blockBytes),
                        Math.toIntExact(absEnd / blockBytes + (if (endOffset == 0) 0 else 1))
                    )
                    .map { buf: ByteBuffer ->
                        buf.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
                    }
                    .toMutableList()

                if (endOffset == 0) {
                    cloned.add(ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN))
                }

                cloned[0].position(Math.toIntExact(absStart and blockMask))
                cloned[cloned.size - 1].limit(endOffset)
                return cloned
            }
        }
    }
}
