package org.gnit.lucenekmp.util

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import kotlin.experimental.and


/**
 * This class contains utility methods and constants for group varint
 *
 * @lucene.internal
 */
object GroupVIntUtil {
    // the maximum length of a single group-varint is 4 integers + 1 byte flag.
    const val MAX_LENGTH_PER_GROUP: Int = 17

    // we use long array instead of int array to make negative integer to be read as positive long.
    private val LONG_MASKS = longArrayOf(0xFFL, 0xFFFFL, 0xFFFFFFL, 0xFFFFFFFFL)
    private val INT_MASKS = intArrayOf(0xFF, 0xFFFF, 0xFFFFFF, 0.inv())

    /**
     * Read all the group varints, including the tail vints. we need a long[] because this is what
     * postings are using, all longs are actually required to be integers.
     *
     * @param dst the array to read ints into.
     * @param limit the number of int values to read.
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun readGroupVInts(`in`: DataInput, dst: LongArray, limit: Int) {
        var i: Int
        i = 0
        while (i <= limit - 4) {
            readGroupVInt(`in`, dst, i)
            i += 4
        }
        while (i < limit) {
            dst[i] = (`in`.readVInt() and 0xFFFFFFFFL.toInt()).toLong()
            ++i
        }
    }

    /**
     * Read all the group varints, including the tail vints.
     *
     * @param dst the array to read ints into.
     * @param limit the number of int values to read.
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun readGroupVInts(`in`: DataInput, dst: IntArray, limit: Int) {
        var i: Int
        i = 0
        while (i <= limit - 4) {
            `in`.readGroupVInt(dst, i)
            i += 4
        }
        while (i < limit) {
            dst[i] = `in`.readVInt()
            ++i
        }
    }

    /**
     * Default implementation of read single group, for optimal performance, you should use [ ][GroupVIntUtil.readGroupVInts] instead.
     *
     * @param in the input to use to read data.
     * @param dst the array to read ints into.
     * @param offset the offset in the array to start storing ints.
     */
    @Throws(IOException::class)
    fun readGroupVInt(`in`: DataInput, dst: LongArray, offset: Int) {
        val flag: Byte = `in`.readByte() and 0xFF.toByte()

        val n1Minus1 = flag.toInt() shr 6
        val n2Minus1 = (flag.toInt() shr 4) and 0x03
        val n3Minus1 = (flag.toInt() shr 2) and 0x03
        val n4Minus1 = (flag and 0x03).toInt()

        dst[offset] = readIntInGroup(`in`, n1Minus1).toLong() and 0xFFFFFFFFL
        dst[offset + 1] = readIntInGroup(`in`, n2Minus1).toLong() and 0xFFFFFFFFL
        dst[offset + 2] = readIntInGroup(`in`, n3Minus1).toLong() and 0xFFFFFFFFL
        dst[offset + 3] = readIntInGroup(`in`, n4Minus1).toLong() and 0xFFFFFFFFL
    }

    /**
     * Default implementation of read single group, for optimal performance, you should use [ ][GroupVIntUtil.readGroupVInts] instead.
     *
     * @param in the input to use to read data.
     * @param dst the array to read ints into.
     * @param offset the offset in the array to start storing ints.
     */
    @Throws(IOException::class)
    fun readGroupVInt(`in`: DataInput, dst: IntArray, offset: Int) {
        val flag: Int = (`in`.readByte() and 0xFF.toByte()).toInt()

        val n1Minus1 = flag shr 6
        val n2Minus1 = (flag shr 4) and 0x03
        val n3Minus1 = (flag shr 2) and 0x03
        val n4Minus1 = flag and 0x03

        dst[offset] = readIntInGroup(`in`, n1Minus1)
        dst[offset + 1] = readIntInGroup(`in`, n2Minus1)
        dst[offset + 2] = readIntInGroup(`in`, n3Minus1)
        dst[offset + 3] = readIntInGroup(`in`, n4Minus1)
    }

    @Throws(IOException::class)
    private fun readIntInGroup(`in`: DataInput, numBytesMinus1: Int): Int {
        when (numBytesMinus1) {
            0 -> return (`in`.readByte() and 0xFF.toByte()).toInt()
            1 -> return (`in`.readShort().toByte() and 0xFFFF.toByte()).toInt()
            2 -> return (`in`.readShort().toByte() and 0xFFFF.toByte()).toInt() or ((`in`.readByte() and 0xFF.toByte()).toInt() shl 16)
            else -> return `in`.readInt()
        }
    }

