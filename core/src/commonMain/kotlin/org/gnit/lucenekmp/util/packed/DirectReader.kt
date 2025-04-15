package org.gnit.lucenekmp.util.packed

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.jdkport.toUnsignedLong
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.util.LongValues
import kotlin.experimental.and

/**
 * Retrieves an instance previously written by [DirectWriter]
 *
 *
 * Example usage:
 *
 * <pre class="prettyprint">
 * int bitsPerValue = DirectWriter.bitsRequired(100);
 * IndexInput in = dir.openInput("packed", IOContext.DEFAULT);
 * LongValues values = DirectReader.getInstance(in.randomAccessSlice(start, end), bitsPerValue);
 * for (int i = 0; i &lt; numValues; i++) {
 * long value = values.get(i);
 * }
</pre> *
 *
 * @see DirectWriter
 */
object DirectReader {
    const val MERGE_BUFFER_SHIFT: Int = 7
    private const val MERGE_BUFFER_SIZE = 1 shl MERGE_BUFFER_SHIFT
    private const val MERGE_BUFFER_MASK = MERGE_BUFFER_SIZE - 1

    /**
     * Retrieves an instance from the specified slice written decoding `bitsPerValue` for each
     * value
     */
    fun getInstance(slice: RandomAccessInput, bitsPerValue: Int): LongValues {
        return getInstance(slice, bitsPerValue, 0)
    }

    /**
     * Retrieves an instance from the specified `offset` of the given slice decoding `bitsPerValue` for each value
     */
    fun getInstance(slice: RandomAccessInput, bitsPerValue: Int, offset: Long): LongValues {
        when (bitsPerValue) {
            1 -> return DirectPackedReader1(slice, offset)
            2 -> return DirectPackedReader2(slice, offset)
            4 -> return DirectPackedReader4(slice, offset)
            8 -> return DirectPackedReader8(slice, offset)
            12 -> return DirectPackedReader12(slice, offset)
            16 -> return DirectPackedReader16(slice, offset)
            20 -> return DirectPackedReader20(slice, offset)
            24 -> return DirectPackedReader24(slice, offset)
            28 -> return DirectPackedReader28(slice, offset)
            32 -> return DirectPackedReader32(slice, offset)
            40 -> return DirectPackedReader40(slice, offset)
            48 -> return DirectPackedReader48(slice, offset)
            56 -> return DirectPackedReader56(slice, offset)
            64 -> return DirectPackedReader64(slice, offset)
            else -> throw IllegalArgumentException("unsupported bitsPerValue: $bitsPerValue")
        }
    }

    /**
     * Retrieves an instance that is specialized for merges and is typically faster at sequential
     * access but slower at random access.
     */
    fun getMergeInstance(
        slice: RandomAccessInput, bitsPerValue: Int, numValues: Long
    ): LongValues? {
        return getMergeInstance(slice, bitsPerValue, 0L, numValues)
    }

