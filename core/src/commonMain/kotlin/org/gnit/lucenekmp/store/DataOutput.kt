package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.GroupVIntUtil


/**
 * Abstract base class for performing write operations of Lucene's low-level data types.
 *
 *
 * `DataOutput` may only be used from one thread, because it is not thread safe (it keeps
 * internal state like file position).
 */
abstract class DataOutput {
    private var groupVIntBytes = ByteArray(GroupVIntUtil.MAX_LENGTH_PER_GROUP)

    /**
     * Writes a single byte.
     *
     *
     * The most primitive data type is an eight-bit byte. Files are accessed as sequences of bytes.
     * All other data types are defined as sequences of bytes, so file formats are byte-order
     * independent.
     *
     * @see IndexInput.readByte
     */
    @Throws(IOException::class)
    abstract fun writeByte(b: Byte)

    /**
     * Writes an array of bytes.
     *
     * @param b the bytes to write
     * @param length the number of bytes to write
     * @see DataInput.readBytes
     */
    @Throws(IOException::class)
    open fun writeBytes(b: ByteArray, length: Int) {
        writeBytes(b, 0, length)
    }

    /**
     * Writes an array of bytes.
     *
     * @param b the bytes to write
     * @param offset the offset in the byte array
     * @param length the number of bytes to write
     * @see DataInput.readBytes
     */
    @Throws(IOException::class)
    abstract fun writeBytes(b: ByteArray, offset: Int, length: Int)

    /**
     * Writes an int as four bytes (LE byte order).
     *
     * @see DataInput.readInt
     * @see BitUtil.VH_LE_INT
     */
    @Throws(IOException::class)
    open fun writeInt(i: Int) {
        writeByte(i.toByte())
        writeByte((i shr 8).toByte())
        writeByte((i shr 16).toByte())
        writeByte((i shr 24).toByte())
    }

    /**
     * Writes a short as two bytes (LE byte order).
     *
     * @see DataInput.readShort
     * @see BitUtil.VH_LE_SHORT
     */
    @Throws(IOException::class)
    open fun writeShort(i: Short) {
        writeByte(i.toByte())
        writeByte((i.toInt() shr 8).toByte())
    }

    /**
     * Writes an int in a variable-length format. Writes between one and five bytes. Smaller values
     * take fewer bytes. Negative numbers are supported, but should be avoided.
     *
     *
     * VByte is a variable-length format for positive integers is defined where the high-order bit
     * of each byte indicates whether more bytes remain to be read. The low-order seven bits are
     * appended as increasingly more significant bits in the resulting integer value. Thus values from
     * zero to 127 may be stored in a single byte, values from 128 to 16,383 may be stored in two
     * bytes, and so on.
     *
     *
     * VByte Encoding Example
     *
     * <table class="padding2" style="border-spacing: 0px; border-collapse: separate; border: 0">
     * <caption>variable length encoding examples</caption>
     * <tr style="vertical-align: top">
     * <th style="text-align:left">Value</th>
     * <th style="text-align:left">Byte 1</th>
     * <th style="text-align:left">Byte 2</th>
     * <th style="text-align:left">Byte 3</th>
    </tr> *
     * <tr style="vertical-align: bottom">
     * <td>0</td>
     * <td>`00000000`</td>
     * <td></td>
     * <td></td>
    </tr> *
     * <tr style="vertical-align: bottom">
     * <td>1</td>
     * <td>`00000001`</td>
     * <td></td>
     * <td></td>
    </tr> *
     * <tr style="vertical-align: bottom">
     * <td>2</td>
     * <td>`00000010`</td>
     * <td></td>
     * <td></td>
    </tr> *
     * <tr>
     * <td style="vertical-align: top">...</td>
     * <td></td>
     * <td></td>
     * <td></td>
    </tr> *
     * <tr style="vertical-align: bottom">
     * <td>127</td>
     * <td>`01111111`</td>
     * <td></td>
     * <td></td>
    </tr> *
     * <tr style="vertical-align: bottom">
     * <td>128</td>
     * <td>`10000000`</td>
     * <td>`00000001`</td>
     * <td></td>
    </tr> *
     * <tr style="vertical-align: bottom">
     * <td>129</td>
     * <td>`10000001`</td>
     * <td>`00000001`</td>
     * <td></td>
    </tr> *
     * <tr style="vertical-align: bottom">
     * <td>130</td>
     * <td>`10000010`</td>
     * <td>`00000001`</td>
     * <td></td>
    </tr> *
     * <tr>
     * <td style="vertical-align: top">...</td>
     * <td></td>
     * <td></td>
     * <td></td>
    </tr> *
     * <tr style="vertical-align: bottom">
     * <td>16,383</td>
     * <td>`11111111`</td>
     * <td>`01111111`</td>
     * <td></td>
    </tr> *
     * <tr style="vertical-align: bottom">
     * <td>16,384</td>
     * <td>`10000000`</td>
     * <td>`10000000`</td>
     * <td>`00000001`</td>
    </tr> *
     * <tr style="vertical-align: bottom">
     * <td>16,385</td>
     * <td>`10000001`</td>
     * <td>`10000000`</td>
     * <td>`00000001`</td>
    </tr> *
     * <tr>
     * <td style="vertical-align: top">...</td>
     * <td></td>
     * <td></td>
     * <td></td>
    </tr> *
    </table> *
     *
     *
     * This provides compression while still being efficient to decode.
     *
     * @param i Smaller values take fewer bytes. Negative numbers are supported, but should be
     * avoided.
     * @throws IOException If there is an I/O error writing to the underlying medium.
     * @see DataInput.readVInt
     */
    @Throws(IOException::class)
    fun writeVInt(i: Int) {
        var i = i
        while ((i and 0x7F.inv()) != 0) {
            writeByte(((i and 0x7F) or 0x80).toByte())
            i = i ushr 7
        }
        writeByte(i.toByte())
    }