    /**
     * Faster implementation of read single group, It read values from the buffer that would not cross
     * boundaries.
     *
     * @param in the input to use to read data.
     * @param remaining the number of remaining bytes allowed to read for current block/segment.
     * @param reader the supplier of read int.
     * @param pos the start pos to read from the reader.
     * @param dst the array to read ints into.
     * @param offset the offset in the array to start storing ints.
     * @return the number of bytes read excluding the flag. this indicates the number of positions
     * should to be increased for caller, it is 0 or positive number and less than [     ][.MAX_LENGTH_PER_GROUP]
     */
    @Throws(IOException::class)
    fun readGroupVInt(
        `in`: DataInput, remaining: Long, reader: IntReader, pos: Long, dst: LongArray, offset: Int
    ): Int {
        var pos = pos
        if (remaining < MAX_LENGTH_PER_GROUP) {
            readGroupVInt(`in`, dst, offset)
            return 0
        }
        val flag: Int = (`in`.readByte() and 0xFF.toByte()).toInt()
        val posStart = ++pos // exclude the flag bytes, the position has updated via readByte().
        val n1Minus1 = flag shr 6
        val n2Minus1 = (flag shr 4) and 0x03
        val n3Minus1 = (flag shr 2) and 0x03
        val n4Minus1 = flag and 0x03

        // This code path has fewer conditionals and tends to be significantly faster in benchmarks
        dst[offset] = reader.read(pos).toLong() and LONG_MASKS[n1Minus1]
        pos += (1 + n1Minus1).toLong()
        dst[offset + 1] = reader.read(pos).toLong() and LONG_MASKS[n2Minus1]
        pos += (1 + n2Minus1).toLong()
        dst[offset + 2] = reader.read(pos).toLong() and LONG_MASKS[n3Minus1]
        pos += (1 + n3Minus1).toLong()
        dst[offset + 3] = reader.read(pos).toLong() and LONG_MASKS[n4Minus1]
        pos += (1 + n4Minus1).toLong()
        return (pos - posStart).toInt()
    }

    /**
     * Faster implementation of read single group, It read values from the buffer that would not cross
     * boundaries.
     *
     * @param in the input to use to read data.
     * @param remaining the number of remaining bytes allowed to read for current block/segment.
     * @param reader the supplier of read int.
     * @param pos the start pos to read from the reader.
     * @param dst the array to read ints into.
     * @param offset the offset in the array to start storing ints.
     * @return the number of bytes read excluding the flag. this indicates the number of positions
     * should to be increased for caller, it is 0 or positive number and less than [     ][.MAX_LENGTH_PER_GROUP]
     */
    @Throws(IOException::class)
    fun readGroupVInt(
        `in`: DataInput, remaining: Long, reader: IntReader, pos: Long, dst: IntArray, offset: Int
    ): Int {
        var pos = pos
        if (remaining < MAX_LENGTH_PER_GROUP) {
            readGroupVInt(`in`, dst, offset)
            return 0
        }
        val flag: Int = (`in`.readByte() and 0xFF.toByte()).toInt()
        val posStart = ++pos // exclude the flag bytes, the position has updated via readByte().
        val n1Minus1 = flag shr 6
        val n2Minus1 = (flag shr 4) and 0x03
        val n3Minus1 = (flag shr 2) and 0x03
        val n4Minus1 = flag and 0x03

        // This code path has fewer conditionals and tends to be significantly faster in benchmarks
        dst[offset] = reader.read(pos) and INT_MASKS[n1Minus1]
        pos += (1 + n1Minus1).toLong()
        dst[offset + 1] = reader.read(pos) and INT_MASKS[n2Minus1]
        pos += (1 + n2Minus1).toLong()
        dst[offset + 2] = reader.read(pos) and INT_MASKS[n3Minus1]
        pos += (1 + n3Minus1).toLong()
        dst[offset + 3] = reader.read(pos) and INT_MASKS[n4Minus1]
        pos += (1 + n4Minus1).toLong()
        return (pos - posStart).toInt()
    }

    private fun numBytes(v: Int): Int {
        // | 1 to return 1 when v = 0
        return Int.SIZE_BYTES - (Int.numberOfLeadingZeros(v or 1) shr 3)
    }

    private fun compare(x: Long, y: Long): Int {
        return if (x < y) -1 else (if (x == y) 0 else 1)
    }

    private fun compareUnsigned(x: Long, y: Long): Int {
        return compare(x + Long.Companion.MIN_VALUE, y + Long.Companion.MIN_VALUE)
    }

    private fun toInt(value: Long): Int {
        if ((compareUnsigned(value, 0xFFFFFFFFL) > 0)) {
            throw ArithmeticException("integer overflow")
        }
        return value.toInt()
    }

