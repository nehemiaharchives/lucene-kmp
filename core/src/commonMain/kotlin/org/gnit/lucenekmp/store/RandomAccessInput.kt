package org.gnit.lucenekmp.store

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Optional


/**
 * Random Access Index API. Unlike [IndexInput], this has no concept of file position, all
 * reads are absolute. However, like IndexInput, it is only intended for use by a single thread.
 */
interface RandomAccessInput {
    /** The number of bytes in the file.  */
    fun length(): Long

    /**
     * Reads a byte at the given position in the file
     *
     * @see DataInput.readByte
     */
    @Throws(IOException::class)
    fun readByte(pos: Long): Byte

    /**
     * Reads a specified number of bytes starting at a given position into an array at the specified
     * offset.
     *
     * @see DataInput.readBytes
     */
    @Throws(IOException::class)
    fun readBytes(pos: Long, bytes: ByteArray, offset: Int, length: Int) {
        for (i in 0..<length) {
            bytes[offset + i] = readByte(pos + i)
        }
    }

    /**
     * Reads a short (LE byte order) at the given position in the file
     *
     * @see DataInput.readShort
     *
     * @see BitUtil.VH_LE_SHORT
     */
    @Throws(IOException::class)
    fun readShort(pos: Long): Short

    /**
     * Reads an integer (LE byte order) at the given position in the file
     *
     * @see DataInput.readInt
     *
     * @see BitUtil.VH_LE_INT
     */
    @Throws(IOException::class)
    fun readInt(pos: Long): Int

    /**
     * Reads a long (LE byte order) at the given position in the file
     *
     * @see DataInput.readLong
     *
     * @see BitUtil.VH_LE_LONG
     */
    @Throws(IOException::class)
    fun readLong(pos: Long): Long

    /**
     * Prefetch data in the background.
     *
     * @see IndexInput.prefetch
     */
    @Throws(IOException::class)
    fun prefetch(offset: Long, length: Long) {
    }

    val isLoaded: Optional<Boolean?>
        /**
         * Returns a hint whether all the contents of this input are resident in physical memory.
         *
         * @see IndexInput.isLoaded
         */
        get() = Optional.empty<Boolean?>()
}