    /**
     * Write a [zig-zag][BitUtil.zigZagEncode]-encoded [ variable-length][.writeVInt] integer. This is typically useful to write small signed ints and is equivalent
     * to calling `writeVInt(BitUtil.zigZagEncode(i))`.
     *
     * @see DataInput.readZInt
     */
    @Throws(IOException::class)
    fun writeZInt(i: Int) {
        writeVInt(BitUtil.zigZagEncode(i))
    }

    /**
     * Writes a long as eight bytes (LE byte order).
     *
     * @see DataInput.readLong
     * @see BitUtil.VH_LE_LONG
     */
    @Throws(IOException::class)
    open fun writeLong(i: Long) {
        writeInt(i.toInt())
        writeInt((i shr 32).toInt())
    }

    /**
     * Writes an long in a variable-length format. Writes between one and nine bytes. Smaller values
     * take fewer bytes. Negative numbers are not supported.
     *
     *
     * The format is described further in [DataOutput.writeVInt].
     *
     * @see DataInput.readVLong
     */
    @Throws(IOException::class)
    fun writeVLong(i: Long) {
        require(i >= 0) { "cannot write negative vLong (got: " + i + ")" }
        writeSignedVLong(i)
    }

    // write a potentially negative vLong
    @Throws(IOException::class)
    private fun writeSignedVLong(i: Long) {
        var i = i
        while ((i and 0x7FL.inv()) != 0L) {
            writeByte(((i and 0x7FL) or 0x80L).toByte())
            i = i ushr 7
        }
        writeByte(i.toByte())
    }

    /**
     * Write a [zig-zag][BitUtil.zigZagEncode]-encoded [ variable-length][.writeVLong] long. Writes between one and ten bytes. This is typically useful to write
     * small signed ints.
     *
     * @see DataInput.readZLong
     */
    @Throws(IOException::class)
    fun writeZLong(i: Long) {
        writeSignedVLong(BitUtil.zigZagEncode(i))
    }

    /**
     * Writes a string.
     *
     *
     * Writes strings as UTF-8 encoded bytes. First the length, in bytes, is written as a [ ][.writeVInt], followed by the bytes.
     *
     * @see DataInput.readString
     */
    @Throws(IOException::class)
    open fun writeString(s: String) {
        val utf8Result: BytesRef = BytesRef(s)
        writeVInt(utf8Result.length)
        writeBytes(utf8Result.bytes, utf8Result.offset, utf8Result.length)
    }

    private var copyBuffer: ByteArray = ByteArray(COPY_BUFFER_SIZE)

    /** Copy numBytes bytes from input to ourself.  */
    @Throws(IOException::class)
    open fun copyBytes(input: DataInput, numBytes: Long) {
        require(numBytes >= 0) { "numBytes=" + numBytes }
        var left = numBytes
        /*if (copyBuffer == null) copyBuffer = ByteArray(COPY_BUFFER_SIZE)*/
        while (left > 0) {
            val toCopy: Int
            if (left > COPY_BUFFER_SIZE) toCopy = COPY_BUFFER_SIZE
            else toCopy = left.toInt()
            input.readBytes(copyBuffer, 0, toCopy)
            writeBytes(copyBuffer, 0, toCopy)
            left -= toCopy.toLong()
        }
    }

    /**
     * Writes a String map.
     *
     *
     * First the size is written as an [vInt][.writeVInt], followed by each key-value
     * pair written as two consecutive [String][.writeString]s.
     *
     * @param map Input map.
     * @throws NullPointerException if `map` is null.
     */
    @Throws(IOException::class)
    open fun writeMapOfStrings(map: /*Mutable*/Map<String, String>) {
        writeVInt(map.size)
        for (entry in map.entries) {
            writeString(entry.key)
            writeString(entry.value)
        }
    }

    /**
     * Writes a String set.
     *
     *
     * First the size is written as an [vInt][.writeVInt], followed by each value written
     * as a [String][.writeString].
     *
     * @param set Input set.
     * @throws NullPointerException if `set` is null.
     */
    @Throws(IOException::class)
    open fun writeSetOfStrings(set: MutableSet<String>) {
        writeVInt(set.size)
        for (value in set) {
            writeString(value)
        }
    }

    /**
     * Encode integers using group-varint. It uses [VInt][DataOutput.writeVInt] to encode tail
     * values that are not enough for a group. we need a long[] because this is what postings are
     * using, all longs are actually required to be integers.
     *
     * @param values the values to write
     * @param limit the number of values to write.
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun writeGroupVInts(values: LongArray, limit: Int) {
        /*groupVIntBytes = ByteArray(GroupVIntUtil.MAX_LENGTH_PER_GROUP)*/
        GroupVIntUtil.writeGroupVInts(this, groupVIntBytes, values, limit)
    }

    /**
     * Encode integers using group-varint. It uses [VInt][DataOutput.writeVInt] to encode tail
     * values that are not enough for a group.
     *
     * @param values the values to write
     * @param limit the number of values to write.
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun writeGroupVInts(values: IntArray, limit: Int) {
        /*groupVIntBytes = ByteArray(GroupVIntUtil.MAX_LENGTH_PER_GROUP)*/
        GroupVIntUtil.writeGroupVInts(this, groupVIntBytes, values, limit)
    }

    companion object {
        private const val COPY_BUFFER_SIZE = 16384
    }
}
