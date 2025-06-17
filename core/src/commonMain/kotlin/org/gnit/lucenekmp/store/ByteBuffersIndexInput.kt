package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/** An [IndexInput] backed by a [ByteBuffersDataInput]. */
class ByteBuffersIndexInput(private var input: ByteBuffersDataInput?, resourceDescription: String) :
    IndexInput(resourceDescription), RandomAccessInput {

    override fun close() {
        input = null
    }

    override val filePointer: Long
        get() {
            ensureOpen()
            return input!!.position()
        }

    @Throws(IOException::class)
    override fun seek(pos: Long) {
        ensureOpen()
        input!!.seek(pos)
    }

    override fun length(): Long {
        ensureOpen()
        return input!!.length()
    }

    @Throws(IOException::class)
    override fun slice(sliceDescription: String, offset: Long, length: Long): ByteBuffersIndexInput {
        ensureOpen()
        return ByteBuffersIndexInput(
            input!!.slice(offset, length),
            "(sliced) offset=" + offset + ", length=" + length + " " + toString() + " [slice=" + sliceDescription + "]"
        )
    }

    @Throws(IOException::class)
    override fun readByte(): Byte {
        ensureOpen()
        return input!!.readByte()
    }

    @Throws(IOException::class)
    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
        ensureOpen()
        input!!.readBytes(b, offset, len)
    }

    @Throws(IOException::class)
    override fun readBytes(b: ByteArray, offset: Int, len: Int, useBuffer: Boolean) {
        ensureOpen()
        input!!.readBytes(b, offset, len, useBuffer)
    }

    @Throws(IOException::class)
    override fun readShort(): Short {
        ensureOpen()
        return input!!.readShort()
    }

    @Throws(IOException::class)
    override fun readInt(): Int {
        ensureOpen()
        return input!!.readInt()
    }

    @Throws(IOException::class)
    override fun readVInt(): Int {
        ensureOpen()
        return input!!.readVInt()
    }


    @Throws(IOException::class)
    override fun readLong(): Long {
        ensureOpen()
        return input!!.readLong()
    }

    @Throws(IOException::class)
    override fun readVLong(): Long {
        ensureOpen()
        return input!!.readVLong()
    }



    @Throws(IOException::class)
    override fun skipBytes(numBytes: Long) {
        ensureOpen()
        super.skipBytes(numBytes)
    }

    @Throws(IOException::class)
    override fun readByte(pos: Long): Byte {
        ensureOpen()
        return input!!.readByte(pos)
    }

    @Throws(IOException::class)
    override fun readBytes(pos: Long, bytes: ByteArray, offset: Int, length: Int) {
        ensureOpen()
        input!!.readBytes(pos, bytes, offset, length)
    }

    @Throws(IOException::class)
    override fun readShort(pos: Long): Short {
        ensureOpen()
        return input!!.readShort(pos)
    }

    @Throws(IOException::class)
    override fun readInt(pos: Long): Int {
        ensureOpen()
        return input!!.readInt(pos)
    }

    @Throws(IOException::class)
    override fun readLong(pos: Long): Long {
        ensureOpen()
        return input!!.readLong(pos)
    }

    @Throws(IOException::class)
    override fun readFloats(floats: FloatArray, offset: Int, len: Int) {
        ensureOpen()
        input!!.readFloats(floats, offset, len)
    }

    @Throws(IOException::class)
    override fun readLongs(dst: LongArray, offset: Int, length: Int) {
        ensureOpen()
        input!!.readLongs(dst, offset, length)
    }

    @Throws(IOException::class)
    override fun readGroupVInt(dst: IntArray, offset: Int) {
        ensureOpen()
        input!!.readGroupVInt(dst, offset)
    }

    @Throws(IOException::class)
    override fun prefetch(offset: Long, length: Long) {
        // no-op for heap-based storage
    }

    override fun clone(): ByteBuffersIndexInput {
        ensureOpen()
        val cloned = ByteBuffersIndexInput(input!!.slice(0, input!!.length()), "(clone of) " + toString())
        try {
            cloned.seek(filePointer)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
        return cloned
    }

    private fun ensureOpen() {
        if (input == null) {
            throw AlreadyClosedException("Already closed.")
        }
    }
}
