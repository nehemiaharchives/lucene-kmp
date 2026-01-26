package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.GroupVIntUtil

/**
 * Abstract base class for performing read operations of Lucene's low-level data types.
 *
 *
 * `DataInput` may only be used from one thread, because it is not thread safe (it keeps
 * internal state like file position). To allow multithreaded use, every `DataInput` instance
 * must be cloned before used in another thread. Subclasses must therefore implement [ ][.clone], returning a new `DataInput` which operates on the same underlying resource, but
 * positioned independently.
 */
    abstract class DataInput : Cloneable<DataInput> {
    /**
     * Reads and returns a single byte.
     *
     * @see DataOutput.writeByte
     */
    @Throws(IOException::class)
    abstract fun readByte(): Byte

    /**
     * Reads a specified number of bytes into an array at the specified offset.
     *
     * @param b the array to read bytes into
     * @param offset the offset in the array to start storing bytes
     * @param len the number of bytes to read
     * @see DataOutput.writeBytes
     */
    @Throws(IOException::class)
    abstract fun readBytes(b: ByteArray, offset: Int, len: Int)

    /**
     * Reads a specified number of bytes into an array at the specified offset with control over
     * whether the read should be buffered (callers who have their own buffer should pass in "false"
     * for useBuffer). Currently only [BufferedIndexInput] respects this parameter.
     *
     * @param b the array to read bytes into
     * @param offset the offset in the array to start storing bytes
     * @param len the number of bytes to read
     * @param useBuffer set to false if the caller will handle buffering.
     * @see DataOutput.writeBytes
     */
    @Throws(IOException::class)
    open fun readBytes(b: ByteArray, offset: Int, len: Int, useBuffer: Boolean) {
        // Default to ignoring useBuffer entirely
        readBytes(b, offset, len)
    }

    /**
     * Reads two bytes and returns a short (LE byte order).
     *
     * @see DataOutput.writeShort
     * @see BitUtil.VH_LE_SHORT
     */
    @Throws(IOException::class)
    open fun readShort(): Short {
        val b1 = readByte()
        val b2 = readByte()
        return (((b2.toInt() and 0xFF) shl 8) or (b1.toInt() and 0xFF)).toShort()
    }

    /**
     * Reads four bytes and returns an int (LE byte order).
     *
     * @see DataOutput.writeInt
     * @see BitUtil.VH_LE_INT
     */
    @Throws(IOException::class)
    open fun readInt(): Int {
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()
        val b4 = readByte()
        return ((b4.toInt() and 0xFF) shl 24) or ((b3.toInt() and 0xFF) shl 16) or ((b2.toInt() and 0xFF) shl 8) or (b1.toInt() and 0xFF)
    }

    /**
     * Override if you have an efficient implementation. In general this is when the input supports
     * random access.
     *
     * @lucene.experimental
     */
    @Throws(IOException::class)
    open fun readGroupVInt(dst: IntArray, offset: Int) {
        GroupVIntUtil.readGroupVInt(this, dst, offset)
    }

    /**
     * Reads an int stored in variable-length format. Reads between one and five bytes. Smaller values
     * take fewer bytes. Negative numbers are supported, but should be avoided.
     *
     *
     * The format is described further in [DataOutput.writeVInt].
     *
     * @see DataOutput.writeVInt
     */
    @Throws(IOException::class)
    open fun readVInt(): Int {
        var b = readByte()
        var i = b.toInt() and 0x7F
        var shift = 7
        while ((b.toInt() and 0x80) != 0) {
            b = readByte()
            i = i or ((b.toInt() and 0x7F) shl shift)
            shift += 7
        }
        return i
    }

    /**
     * Read a [zig-zag][BitUtil.zigZagDecode]-encoded [variable-length][.readVInt]
     * integer.
     *
     * @see DataOutput.writeZInt
     */
    @Throws(IOException::class)
    open fun readZInt(): Int {
        return BitUtil.zigZagDecode(readVInt())
    }

    /**
     * Reads eight bytes and returns a long (LE byte order).
     *
     * @see DataOutput.writeLong
     * @see BitUtil.VH_LE_LONG
     */
    @Throws(IOException::class)
    open fun readLong(): Long {
        return (readInt().toLong() and 0xFFFFFFFFL) or ((readInt().toLong()) shl 32)
    }

    /**
     * Read a specified number of longs.
     *
     * @lucene.experimental
     */
    @Throws(IOException::class)
    open fun readLongs(dst: LongArray, offset: Int, length: Int) {
        Objects.checkFromIndexSize(offset, length, dst.size)
        for (i in 0..<length) {
            dst[offset + i] = readLong()
        }
    }

    /**
     * Reads a specified number of ints into an array at the specified offset.
     *
     * @param dst the array to read bytes into
     * @param offset the offset in the array to start storing ints
     * @param length the number of ints to read
     */
    @Throws(IOException::class)
    open fun readInts(dst: IntArray, offset: Int, length: Int) {
        Objects.checkFromIndexSize(offset, length, dst.size)
        for (i in 0..<length) {
            dst[offset + i] = readInt()
        }
    }

    /**
     * Reads a specified number of floats into an array at the specified offset.
     *
     * @param floats the array to read bytes into
     * @param offset the offset in the array to start storing floats
     * @param len the number of floats to read
     */
    @Throws(IOException::class)
    open fun readFloats(floats: FloatArray, offset: Int, len: Int) {
        Objects.checkFromIndexSize(offset, len, floats.size)
        for (i in 0..<len) {
            floats[offset + i] = Float.fromBits(readInt()) /*java.lang.Float.intBitsToFloat(readInt())*/
        }
    }

    /**
     * Reads a long stored in variable-length format. Reads between one and nine bytes. Smaller values
     * take fewer bytes. Negative numbers are not supported.
     *
     *
     * The format is described further in [DataOutput.writeVInt].
     *
     * @see DataOutput.writeVLong
     */
    @Throws(IOException::class)
    open fun readVLong(): Long {
        var b = readByte()
        var i = (b.toInt() and 0x7F).toLong()
        var shift = 7
        while ((b.toInt() and 0x80) != 0) {
            b = readByte()
            i = i or ((b.toLong() and 0x7FL) shl shift)
            shift += 7
        }
        return i
    }

    /**
     * Read a [zig-zag][BitUtil.zigZagDecode]-encoded [variable-length][.readVLong]
     * integer. Reads between one and ten bytes.
     *
     * @see DataOutput.writeZLong
     */
    @Throws(IOException::class)
    open fun readZLong(): Long {
        return BitUtil.zigZagDecode(readVLong())
    }

    /**
     * Reads a string.
     *
     * @see DataOutput.writeString
     */
    @Throws(IOException::class)
    open fun readString(): String {
        val length = readVInt()
        val bytes = ByteArray(length)
        readBytes(bytes, 0, length)
        return bytes.decodeToString(0, length) /*String(bytes, 0, length, java.nio.charset.StandardCharsets.UTF_8)*/
    }

    /**
     * Returns a clone of this stream.
     *
     *
     * Clones of a stream access the same data, and are positioned at the same point as the stream
     * they were cloned from.
     *
     *
     * Expert: Subclasses must ensure that clones may be positioned at different points in the
     * input from each other and from the stream they were cloned from.
     */
    override fun clone(): DataInput {
        throw UnsupportedOperationException("Subclasses of DataInput must implement clone()")
    }

    /**
     * Reads a Map&lt;String,String&gt; previously written with [ ][DataOutput.writeMapOfStrings].
     *
     * @return An immutable map containing the written contents.
     */
    @Throws(IOException::class)
    open fun readMapOfStrings(): MutableMap<String, String> {
        val count = readVInt()
        if (count == 0) {
            return mutableMapOf<String, String>()
        } else if (count == 1) {
            return mutableMapOf(readString() to readString())
        } else {
            val map: MutableMap<String, String> =
                if (count > 10) hashMapOf<String, String>() else mutableMapOf<String, String>()
            for (i in 0..<count) {
                val key = readString()
                val `val` = readString()
                map.put(key, `val`)
            }
            return map
        }
    }

    /**
     * Reads a Set&lt;String&gt; previously written with [DataOutput.writeSetOfStrings].
     *
     * @return An immutable set containing the written contents.
     */
    @Throws(IOException::class)
    open fun readSetOfStrings(): MutableSet<String> {
        val count = readVInt()
        if (count == 0) {
            return mutableSetOf<String>()
        } else if (count == 1) {
            return mutableSetOf<String>(readString())
        } else {
            val set: MutableSet<String> =
                if (count > 10) hashSetOf<String>() else mutableSetOf<String>()
            for (i in 0..<count) {
                set.add(readString())
            }
            return set
        }
    }

    /**
     * Skip over `numBytes` bytes. This method may skip bytes in whatever way is most
     * optimal, and may not have the same behavior as reading the skipped bytes. In general, negative
     * `numBytes` are not supported.
     */
    @Throws(IOException::class)
    abstract fun skipBytes(numBytes: Long)
}
