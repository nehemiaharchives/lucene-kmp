package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.util.ByteBlockPool.Companion.BYTE_BLOCK_MASK
import org.gnit.lucenekmp.util.ByteBlockPool.Companion.BYTE_BLOCK_SHIFT
import org.gnit.lucenekmp.util.ByteBlockPool.DirectAllocator
import org.gnit.lucenekmp.util.BytesRefHash.MaxBytesLengthExceededException


/**
 * Represents a logical list of ByteRef backed by a [ByteBlockPool]. It uses up to two bytes
 * to record the length of the BytesRef followed by the actual bytes. They can be read using the
 * start position returned when they are appended.
 *
 *
 * The [BytesRef] is written so it never crosses the [ByteBlockPool.BYTE_BLOCK_SIZE]
 * boundary. The limit of the largest [BytesRef] is therefore [ ][ByteBlockPool.BYTE_BLOCK_SIZE]-2 bytes.
 *
 * @lucene.internal
 */
class BytesRefBlockPool : Accountable {
    private val byteBlockPool: ByteBlockPool

    constructor() {
        this.byteBlockPool = ByteBlockPool(DirectAllocator())
    }

    constructor(byteBlockPool: ByteBlockPool) {
        this.byteBlockPool = byteBlockPool
    }

    /** Reset this buffer to the empty state.  */
    fun reset() {
        byteBlockPool.reset(false, false) // we don't need to 0-fill the buffers
    }

    /**
     * Populates the given BytesRef with the term starting at *start*.
     *
     * @see .fillBytesRef
     */
    fun fillBytesRef(term: BytesRef, start: Int) {
        term.bytes = byteBlockPool.getBuffer(start shr BYTE_BLOCK_SHIFT)
        val bytes = term.bytes
        val pos = start and BYTE_BLOCK_MASK
        if ((bytes[pos].toInt() and 0x80) == 0) {
            // length is 1 byte
            term.length = bytes[pos].toInt()
            term.offset = pos + 1
        } else {
            // length is 2 bytes

            val beShort = bytes.getShortBE(pos)

            term.length = (/*BitUtil.VH_BE_SHORT.get(bytes, pos) as Short*/ beShort ).toInt() and 0x7FFF
            term.offset = pos + 2
        }
        require(term.length >= 0)
    }

    /**
     * Add a term returning the start position on the underlying [ByteBlockPool]. THis can be
     * used to read back the value using [.fillBytesRef].
     *
     * @see .fillBytesRef
     */
    fun addBytesRef(bytes: BytesRef): Int {
        val length = bytes.length
        val len2 = 2 + bytes.length
        if (len2 + byteBlockPool.byteUpto > ByteBlockPool.BYTE_BLOCK_SIZE) {
            if (len2 > ByteBlockPool.BYTE_BLOCK_SIZE) {
                throw MaxBytesLengthExceededException(
                    "bytes can be at most " + (ByteBlockPool.BYTE_BLOCK_SIZE - 2) + " in length; got " + bytes.length
                )
            }
            byteBlockPool.nextBuffer()
        }
        val buffer: ByteArray = byteBlockPool.buffer!!
        val bufferUpto = byteBlockPool.byteUpto
        val textStart = bufferUpto + byteBlockPool.byteOffset

        // We first encode the length, followed by the
        // bytes. Length is encoded as vInt, but will consume
        // 1 or 2 bytes at most (we reject too-long terms,
        // above).
        if (length < 128) {
            // 1 byte to store length
            buffer[bufferUpto] = length.toByte()
            byteBlockPool.byteUpto += length + 1
            require(length >= 0) { "Length must be positive: $length" }
            /*java.lang.System.arraycopy(bytes.bytes, bytes.offset, buffer, bufferUpto + 1, length)*/
            bytes.bytes.copyInto(
                destination = buffer,
                destinationOffset = bufferUpto + 1,
                startIndex = bytes.offset,
                endIndex = bytes.offset + length
            )
        } else {
            // 2 byte to store length

            buffer.setShortBE(bufferUpto, (length or 0x8000).toShort())
            /*BitUtil.VH_BE_SHORT.set(buffer, bufferUpto, (length or 0x8000).toShort())*/
            byteBlockPool.byteUpto += length + 2
            /*java.lang.System.arraycopy(bytes.bytes, bytes.offset, buffer, bufferUpto + 2, length)*/
            bytes.bytes.copyInto(
                destination = buffer,
                destinationOffset = bufferUpto + 2,
                startIndex = bytes.offset,
                endIndex = bytes.offset + length
            )
        }
        return textStart
    }

    /**
     * Computes the hash of the BytesRef at the given start. This is equivalent of doing:
     *
     * <pre>
     * BytesRef bytes = new BytesRef();
     * fillTerm(bytes, start);
     * BytesRefHash.doHash(bytes.bytes, bytes.pos, bytes.len);
    </pre> *
     *
     * It just saves the work of filling the BytesRef.
     */
    fun hash(start: Int): Int {
        val offset = start and BYTE_BLOCK_MASK
        val bytes = byteBlockPool.getBuffer(start shr BYTE_BLOCK_SHIFT)
        val len: Int
        val pos: Int
        if ((bytes[offset].toInt() and 0x80) == 0) {
            // length is 1 byte
            len = bytes[offset].toInt()
            pos = offset + 1
        } else {

            val beShort = bytes.getShortBE(offset)

            len = (/*BitUtil.VH_BE_SHORT.get(bytes, offset) as Short*/ beShort ).toInt() and 0x7FFF
            pos = offset + 2
        }
        return BytesRefHash.doHash(bytes, pos, len)
    }

    /**
     * Computes the equality between the BytesRef at the start position with the provided BytesRef.
     * This is equivalent of doing:
     *
     * <pre>
     * BytesRef bytes = new BytesRef();
     * fillTerm(bytes, start);
     * Arrays.equals(bytes.bytes, bytes.offset, bytes.offset + length, b.bytes, b.offset, b.offset + b.length);
    </pre> *
     *
     * It just saves the work of filling the BytesRef.
     */
    fun equals(start: Int, b: BytesRef): Boolean {
        val bytes = byteBlockPool.getBuffer(start shr BYTE_BLOCK_SHIFT)
        val pos = start and BYTE_BLOCK_MASK
        val length: Int
        val offset: Int
        if ((bytes[pos].toInt() and 0x80) == 0) {
            // length is 1 byte
            length = bytes[pos].toInt()
            offset = pos + 1
        } else {
            // length is 2 bytes

            val beShort = bytes.getShortBE(pos)

            length = (/*BitUtil.VH_BE_SHORT.get(bytes, pos) as Short*/ beShort ).toInt() and 0x7FFF
            offset = pos + 2
        }
        return Arrays.equals(bytes, offset, offset + length, b.bytes, b.offset, b.offset + b.length)
    }

    public override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES + byteBlockPool.ramBytesUsed()
    }

    companion object {
        private val BASE_RAM_BYTES = RamUsageEstimator.shallowSizeOfInstance(BytesRefBlockPool::class)
    }
}
