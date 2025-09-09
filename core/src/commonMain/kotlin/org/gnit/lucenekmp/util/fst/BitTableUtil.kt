package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.util.fst.FST.BytesReader
import okio.IOException
import org.gnit.lucenekmp.jdkport.bitCount
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros


/**
 * Static helper methods for [FST.Arc.BitTable].
 *
 * @lucene.experimental
 */
internal object BitTableUtil {
    /**
     * Returns whether the bit at given zero-based index is set. <br></br>
     * Example: bitIndex 10 means the third bit on the right of the second byte.
     *
     * @param bitIndex The bit zero-based index. It must be greater than or equal to 0, and strictly
     * less than `number of bit-table bytes * Byte.SIZE`.
     * @param reader The [FST.BytesReader] to read. It must be positioned at the beginning of
     * the bit-table.
     */
    @Throws(IOException::class)
    fun isBitSet(bitIndex: Int, reader: BytesReader): Boolean {
        require(bitIndex >= 0) { "bitIndex=$bitIndex" }
        reader.skipBytes((bitIndex shr 3).toLong())
        return (readByte(reader) and (1L shl (bitIndex and (Byte.SIZE_BITS - 1)))) != 0L
    }

    /**
     * Counts all bits set in the bit-table.
     *
     * @param bitTableBytes The number of bytes in the bit-table.
     * @param reader The [FST.BytesReader] to read. It must be positioned at the beginning of
     * the bit-table.
     */
    @Throws(IOException::class)
    fun countBits(bitTableBytes: Int, reader: BytesReader): Int {
        require(bitTableBytes >= 0) { "bitTableBytes=$bitTableBytes" }
        var bitCount = 0
        for (i in bitTableBytes shr 3 downTo 1) {
            // Count the bits set for all plain longs.
            bitCount += bitCount8Bytes(reader)
        }
        val numRemainingBytes: Int
        if (((bitTableBytes and (Long.SIZE_BYTES - 1)).also { numRemainingBytes = it }) != 0) {
            bitCount += Long.bitCount(readUpTo8Bytes(numRemainingBytes, reader))
        }
        return bitCount
    }

    /**
     * Counts the bits set up to the given bit zero-based index, exclusive. <br></br>
     * In other words, how many 1s there are up to the bit at the given index excluded. <br></br>
     * Example: bitIndex 10 means the third bit on the right of the second byte.
     *
     * @param bitIndex The bit zero-based index, exclusive. It must be greater than or equal to 0, and
     * less than or equal to `number of bit-table bytes * Byte.SIZE`.
     * @param reader The [FST.BytesReader] to read. It must be positioned at the beginning of
     * the bit-table.
     */
    @Throws(IOException::class)
    fun countBitsUpTo(bitIndex: Int, reader: BytesReader): Int {
        require(bitIndex >= 0) { "bitIndex=$bitIndex" }
        var bitCount = 0
        for (i in bitIndex shr 6 downTo 1) {
            // Count the bits set for all plain longs.
            bitCount += bitCount8Bytes(reader)
        }
        val remainingBits: Int
        if (((bitIndex and (Long.SIZE_BITS - 1)).also { remainingBits = it }) != 0) {
            val numRemainingBytes: Int = (remainingBits + (Byte.SIZE_BITS - 1)) shr 3
            // Prepare a mask with 1s on the right up to bitIndex exclusive.
            val mask = (1L shl bitIndex) - 1L // Shifts are mod 64.
            // Count the bits set only within the mask part, so up to bitIndex exclusive.
            bitCount += Long.bitCount(readUpTo8Bytes(numRemainingBytes, reader) and mask)
        }
        return bitCount
    }

    /**
     * Returns the index of the next bit set following the given bit zero-based index. <br></br>
     * For example with bits 100011: the next bit set after index=-1 is at index=0; the next bit set
     * after index=0 is at index=1; the next bit set after index=1 is at index=5; there is no next bit
     * set after index=5.
     *
     * @param bitIndex The bit zero-based index. It must be greater than or equal to -1, and strictly
     * less than `number of bit-table bytes * Byte.SIZE`.
     * @param bitTableBytes The number of bytes in the bit-table.
     * @param reader The [FST.BytesReader] to read. It must be positioned at the beginning of
     * the bit-table.
     * @return The zero-based index of the next bit set after the provided `bitIndex`; or -1 if
     * none.
     */
    @Throws(IOException::class)
    fun nextBitSet(bitIndex: Int, bitTableBytes: Int, reader: BytesReader): Int {
        require(
            bitIndex >= -1 && bitIndex < bitTableBytes * Byte.SIZE_BITS
        ) { "bitIndex=$bitIndex bitTableBytes=$bitTableBytes" }
        var byteIndex: Int = bitIndex / Byte.SIZE_BITS
        val mask = -1 shl ((bitIndex + 1) and (Byte.SIZE_BITS - 1))
        var i: Int
        if (mask == -1 && bitIndex != -1) {
            reader.skipBytes(byteIndex.toLong() + 1L)
            i = 0
        } else {
            reader.skipBytes(byteIndex.toLong())
            i = reader.readByte().toInt() and 0xFF and mask
        }
        while (i == 0) {
            if (++byteIndex == bitTableBytes) {
                return -1
            }
            i = reader.readByte().toInt() and 0xFF
        }
        return Int.numberOfTrailingZeros(i) + (byteIndex shl 3)
    }

    /**
     * Returns the index of the previous bit set preceding the given bit zero-based index. <br></br>
     * For example with bits 100011: there is no previous bit set before index=0. the previous bit set
     * before index=1 is at index=0; the previous bit set before index=5 is at index=1; the previous
     * bit set before index=64 is at index=5;
     *
     * @param bitIndex The bit zero-based index. It must be greater than or equal to 0, and less than
     * or equal to `number of bit-table bytes * Byte.SIZE`.
     * @param reader The [FST.BytesReader] to read. It must be positioned at the beginning of
     * the bit-table.
     * @return The zero-based index of the previous bit set before the provided `bitIndex`; or
     * -1 if none.
     */
    @Throws(IOException::class)
    fun previousBitSet(bitIndex: Int, reader: BytesReader): Int {
        require(bitIndex >= 0) { "bitIndex=$bitIndex" }
        var byteIndex = bitIndex shr 3
        reader.skipBytes(byteIndex.toLong())
        val mask = (1 shl (bitIndex and (Byte.SIZE_BITS - 1))) - 1
        var i = reader.readByte().toInt() and 0xFF and mask
        while (i == 0) {
            if (byteIndex-- == 0) {
                return -1
            }
            reader.skipBytes(-2) // FST.BytesReader implementations support negative skip.
            i = reader.readByte().toInt() and 0xFF
        }
        return (Int.SIZE_BITS - 1) - Int.numberOfLeadingZeros(i) + (byteIndex shl 3)
    }

    @Throws(IOException::class)
    private fun readByte(reader: BytesReader): Long {
        return (reader.readByte().toInt() and 0xFF).toLong()
    }

    @Throws(IOException::class)
    private fun readUpTo8Bytes(numBytes: Int, reader: BytesReader): Long {
        var numBytes = numBytes
        require(numBytes > 0 && numBytes <= 8) { "numBytes=$numBytes" }
        var l = readByte(reader)
        var shift = 0
        while (--numBytes != 0) {
            l = l or (readByte(reader) shl (8.let { shift += it; shift }))
        }
        return l
    }

    @Throws(IOException::class)
    private fun bitCount8Bytes(reader: BytesReader): Int {
        return Long.bitCount(reader.readLong())
    }
}
