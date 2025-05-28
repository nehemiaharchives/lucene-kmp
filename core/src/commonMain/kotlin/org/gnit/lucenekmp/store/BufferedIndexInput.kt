package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.ByteOrder
import okio.EOFException
import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.util.GroupVIntUtil
import kotlin.math.max
import kotlin.math.min


/** Base implementation class for buffered [IndexInput].  */
abstract class BufferedIndexInput constructor(resourceDesc: String, bufferSize: Int = BUFFER_SIZE) :
    IndexInput(resourceDesc), RandomAccessInput {
    /** Returns buffer size  */
    val bufferSize: Int

    private var buffer: ByteBuffer = EMPTY_BYTEBUFFER

    private var bufferStart: Long = 0 // position in file of buffer

    override fun readByte(): Byte {
        if (buffer.hasRemaining() == false) {
            refill()
        }
        return buffer.get()
    }

    constructor(resourceDesc: String, context: IOContext) : this(
        resourceDesc,
        bufferSize(context)
    )

    /** Inits BufferedIndexInput with a specific bufferSize  */
    init {
        checkBufferSize(bufferSize)
        this.bufferSize = bufferSize
    }

    private fun checkBufferSize(bufferSize: Int) {
        require(bufferSize >= MIN_BUFFER_SIZE) { "bufferSize must be at least MIN_BUFFER_SIZE (got $bufferSize)" }
    }

    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
        readBytes(b, offset, len, true)
    }

    override fun readBytes(b: ByteArray, offset: Int, len: Int, useBuffer: Boolean) {
        var offset = offset
        var len = len
        val available: Int = buffer.remaining()
        if (len <= available) {
            // the buffer contains enough data to satisfy this request
            if (len > 0)  // to allow b to be null if len is 0...
                buffer.get(b, offset, len)
        } else {
            // the buffer does not have enough data. First serve all we've got.
            if (available > 0) {
                buffer.get(b, offset, available)
                offset += available
                len -= available
            }
            // and now, read the remaining 'len' bytes:
            if (useBuffer && len < bufferSize) {
                // If the amount left to read is small enough, and
                // we are allowed to use our buffer, do it in the usual
                // buffered way: fill the buffer and copy from it:
                refill()
                if (buffer.remaining() < len) {
                    // Throw an exception when refill() could not read len bytes:
                    buffer.get(b, offset, buffer.remaining())
                    throw EOFException("read past EOF: $this")
                } else {
                    buffer.get(b, offset, len)
                }
            } else {
                // The amount left to read is larger than the buffer
                // or we've been asked to not use our buffer -
                // there's no performance reason not to read it all
                // at once. Note that unlike the previous code of
                // this function, there is no need to do a seek
                // here, because there's no need to reread what we
                // had in the buffer.
                val after: Long = bufferStart + buffer.position + len
                if (after > length()) throw EOFException("read past EOF: $this")
                readInternal(ByteBuffer.wrap(b, offset, len))
                bufferStart = after
                buffer.limit(0) // trigger refill() on read
            }
        }
    }

    override fun readShort(): Short {
        if (Short.SIZE_BYTES <= buffer.remaining()) {
            return buffer.getShort()
        } else {
            return super.readShort()
        }
    }

    override fun readInt(): Int {
        if (Int.SIZE_BYTES <= buffer.remaining()) {
            return buffer.getInt()
        } else {
            return super.readInt()
        }
    }

    override fun readGroupVInt(dst: IntArray, offset: Int) {
        val len: Int =
            GroupVIntUtil.readGroupVInt(
                this,
                buffer.remaining().toLong(),
                { p: Long -> buffer.getInt(p.toInt()) },
                buffer.position.toLong(),
                dst,
                offset
            )
        if (len > 0) {
            buffer.position(buffer.position + len)
        }
    }

    override fun readLong(): Long {
        if (Long.SIZE_BYTES <= buffer.remaining()) {
            return buffer.getLong()
        } else {
            return super.readLong()
        }
    }

    override fun readFloats(dst: FloatArray, offset: Int, len: Int) {
        var remainingDst = len
        while (remainingDst > 0) {
            val cnt = min(buffer.remaining() / Float.SIZE_BYTES, remainingDst)
            buffer.asFloatBuffer().get(dst, offset + len - remainingDst, cnt)
            buffer.position(buffer.position + Float.SIZE_BYTES * cnt)
            remainingDst -= cnt
            if (remainingDst > 0) {
                if (buffer.hasRemaining()) {
                    dst[offset + len - remainingDst] = Float.intBitsToFloat(readInt())
                    --remainingDst
                } else {
                    refill()
                }
            }
        }
    }

    override fun readLongs(dst: LongArray, offset: Int, len: Int) {
        var remainingDst = len
        while (remainingDst > 0) {
            val cnt = min(buffer.remaining() / Long.SIZE_BYTES, remainingDst)
            buffer.asLongBuffer().get(dst, offset + len - remainingDst, cnt)
            buffer.position(buffer.position + Long.SIZE_BYTES * cnt)
            remainingDst -= cnt
            if (remainingDst > 0) {
                if (buffer.hasRemaining()) {
                    dst[offset + len - remainingDst] = readLong()
                    --remainingDst
                } else {
                    refill()
                }
            }
        }
    }

    override fun readInts(dst: IntArray, offset: Int, len: Int) {
        var remainingDst = len
        while (remainingDst > 0) {
            val cnt = min(buffer.remaining() / Int.SIZE_BYTES, remainingDst)
            buffer.asIntBuffer().get(dst, offset + len - remainingDst, cnt)
            buffer.position(buffer.position + Int.SIZE_BYTES * cnt)
            remainingDst -= cnt
            if (remainingDst > 0) {
                if (buffer.hasRemaining()) {
                    dst[offset + len - remainingDst] = readInt()
                    --remainingDst
                } else {
                    refill()
                }
            }
        }
    }

    // Computes an offset into the current buffer from an absolute position to read
    // `width` bytes from.  If the buffer does not contain the position, then we
    // readjust the bufferStart and refill.
    @Throws(IOException::class)
    private fun resolvePositionInBuffer(pos: Long, width: Int): Long {
        val index = pos - bufferStart
        if (index >= 0 && index <= buffer.limit - width) {
            return index
        }
        if (index < 0) {
            // if we're moving backwards, then try and fill up the previous page rather than
            // starting again at the current pos, to avoid successive backwards reads reloading
            // the same data over and over again.  We also check that we can read `width`
            // bytes without going over the end of the buffer
            bufferStart = max(bufferStart - bufferSize, pos + width - bufferSize)
            bufferStart = max(bufferStart, 0)
            bufferStart = min(bufferStart, pos)
        } else {
            // we're moving forwards, reset the buffer to start at pos
            bufferStart = pos
        }
        buffer.limit(0) // trigger refill() on read
        seekInternal(bufferStart)
        refill()
        return pos - bufferStart
    }

    override fun readByte(pos: Long): Byte {
        val index = resolvePositionInBuffer(pos, Byte.SIZE_BYTES)
        return buffer.get(index.toInt())
    }

    override fun readBytes(pos: Long, bytes: ByteArray, offset: Int, len: Int) {
        var pos = pos
        var offset = offset
        var len = len
        if (len <= bufferSize) {
            // the buffer is big enough to satisfy this request
            if (len > 0) { // to allow b to be null if len is 0...
                val index = resolvePositionInBuffer(pos, len)
                buffer.get(index.toInt(), bytes, offset, len)
            }
        } else {
            while (len > bufferSize) {
                val index = resolvePositionInBuffer(pos, bufferSize)
                buffer.get(index.toInt(), bytes, offset, bufferSize)
                len -= bufferSize
                offset += bufferSize
                pos += bufferSize.toLong()
            }
            val index = resolvePositionInBuffer(pos, len)
            buffer.get(index.toInt(), bytes, offset, len)
        }
    }

    override fun readShort(pos: Long): Short {
        val index = resolvePositionInBuffer(pos, Short.SIZE_BYTES)
        return buffer.getShort(index.toInt())
    }

    override fun readInt(pos: Long): Int {
        val index = resolvePositionInBuffer(pos, Int.SIZE_BYTES)
        return buffer.getInt(index.toInt())
    }

    override fun readLong(pos: Long): Long {
        val index = resolvePositionInBuffer(pos, Long.SIZE_BYTES)
        return buffer.getLong(index.toInt())
    }

    @Throws(IOException::class)
    private fun refill() {
        val start: Long = bufferStart + buffer.position
        var end = start + bufferSize
        if (end > length())  // don't read past EOF
            end = length()
        val newLength = (end - start).toInt()
        if (newLength <= 0) throw EOFException("read past EOF: $this")

        if (buffer === EMPTY_BYTEBUFFER) {
            buffer =
                ByteBuffer.allocate(bufferSize)
                    .order(ByteOrder.LITTLE_ENDIAN) // allocate buffer lazily
            seekInternal(bufferStart)
        }
        buffer.position(0)
        buffer.limit(newLength)
        bufferStart = start
        readInternal(buffer)
        // Make sure sub classes don't mess up with the buffer.
        assert(buffer.order() == ByteOrder.LITTLE_ENDIAN) { buffer.order() }
        assert(buffer.remaining() == 0) { "should have thrown EOFException" }
        assert(buffer.position == newLength)
        buffer.flip()
    }

    /**
     * Expert: implements buffer refill. Reads bytes from the current position in the input.
     *
     * @param b the buffer to read bytes into
     */
    @Throws(IOException::class)
    protected abstract fun readInternal(b: ByteBuffer)

    override val filePointer: Long
        get() = bufferStart + buffer.position

    override fun seek(pos: Long) {
        if (pos >= bufferStart && pos < (bufferStart + buffer.limit)) buffer.position((pos - bufferStart).toInt()) // seek within buffer
        else {
            bufferStart = pos
            buffer.limit(0) // trigger refill() on read
            seekInternal(pos)
        }
    }

    /**
     * Expert: implements seek. Sets current position in this file, where the next [ ][.readInternal] will occur.
     *
     * @see .readInternal
     */
    @Throws(IOException::class)
    protected abstract fun seekInternal(pos: Long)

    override fun clone(): BufferedIndexInput {
        val clone = super.clone() as BufferedIndexInput

        clone.buffer = EMPTY_BYTEBUFFER
        clone.bufferStart = this.filePointer

        return clone
    }

    override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
        return wrap(sliceDescription, this, offset, length)
    }

    override fun prefetch(offset: Long, length: Long) {
        TODO("Not yet implemented")
    }

    /** Implementation of an IndexInput that reads from a portion of a file.  */
    private class SlicedIndexInput(
        sliceDescription: String,
        base: IndexInput,
        offset: Long,
        length: Long
    ) : BufferedIndexInput(
        if (sliceDescription == null)
            base.toString()
        else
            ("$base [slice=$sliceDescription]"),
        BUFFER_SIZE
    ) {
        var base: IndexInput
        var fileOffset: Long
        var length: Long

        init {
            require(!((length or offset) < 0 || length > base.length() - offset)) { "slice() $sliceDescription out of bounds: $base" }
            this.base = base.clone()
            this.fileOffset = offset
            this.length = length
        }

        override fun clone(): SlicedIndexInput {
            val clone = super.clone() as SlicedIndexInput
            clone.base = base.clone()
            clone.fileOffset = fileOffset
            clone.length = length
            return clone
        }

        @Throws(IOException::class)
        override fun readInternal(b: ByteBuffer) {
            val start = this.filePointer
            if (start + b.remaining() > length) {
                throw EOFException("read past EOF: $this")
            }
            base.seek(fileOffset + start)
            base.readBytes(b.array(), b.position, b.remaining())
            b.position(b.position + b.remaining())
        }

        override fun seekInternal(pos: Long) {}

        @Throws(IOException::class)
        override fun close() {
            base.close()
        }

        override fun length(): Long {
            return length
        }
    }

    companion object {
        private val EMPTY_BYTEBUFFER: ByteBuffer =
            ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN)

        /** Default buffer size set to {@value #BUFFER_SIZE}.  */
        const val BUFFER_SIZE: Int = 1024

        /** Minimum buffer size allowed  */
        const val MIN_BUFFER_SIZE: Int = 8

        // The normal read buffer size defaults to 1024, but
        // increasing this during merging seems to yield
        // performance gains.  However we don't want to increase
        // it too much because there are quite a few
        // BufferedIndexInputs created during merging.  See
        // LUCENE-888 for details.
        /** A buffer size for merges set to {@value #MERGE_BUFFER_SIZE}.  */
        const val MERGE_BUFFER_SIZE: Int = 4096

        /** Returns default buffer sizes for the given [IOContext]  */
        fun bufferSize(context: IOContext): Int {
            when (context.context) {
                IOContext.Context.MERGE -> return MERGE_BUFFER_SIZE
                IOContext.Context.DEFAULT, IOContext.Context.FLUSH -> return BUFFER_SIZE
                else -> return BUFFER_SIZE
            }
        }

        /**
         * Wraps a portion of another IndexInput with buffering.
         *
         *
         * **Please note:** This is in most cases ineffective, because it may double buffer!
         */
        fun wrap(
            sliceDescription: String, other: IndexInput, offset: Long, length: Long
        ): BufferedIndexInput {
            return SlicedIndexInput(sliceDescription, other, offset, length)
        }
    }
}
