package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.util.BitUtil
import okio.IOException
import org.gnit.lucenekmp.jdkport.BufferedOutputStream
import org.gnit.lucenekmp.jdkport.CRC32
import org.gnit.lucenekmp.jdkport.CheckedOutputStream
import org.gnit.lucenekmp.jdkport.OutputStream

/** Implementation class for buffered [IndexOutput] that writes to an [OutputStream].  */
open class OutputStreamIndexOutput(
    resourceDescription: String,
    name: String,
    out: OutputStream,
    bufferSize: Int
) : IndexOutput(resourceDescription, name) {
    private val crc: CRC32 = CRC32()
    private val os: XBufferedOutputStream

    override var filePointer: Long = 0L

    private var flushedOnClose = false

    /**
     * Creates a new [OutputStreamIndexOutput] with the given buffer size.
     *
     * @param bufferSize the buffer size in bytes used to buffer writes internally.
     * @throws IllegalArgumentException if the given buffer size is less than `
     * {@value Long#BYTES}`
     */
    init {
        require(bufferSize >= Long.SIZE_BYTES) { "Buffer size too small, need: " + Long.SIZE_BYTES }
        this.os = XBufferedOutputStream(CheckedOutputStream(out, crc), bufferSize)
    }

    @Throws(IOException::class)
    override fun writeByte(b: Byte) {
        os.write(b.toInt())
        this.filePointer++
    }

    @Throws(IOException::class)
    override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
        os.write(b, offset, length)
        this.filePointer += length.toLong()
    }

    @Throws(IOException::class)
    override fun writeShort(i: Short) {
        os.writeShort(i)
        this.filePointer += Short.SIZE_BYTES.toLong()
    }

    @Throws(IOException::class)
    override fun writeInt(i: Int) {
        os.writeInt(i)
        this.filePointer += Int.SIZE_BYTES.toLong()
    }

    @Throws(IOException::class)
    override fun writeLong(i: Long) {
        os.writeLong(i)
        this.filePointer += Long.SIZE_BYTES.toLong()
    }

    override fun close() {
        os.use { o ->
            // We want to make sure that os.flush() was running before close:
            // BufferedOutputStream may ignore IOExceptions while flushing on close().
            // We keep this also in Java 8, although it claims to be fixed there,
            // because there are more bugs around this! See:
            // # https://bugs.openjdk.java.net/browse/JDK-7015589
            // # https://bugs.openjdk.java.net/browse/JDK-8054565
            if (!flushedOnClose) {
                flushedOnClose = true // set this BEFORE calling flush!
                o.flush()
            }
        }
    }

    override fun getChecksum(): Long {
        os.flush()
        return crc.getValue()
    }

    /** This subclass is an optimization for writing primitives. Don't use outside of this class!  */
    private class XBufferedOutputStream(out: OutputStream, size: Int) :
        BufferedOutputStream(out, size) {
        @Throws(IOException::class)
        fun flushIfNeeded(len: Int) {
            if (len > buf.size - count) {
                flush()
            }
        }

        @Throws(IOException::class)
        fun writeShort(i: Short) {
            flushIfNeeded(Short.SIZE_BYTES)
            BitUtil.VH_LE_SHORT.set(buf, count, i)
            count += Short.SIZE_BYTES
        }

        @Throws(IOException::class)
        fun writeInt(i: Int) {
            flushIfNeeded(Int.SIZE_BYTES)
            BitUtil.VH_LE_INT.set(buf, count, i)
            count += Int.SIZE_BYTES
        }

        @Throws(IOException::class)
        fun writeLong(i: Long) {
            flushIfNeeded(Long.SIZE_BYTES)
            BitUtil.VH_LE_LONG.set(buf, count, i)
            count += Long.SIZE_BYTES
        }

        @Throws(IOException::class)
        override fun write(b: Int) {
            // override single byte write to avoid synchronization overhead now that JEP374 removed biased
            // locking
            val buffer: ByteArray = buf
            val count: Int = this.count
            if (count >= buffer.size) {
                super.write(b)
            } else {
                buffer[count] = b.toByte()
                this.count = count + 1
            }
        }
    }
}
