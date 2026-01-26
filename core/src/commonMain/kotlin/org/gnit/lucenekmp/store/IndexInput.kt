package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.jdkport.Optional

/**
 * Abstract base class for input from a file in a [Directory]. A random-access input stream.
 * Used for all Lucene index input operations.
 *
 *
 * `IndexInput` may only be used from one thread, because it is not thread safe (it keeps
 * internal state like file position). To allow multithreaded use, every `IndexInput` instance
 * must be cloned before it is used in another thread. Subclasses must therefore implement [ ][.clone], returning a new `IndexInput` which operates on the same underlying resource, but
 * positioned independently.
 *
 *
 * **Warning:** Lucene never closes cloned `IndexInput`s, it will only call [ ][.close] on the original object.
 *
 *
 * If you access the cloned IndexInput after closing the original object, any `readXXX
` *  methods will throw [AlreadyClosedException].
 *
 * @see Directory
 */
abstract class IndexInput protected constructor(resourceDescription: String) : DataInput(), AutoCloseable {
    private val resourceDescription: String

    /**
     * resourceDescription should be a non-null, opaque string describing this resource; it's returned
     * from [.toString].
     */
    init {
        requireNotNull(resourceDescription) { "resourceDescription must not be null" }
        this.resourceDescription = resourceDescription
    }

    /** Closes the stream to further operations.  */
    abstract override fun close()

    /**
     * Returns the current position in this file, where the next read will occur.
     *
     * @see .seek
     */
    abstract val filePointer: Long

    /**
     * Sets current position in this file, where the next read will occur. If this is beyond the end
     * of the file then this will throw `EOFException` and then the stream is in an undetermined
     * state.
     *
     * @see .getFilePointer
     */
    @Throws(IOException::class)
    abstract fun seek(pos: Long)

    /**
     * {@inheritDoc}
     *
     *
     * Behavior is functionally equivalent to seeking to `getFilePointer() + numBytes`.
     *
     * @see .getFilePointer
     * @see .seek
     */
    @Throws(IOException::class)
    override fun skipBytes(numBytes: Long) {
        require(numBytes >= 0) { "numBytes must be >= 0, got $numBytes" }
        val skipTo = this.filePointer + numBytes
        seek(skipTo)
    }

    /** The number of bytes in the file.  */
    abstract fun length(): Long

    override fun toString(): String {
        return resourceDescription
    }

    /**
     * {@inheritDoc}
     *
     *
     * **Warning:** Lucene never closes cloned `IndexInput`s, it will only call [ ][.close] on the original object.
     *
     *
     * If you access the cloned IndexInput after closing the original object, any `readXXX
    ` *  methods will throw [AlreadyClosedException].
     *
     *
     * This method is NOT thread safe, so if the current `IndexInput` is being used by one
     * thread while `clone` is called by another, disaster could strike.
     */
    override fun clone(): IndexInput {
        // Create an independent view over the same underlying resource
        val pos = this.filePointer
        val clone = this.slice("clone", 0L, this.length())
        clone.seek(pos)
        return clone
    }

    /**
     * Creates a slice of this index input, with the given description, offset, and length. The slice
     * is sought to the beginning.
     */
    @Throws(IOException::class)
    abstract fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput

    /**
     * Create a slice with a specific [ReadAdvice]. This is typically used by [ ] implementations to honor the [ReadAdvice] of each file within the
     * compound file.
     *
     *
     * **NOTE**: it is only legal to call this method if this [IndexInput] has been open
     * with [ReadAdvice.NORMAL]. However, this method accepts any [ReadAdvice] value but
     * `null` as a read advice for the slice.
     *
     *
     * The default implementation delegates to [.slice] and ignores the
     * [ReadAdvice].
     */
    @Throws(IOException::class)
    open fun slice(sliceDescription: String, offset: Long, length: Long, readAdvice: ReadAdvice): IndexInput {
        return slice(sliceDescription, offset, length)
    }

    /**
     * Subclasses call this to get the String for resourceDescription of a slice of this `IndexInput`.
     */
    protected fun getFullSliceDescription(sliceDescription: String?): String {
        return if (sliceDescription == null) {
            // Clones pass null sliceDescription:
            toString()
        } else {
            toString() + " [slice=" + sliceDescription + "]"
        }
    }

    /**
     * Creates a random-access slice of this index input, with the given offset and length.
     *
     *
     * The default implementation calls [.slice], and it doesn't support random access, it
     * implements absolute reads as seek+read.
     */
    @Throws(IOException::class)
    fun randomAccessSlice(offset: Long, length: Long): RandomAccessInput {
        val slice = slice("randomaccess", offset, length)
        if (slice is RandomAccessInput) {
            // slice() already supports random access
            return slice as RandomAccessInput
        } else {
            // return default impl
            return object : RandomAccessInput {
                override fun length(): Long {
                    require(length == slice.length())
                    return slice.length()
                }

                @Throws(IOException::class)
                override fun readByte(pos: Long): Byte {
                    slice.seek(pos)
                    return slice.readByte()
                }

                @Throws(IOException::class)
                override fun readBytes(pos: Long, bytes: ByteArray, offset: Int, length: Int) {
                    slice.seek(pos)
                    slice.readBytes(bytes, offset, length)
                }

                @Throws(IOException::class)
                override fun readShort(pos: Long): Short {
                    slice.seek(pos)
                    return slice.readShort()
                }

                @Throws(IOException::class)
                override fun readInt(pos: Long): Int {
                    slice.seek(pos)
                    return slice.readInt()
                }

                @Throws(IOException::class)
                override fun readLong(pos: Long): Long {
                    slice.seek(pos)
                    return slice.readLong()
                }

                @Throws(IOException::class)
                override fun prefetch(offset: Long, length: Long) {
                    slice.prefetch(offset, length)
                }

                override fun toString(): String {
                    return "RandomAccessInput(" + this@IndexInput.toString() + ")"
                }
            }
        }
    }

    /**
     * Optional method: Give a hint to this input that some bytes will be read in the near future.
     * IndexInput implementations may take advantage of this hint to start fetching pages of data
     * immediately from storage.
     *
     *
     * The default implementation is a no-op.
     *
     * @param offset start offset
     * @param length the number of bytes to prefetch
     */
    @Throws(IOException::class)
    open fun prefetch(offset: Long, length: Long) {
    }

    /**
     * Optional method: Give a hint to this input about the change in read access pattern. IndexInput
     * implementations may take advantage of this hint to optimize reads from storage.
     *
     *
     * The default implementation is a no-op.
     */
    @Throws(IOException::class)
    open fun updateReadAdvice(readAdvice: ReadAdvice?) {
    }

    open val isLoaded: Optional<Boolean?>
        /**
         * Returns a hint whether all the contents of this input are resident in physical memory. It's a
         * hint because the operating system may have paged out some of the data by the time this method
         * returns. If the optional is true, then it's likely that the contents of this input are resident
         * in physical memory. A value of false does not imply that the contents are not resident in
         * physical memory. An empty optional is returned if it is not possible to determine.
         *
         *
         * This runs in linear time with the [.length] of this input / page size.
         *
         *
         * The default implementation returns an empty optional.
         */
        get() = Optional.empty<Boolean?>()
}
