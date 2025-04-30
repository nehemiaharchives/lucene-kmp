package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import kotlin.jvm.JvmOverloads
import kotlin.math.min


/**
 * This class enables the allocation of fixed-size buffers and their management as part of a buffer
 * array. Allocation is done through the use of an [Allocator] which can be customized, e.g.
 * to allow recycling old buffers. There are methods for writing ([.append] and
 * reading from the buffers (e.g. [.readBytes], which handle
 * read/write operations across buffer boundaries.
 *
 * @lucene.internal
 */
class ByteBlockPool(private val allocator: Allocator) : Accountable {
    /** Abstract class for allocating and freeing byte blocks.  */
    abstract class Allocator protected constructor(// TODO: ByteBlockPool assume the blockSize is always {@link BYTE_BLOCK_SIZE}, but this class
        // allow arbitrary value of blockSize. We should make them consistent.
        val blockSize: Int
    ) {
        abstract fun recycleByteBlocks(blocks: Array<ByteArray?>, start: Int, end: Int)

        open val byteBlock: ByteArray
            get() = ByteArray(blockSize)
    }

    /** A simple [Allocator] that never recycles.  */
    class DirectAllocator : Allocator(BYTE_BLOCK_SIZE) {
        override fun recycleByteBlocks(blocks: Array<ByteArray?>, start: Int, end: Int) {}
    }

    /** A simple [Allocator] that never recycles, but tracks how much total RAM is in use.  */
    class DirectTrackingAllocator(private val bytesUsed: Counter) : Allocator(BYTE_BLOCK_SIZE) {

        override val byteBlock: ByteArray
            get() {
                bytesUsed.addAndGet(blockSize.toLong())
                return ByteArray(blockSize)
            }

        override fun recycleByteBlocks(blocks: Array<ByteArray?>, start: Int, end: Int) {
            bytesUsed.addAndGet(-((end - start) * blockSize).toLong())
            for (i in start..<end) {
                blocks[i] = null
            }
        }
    }

    /** Array of buffers currently used in the pool. Buffers are allocated if needed.  */
    private var buffers: Array<ByteArray?> = kotlin.arrayOfNulls<ByteArray>(10)

    /** index into the buffers array pointing to the current buffer used as the head  */
    private var bufferUpto = -1 // Which buffer we are upto

    /** Where we are in the head buffer.  */
    var byteUpto: Int = BYTE_BLOCK_SIZE

    /** Current head buffer.  */
    var buffer: ByteArray? = null

    /**
     * Offset from the start of the first buffer to the start of the current buffer, which is
     * bufferUpto * BYTE_BLOCK_SIZE. The buffer pool maintains this offset because it is the first to
     * overflow if there are too many allocated blocks.
     */
    var byteOffset: Int = -BYTE_BLOCK_SIZE

    /**
     * Expert: Resets the pool to its initial state, while optionally reusing the first buffer.
     * Buffers that are not reused are reclaimed by [Allocator.recycleByteBlocks]. Buffers can be filled with zeros before recycling them. This is useful if a slice pool
     * works on top of this byte pool and relies on the buffers being filled with zeros to find the
     * non-zero end of slices.
     *
     * @param zeroFillBuffers if `true` the buffers are filled with `0`. This should be
     * set to `true` if this pool is used with slices.
     * @param reuseFirst if `true` the first buffer will be reused and calling [     ][ByteBlockPool.nextBuffer] is not needed after reset iff the block pool was used before
     * ie. [ByteBlockPool.nextBuffer] was called before.
     */
    fun reset(zeroFillBuffers: Boolean, reuseFirst: Boolean) {
        if (bufferUpto != -1) {
            // We allocated at least one buffer

            if (zeroFillBuffers) {
                for (i in 0..<bufferUpto) {
                    // Fully zero fill buffers that we fully used
                    Arrays.fill(a = buffers[i]!!, value = 0.toByte())
                }
                // Partial zero fill the final buffer
                Arrays.fill(buffers[bufferUpto]!!, 0, byteUpto, 0.toByte())
            }

            if (bufferUpto > 0 || !reuseFirst) {
                val offset = if (reuseFirst) 1 else 0
                // Recycle all but the first buffer
                allocator.recycleByteBlocks(buffers, offset, 1 + bufferUpto)
                Arrays.fill(buffers, offset, 1 + bufferUpto, null)
            }
            if (reuseFirst) {
                // Re-use the first buffer
                bufferUpto = 0
                byteUpto = 0
                byteOffset = 0
                buffer = buffers[0]
            } else {
                bufferUpto = -1
                byteUpto = BYTE_BLOCK_SIZE
                byteOffset = -BYTE_BLOCK_SIZE
                buffer = null
            }
        }
    }