    /**
     * Retrieves an instance that is specialized for merges and is typically faster at sequential
     * access.
     */
    fun getMergeInstance(
        slice: RandomAccessInput, bitsPerValue: Int, baseOffset: Long, numValues: Long
    ): LongValues? {
        return object : LongValues() {
            private val buffer = LongArray(MERGE_BUFFER_SIZE)
            private var blockIndex: Long = -1

            override fun get(index: Long): Long {
                require(index < numValues)
                val blockIndex = index ushr MERGE_BUFFER_SHIFT
                if (this.blockIndex != blockIndex) {
                    try {
                        fillBuffer(blockIndex shl MERGE_BUFFER_SHIFT)
                    } catch (e: IOException) {
                        throw UncheckedIOException(e)
                    }
                    this.blockIndex = blockIndex
                }
                return buffer[(index and MERGE_BUFFER_MASK.toLong()).toInt()]
            }

            @Throws(IOException::class)
            fun fillBuffer(index: Long) {
                // NOTE: we're not allowed to read more than 3 bytes past the last value
                if (index >= numValues - MERGE_BUFFER_SIZE) {
                    // 128 values left or less
                    val slowInstance: LongValues = getInstance(slice, bitsPerValue, baseOffset)
                    val numValuesLastBlock: Int = Math.toIntExact(numValues - index)
                    for (i in 0..<numValuesLastBlock) {
                        buffer[i] = slowInstance.get(index + i)
                    }
                } else if ((bitsPerValue and 0x07) == 0) {
                    // bitsPerValue is a multiple of 8: 8, 16, 24, 32, 30, 48, 56, 64
                    val bytesPerValue: Int = bitsPerValue / Byte.SIZE_BITS
                    val mask = if (bitsPerValue == 64) 0L.inv() else (1L shl bitsPerValue) - 1
                    var offset = baseOffset + (index * bitsPerValue) / 8
                    for (i in 0..<MERGE_BUFFER_SIZE) {
                        if (bitsPerValue > Int.SIZE_BITS) {
                            buffer[i] = slice.readLong(offset) and mask
                        } else if (bitsPerValue > Short.SIZE_BITS) {
                            buffer[i] = slice.readInt(offset).toLong() and mask
                        } else if (bitsPerValue > Byte.SIZE_BITS) {
                            buffer[i] = Short.toUnsignedLong(slice.readShort(offset))
                        } else {
                            buffer[i] = Byte.toUnsignedLong(slice.readByte(offset))
                        }
                        offset += bytesPerValue.toLong()
                    }
                } else if (bitsPerValue < 8) {
                    // bitsPerValue is 1, 2 or 4
                    val valuesPerLong: Int = Long.SIZE_BITS / bitsPerValue
                    val mask = (1L shl bitsPerValue) - 1
                    var offset = baseOffset + (index * bitsPerValue) / 8
                    var i = 0
                    for (l in 0..<2 * bitsPerValue) {
                        val bits: Long = slice.readLong(offset)
                        for (j in 0..<valuesPerLong) {
                            buffer[i++] = (bits ushr (j * bitsPerValue)) and mask
                        }
                        offset += Long.SIZE_BYTES.toLong()
                    }
                } else {
                    // bitsPerValue is 12, 20 or 28
                    // Read values 2 by 2
                    val numBytesFor2Values: Int = bitsPerValue * 2 / Byte.SIZE_BITS
                    val mask = (1L shl bitsPerValue) - 1
                    var offset = baseOffset + (index * bitsPerValue) / 8
                    var i = 0
                    while (i < MERGE_BUFFER_SIZE) {
                        val l: Long = if (numBytesFor2Values > Int.SIZE_BYTES) {
                            slice.readLong(offset)
                        } else {
                            slice.readInt(offset).toLong()
                        }
                        buffer[i] = l and mask
                        buffer[i + 1] = (l ushr bitsPerValue) and mask
                        offset += numBytesFor2Values.toLong()
                        i += 2
                    }
                }
            }
        }
    }

    internal class DirectPackedReader1(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                val shift = (index and 7L).toInt()
                return ((`in`.readByte(offset + (index ushr 3)).toInt() ushr shift) and 0x1).toLong()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader2(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                val shift = ((index and 3L).toInt()) shl 1
                return ((`in`.readByte(offset + (index ushr 2)).toInt() ushr shift) and 0x3).toLong()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader4(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                val shift = (index and 1L).toInt() shl 2
                return ((`in`.readByte(offset + (index ushr 1)).toInt() ushr shift) and 0xF).toLong()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader8(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                return (`in`.readByte(offset + index) and 0xFF.toByte()).toLong()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader12(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                val offset = (index * 12) ushr 3
                val shift = (index and 1L).toInt() shl 2
                return ((`in`.readShort(this.offset + offset).toInt() ushr shift) and 0xFFF).toLong()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader16(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                return ((`in`.readShort(offset + (index shl 1)).toInt() and 0xFFFF)).toLong()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader20(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                val offset = (index * 20) ushr 3
                val shift = (index and 1L).toInt() shl 2
                return ((`in`.readInt(this.offset + offset).toInt() ushr shift) and 0xFFFFF).toLong()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader24(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                return (`in`.readInt(this.offset + index * 3).toInt() and 0xFFFFFF).toLong()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader28(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                val offset = (index * 28) ushr 3
                val shift = (index and 1L).toInt() shl 2
                return ((`in`.readInt(this.offset + offset).toInt() ushr shift) and 0xFFFFFFF).toLong()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader32(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                return (`in`.readInt(this.offset + (index shl 2)).toInt() and 0xFFFFFFFFL.toInt()).toLong()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader40(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                return `in`.readLong(this.offset + index * 5) and 0xFFFFFFFFFFL
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader48(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                return `in`.readLong(this.offset + index * 6) and 0xFFFFFFFFFFFFL
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader56(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                return `in`.readLong(this.offset + index * 7) and 0xFFFFFFFFFFFFFFL
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    internal class DirectPackedReader64(val `in`: RandomAccessInput, val offset: Long) : LongValues() {

        override fun get(index: Long): Long {
            try {
                return `in`.readLong(offset + (index shl 3))
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }
}
