package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.jdkport.Math.addExact
import org.gnit.lucenekmp.jdkport.bitCount

/**
 * A [DataOutput] for appending data to a file in a [Directory].
 *
 *
 * Instances of this class are **not** thread-safe.
 *
 * @see Directory
 *
 * @see IndexInput
 */
abstract class IndexOutput protected constructor(resourceDescription: String, name: String) : DataOutput(),
    AutoCloseable {
    /**
     * Full description of this output, e.g. which class such as `FSIndexOutput`, and the full
     * path to the file
     */
    private val resourceDescription: String

    /**
     * Returns the name used to create this `IndexOutput`. This is especially useful when using
     * [Directory.createTempOutput].
     */
    // TODO: can we somehow use this as the default resource description or something?
    /** Just the name part from `resourceDescription`  */
    val name: String

    /**
     * Sole constructor. resourceDescription should be non-null, opaque string describing this
     * resource; it's returned from [.toString].
     */
    init {
        requireNotNull(resourceDescription) { "resourceDescription must not be null" }
        this.resourceDescription = resourceDescription
        this.name = name
    }

    /** Closes this stream to further operations.  */
    abstract override fun close()

    /** Returns the current position in this file, where the next write will occur.  */
    abstract val filePointer: Long

    /** Returns the current checksum of bytes written so far */
    abstract fun getChecksum(): Long

    override fun toString(): String {
        return resourceDescription
    }

    /**
     * Aligns the current file pointer to multiples of `alignmentBytes` bytes to improve reads
     * with mmap. This will write between 0 and `(alignmentBytes-1)` zero bytes using [ ][.writeByte].
     *
     * @param alignmentBytes the alignment to which it should forward file pointer (must be a power of
     * 2)
     * @return the new file pointer after alignment
     * @see .alignOffset
     */
    @Throws(IOException::class)
    fun alignFilePointer(alignmentBytes: Int): Long {
        val offset = this.filePointer
        val alignedOffset = alignOffset(offset, alignmentBytes)
        val count = (alignedOffset - offset).toInt()
        for (i in 0..<count) {
            writeByte(0.toByte())
        }
        return alignedOffset
    }

    companion object {
        /**
         * Aligns the given `offset` to multiples of `alignmentBytes` bytes by rounding up.
         * The alignment must be a power of 2.
         */
        fun alignOffset(offset: Long, alignmentBytes: Int): Long {
            require(offset >= 0L) { "Offset must be positive" }
            require(!(1 != Int.bitCount(alignmentBytes) || alignmentBytes < 0)) { "Alignment must be a power of 2" }
            return addExact(offset - 1L, alignmentBytes.toLong()) and (-alignmentBytes).toLong()
        }
    }
}