    /**
     * Allocates a new buffer and advances the pool to it. This method should be called once after the
     * constructor to initialize the pool. In contrast to the constructor, a [ ][ByteBlockPool.reset] call will advance the pool to its first buffer
     * immediately.
     */
    fun nextBuffer() {
        if (1 + bufferUpto == buffers.size) {
            // The buffer array is full - expand it
            val newBuffers: Array<ByteArray?> =
                kotlin.arrayOfNulls<ByteArray>(ArrayUtil.oversize(buffers.size + 1, RamUsageEstimator.NUM_BYTES_OBJECT_REF))
            /*java.lang.System.arraycopy(buffers, 0, newBuffers, 0, buffers.size)*/
            buffers.copyInto(
                destination = newBuffers,
                destinationOffset = 0,
                startIndex = 0,
                endIndex = buffers.size
            )
            buffers = newBuffers
        }
        // Allocate new buffer and advance the pool to it
        buffers[1 + bufferUpto] = allocator.byteBlock!!
        buffer = buffers[1 + bufferUpto]
        bufferUpto++
        byteUpto = 0
        byteOffset = Math.addExact(byteOffset, BYTE_BLOCK_SIZE)
    }

    /**
     * Fill the provided [BytesRef] with the bytes at the specified offset and length. This will
     * avoid copying the bytes if the slice fits into a single block; otherwise, it uses the provided
     * [BytesRefBuilder] to copy bytes over.
     */
    fun setBytesRef(builder: BytesRefBuilder, result: BytesRef, offset: Long, length: Int) {
        result.length = length

        val bufferIndex: Int = Math.toIntExact(offset shr BYTE_BLOCK_SHIFT)
        val buffer: ByteArray? = buffers[bufferIndex]
        val pos = (offset and BYTE_BLOCK_MASK.toLong()).toInt()
        if (pos + length <= BYTE_BLOCK_SIZE) {
            // Common case: The slice lives in a single block. Reference the buffer directly.
            result.bytes = buffer!!
            result.offset = pos
        } else {
            // Uncommon case: The slice spans at least 2 blocks, so we must copy the bytes.
            builder.growNoCopy(length)
            result.bytes = builder.get().bytes
            result.offset = 0
            readBytes(offset, result.bytes, 0, length)
        }
    }

    /** Appends the bytes in the provided [BytesRef] at the current position.  */
    fun append(bytes: BytesRef) {
        append(bytes.bytes, bytes.offset, bytes.length)
    }

    /**
     * Append the bytes from a source [ByteBlockPool] at a given offset and length
     *
     * @param srcPool the source pool to copy from
     * @param srcOffset the source pool offset
     * @param length the number of bytes to copy
     */
    fun append(srcPool: ByteBlockPool, srcOffset: Long, length: Int) {
        var srcOffset = srcOffset
        var bytesLeft = length
        while (bytesLeft > 0) {
            val bufferLeft = BYTE_BLOCK_SIZE - byteUpto
            if (bytesLeft < bufferLeft) { // fits within current buffer
                appendBytesSingleBuffer(srcPool, srcOffset, bytesLeft)
                break
            } else { // fill up this buffer and move to next one
                if (bufferLeft > 0) {
                    appendBytesSingleBuffer(srcPool, srcOffset, bufferLeft)
                    bytesLeft -= bufferLeft
                    srcOffset += bufferLeft.toLong()
                }
                nextBuffer()
            }
        }
    }

    // copy from source pool until no bytes left. length must be fit within the current head buffer
    private fun appendBytesSingleBuffer(srcPool: ByteBlockPool, srcOffset: Long, length: Int) {
        var srcOffset = srcOffset
        var length = length
        require(length <= BYTE_BLOCK_SIZE - byteUpto)
        // doing a loop as the bytes to copy might span across multiple byte[] in srcPool
        while (length > 0) {
            val srcBytes = srcPool.buffers[Math.toIntExact(srcOffset shr BYTE_BLOCK_SHIFT)]!!
            val srcPos: Int = Math.toIntExact(srcOffset and BYTE_BLOCK_MASK.toLong())
            val bytesToCopy = min(length, BYTE_BLOCK_SIZE - srcPos)
            /*java.lang.System.arraycopy(srcBytes, srcPos, buffer, byteUpto, bytesToCopy)*/
            srcBytes.copyInto(
                destination = buffer!!,
                destinationOffset = byteUpto,
                startIndex = srcPos,
                endIndex = srcPos + bytesToCopy
            )
            length -= bytesToCopy
            srcOffset += bytesToCopy.toLong()
            byteUpto += bytesToCopy
        }
    }