    /**
     * The implementation for group-varint encoding, It uses a maximum of [ ][.MAX_LENGTH_PER_GROUP] bytes scratch buffer.
     */
    @Throws(IOException::class)
    fun writeGroupVInts(out: DataOutput, scratch: ByteArray, values: LongArray, limit: Int) {
        var readPos = 0

        // encode each group
        /*while ((limit - readPos) >= 4) {
            var writePos = 0
            val n1Minus1 = numBytes(toInt(values[readPos])) - 1
            val n2Minus1 = numBytes(toInt(values[readPos + 1])) - 1
            val n3Minus1 = numBytes(toInt(values[readPos + 2])) - 1
            val n4Minus1 = numBytes(toInt(values[readPos + 3])) - 1
            val flag = (n1Minus1 shl 6) or (n2Minus1 shl 4) or (n3Minus1 shl 2) or (n4Minus1)
            scratch[writePos++] = flag.toByte()
            BitUtil.VH_LE_INT.set(scratch, writePos, (values[readPos++]).toInt())
            writePos += n1Minus1 + 1
            BitUtil.VH_LE_INT.set(scratch, writePos, (values[readPos++]).toInt())
            writePos += n2Minus1 + 1
            BitUtil.VH_LE_INT.set(scratch, writePos, (values[readPos++]).toInt())
            writePos += n3Minus1 + 1
            BitUtil.VH_LE_INT.set(scratch, writePos, (values[readPos++]).toInt())
            writePos += n4Minus1 + 1

            out.writeBytes(scratch, writePos)
        }*/

        // Process groups of 4 values at a time
        while (limit - readPos >= 4) {
            var writePos = 0
            // For each value, determine number of bytes needed minus one:
            val n1Minus1 = numBytes(values[readPos].toInt()) - 1
            val n2Minus1 = numBytes(values[readPos + 1].toInt()) - 1
            val n3Minus1 = numBytes(values[readPos + 2].toInt()) - 1
            val n4Minus1 = numBytes(values[readPos + 3].toInt()) - 1

            // Pack the counts into a single flag byte
            val flag = (n1Minus1 shl 6) or (n2Minus1 shl 4) or (n3Minus1 shl 2) or n4Minus1
            scratch[writePos++] = flag.toByte()

            // Write each value into the scratch buffer using little-endian order.
            // Note: Here we simply write all 4 bytes of the int, even though only nXMinus1+1 bytes are significant.
            scratch.putIntLE(writePos, values[readPos].toInt())
            writePos += n1Minus1 + 1
            readPos++

            scratch.putIntLE(writePos, values[readPos].toInt())
            writePos += n2Minus1 + 1
            readPos++

            scratch.putIntLE(writePos, values[readPos].toInt())
            writePos += n3Minus1 + 1
            readPos++

            scratch.putIntLE(writePos, values[readPos].toInt())
            writePos += n4Minus1 + 1
            readPos++

            out.writeBytes(scratch, writePos)
        }

        // tail vints
        while (readPos < limit) {
            out.writeVInt(toInt(values[readPos]))
            readPos++
        }
    }

    /**
     * The implementation for group-varint encoding, It uses a maximum of [ ][.MAX_LENGTH_PER_GROUP] bytes scratch buffer.
     */
    @Throws(IOException::class)
    fun writeGroupVInts(out: DataOutput, scratch: ByteArray, values: IntArray, limit: Int) {
        var readPos = 0

        // encode each group
        /*while ((limit - readPos) >= 4) {
            var writePos = 0
            val n1Minus1 = numBytes(values[readPos]) - 1
            val n2Minus1 = numBytes(values[readPos + 1]) - 1
            val n3Minus1 = numBytes(values[readPos + 2]) - 1
            val n4Minus1 = numBytes(values[readPos + 3]) - 1
            val flag = (n1Minus1 shl 6) or (n2Minus1 shl 4) or (n3Minus1 shl 2) or (n4Minus1)
            scratch[writePos++] = flag.toByte()
            BitUtil.VH_LE_INT.set(scratch, writePos, values[readPos++])
            writePos += n1Minus1 + 1
            BitUtil.VH_LE_INT.set(scratch, writePos, values[readPos++])
            writePos += n2Minus1 + 1
            BitUtil.VH_LE_INT.set(scratch, writePos, values[readPos++])
            writePos += n3Minus1 + 1
            BitUtil.VH_LE_INT.set(scratch, writePos, values[readPos++])
            writePos += n4Minus1 + 1

            out.writeBytes(scratch, writePos)
        }*/

        // Process groups of 4 values at a time
        while ((limit - readPos) >= 4) {
            var writePos = 0
            val n1Minus1 = numBytes(values[readPos]) - 1
            val n2Minus1 = numBytes(values[readPos + 1]) - 1
            val n3Minus1 = numBytes(values[readPos + 2]) - 1
            val n4Minus1 = numBytes(values[readPos + 3]) - 1

            // Pack the 2-bit counts for each integer into a single flag byte.
            val flag = (n1Minus1 shl 6) or (n2Minus1 shl 4) or (n3Minus1 shl 2) or n4Minus1
            scratch[writePos++] = flag.toByte()

            // For each value, write only the lower (nXMinus1+1) bytes.
            scratch.putIntLEPartial(writePos, values[readPos], n1Minus1 + 1)
            writePos += n1Minus1 + 1
            readPos++

            scratch.putIntLEPartial(writePos, values[readPos], n2Minus1 + 1)
            writePos += n2Minus1 + 1
            readPos++

            scratch.putIntLEPartial(writePos, values[readPos], n3Minus1 + 1)
            writePos += n3Minus1 + 1
            readPos++

            scratch.putIntLEPartial(writePos, values[readPos], n4Minus1 + 1)
            writePos += n4Minus1 + 1
            readPos++

            out.writeBytes(scratch, writePos)
        }

        // tail vints
        while (readPos < limit) {
            out.writeVInt(values[readPos])
            readPos++
        }
    }

    /**
     * Provides an abstraction for read int values, so that decoding logic can be reused in different
     * DataInput.
     */
    fun interface IntReader {
        fun read(v: Long): Int
    }
}
