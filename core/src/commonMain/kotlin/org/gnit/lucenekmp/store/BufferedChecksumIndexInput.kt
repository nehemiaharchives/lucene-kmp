package org.gnit.lucenekmp.store

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.CRC32


/**
 * Simple implementation of [ChecksumIndexInput] that wraps another input and delegates calls.
 */
class BufferedChecksumIndexInput(val main: IndexInput) :
    ChecksumIndexInput("BufferedChecksumIndexInput($main)") {
    val digest: BufferedChecksum = BufferedChecksum(CRC32())

    @Throws(IOException::class)
    override fun readByte(): Byte {
        val b = main.readByte()
        digest.update(b.toInt())
        return b
    }

    @Throws(IOException::class)
    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
        main.readBytes(b, offset, len)
        digest.update(b, offset, len)
    }

    @Throws(IOException::class)
    override fun readShort(): Short {
        val v = main.readShort()
        digest.updateShort(v)
        return v
    }

    @Throws(IOException::class)
    override fun readInt(): Int {
        val v = main.readInt()
        digest.updateInt(v)
        return v
    }

    @Throws(IOException::class)
    override fun readLong(): Long {
        val v = main.readLong()
        digest.updateLong(v)
        return v
    }

    @Throws(IOException::class)
    override fun readLongs(dst: LongArray, offset: Int, length: Int) {
        main.readLongs(dst, offset, length)
        digest.updateLongs(dst, offset, length)
    }

    override val checksum: Long
        get() = digest.getValue()

    @Throws(IOException::class)
    override fun close() {
        main.close()
    }


    override fun getFilePointer(): Long {
        return main.getFilePointer()
    }

    override fun length(): Long {
        return main.length()
    }

    override fun clone(): IndexInput {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun slice(sliceDescription: String?, offset: Long, length: Long): IndexInput {
        throw UnsupportedOperationException()
    }
}