    /**
     * Append some portion of the provided byte array at the current position.
     *
     * @param bytes the byte array to write
     * @param offset the offset of the byte array
     * @param length the number of bytes to write
     */
    /**
     * Append the provided byte array at the current position.
     *
     * @param bytes the byte array to write
     */
    @JvmOverloads
    fun append(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        var offset = offset
        var bytesLeft = length
        while (bytesLeft > 0) {
            val bufferLeft = BYTE_BLOCK_SIZE - byteUpto
            if (bytesLeft < bufferLeft) {
                // fits within current buffer
                /*java.lang.System.arraycopy(bytes, offset, buffer, byteUpto, bytesLeft)*/
                bytes.copyInto(
                    destination = buffer!!,
                    destinationOffset = byteUpto,
                    startIndex = offset,
                    endIndex = offset + bytesLeft
                )
                byteUpto += bytesLeft
                break
            } else {
                // fill up this buffer and move to next one
                if (bufferLeft > 0) {
                    /*java.lang.System.arraycopy(bytes, offset, buffer, byteUpto, bufferLeft)*/
                    bytes.copyInto(
                        destination = buffer!!,
                        destinationOffset = byteUpto,
                        startIndex = offset,
                        endIndex = offset + bufferLeft
                    )
                }
                nextBuffer()
                bytesLeft -= bufferLeft
                offset += bufferLeft
            }
        }
    }

    /**
     * Reads bytes out of the pool starting at the given offset with the given length into the given
     * byte array at offset `off`.
     *
     *
     * Note: this method allows to copy across block boundaries.
     */
    fun readBytes(offset: Long, bytes: ByteArray, bytesOffset: Int, bytesLength: Int) {
        var bytesOffset = bytesOffset
        var bytesLeft = bytesLength
        var bufferIndex: Int = Math.toIntExact(offset shr BYTE_BLOCK_SHIFT)
        var pos = (offset and BYTE_BLOCK_MASK.toLong()).toInt()
        while (bytesLeft > 0) {
            val buffer = checkNotNull(buffers[bufferIndex++])
            val chunk = min(bytesLeft, BYTE_BLOCK_SIZE - pos)
            /*java.lang.System.arraycopy(buffer, pos, bytes, bytesOffset, chunk)*/
            buffer.copyInto(
                destination = bytes,
                destinationOffset = bytesOffset,
                startIndex = pos,
                endIndex = pos + chunk
            )
            bytesOffset += chunk
            bytesLeft -= chunk
            pos = 0
        }
    }

    /**
     * Read a single byte at the given offset
     *
     * @param offset the offset to read
     * @return the byte
     */
    fun readByte(offset: Long): Byte {
        val bufferIndex = (offset shr BYTE_BLOCK_SHIFT).toInt()
        val pos = (offset and BYTE_BLOCK_MASK.toLong()).toInt()
        return buffers[bufferIndex]!![pos]
    }

    override fun ramBytesUsed(): Long {
        var size = BASE_RAM_BYTES
        size += RamUsageEstimator.shallowSizeOf(buffers)
        for (buf in buffers) {
            size += RamUsageEstimator.sizeOfObject(buf)
        }
        return size
    }

    val position: Long
        /** the current position (in absolute value) of this byte pool  */
        get() = (bufferUpto * allocator.blockSize + byteUpto).toLong()

    /** Retrieve the buffer at the specified index from the buffer pool.  */
    fun getBuffer(bufferIndex: Int): ByteArray {
        return buffers[bufferIndex]!!
    }

    companion object {
        private val BASE_RAM_BYTES = RamUsageEstimator.shallowSizeOfInstance(ByteBlockPool::class)

        /**
         * Use this to find the index of the buffer containing a byte, given an offset to that byte.
         *
         *
         * bufferUpto = globalOffset &gt;&gt; BYTE_BLOCK_SHIFT
         *
         *
         * bufferUpto = globalOffset / BYTE_BLOCK_SIZE
         */
        const val BYTE_BLOCK_SHIFT: Int = 15

        /** The size of each buffer in the pool.  */
        const val BYTE_BLOCK_SIZE: Int = 1 shl BYTE_BLOCK_SHIFT

        /**
         * Use this to find the position of a global offset in a particular buffer.
         *
         *
         * positionInCurrentBuffer = globalOffset &amp; BYTE_BLOCK_MASK
         *
         *
         * positionInCurrentBuffer = globalOffset % BYTE_BLOCK_SIZE
         */
        const val BYTE_BLOCK_MASK: Int = BYTE_BLOCK_SIZE - 1
    }
}
