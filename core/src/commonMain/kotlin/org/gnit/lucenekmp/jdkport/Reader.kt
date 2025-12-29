package org.gnit.lucenekmp.jdkport

import okio.IOException

/**
 * A minimal multiplatform abstraction for reading characters.
 * This class mimics many of the core methods of java.io.Reader, but only those that you really need.
 */
@Ported(from = "java.io.Reader")
abstract class Reader: Readable, AutoCloseable {
    companion object {
        protected const val TRANSFER_BUFFER_SIZE: Int = 8192

        /**
         * Returns a Reader that reads no characters.
         */
        fun nullReader(): Reader = object : Reader() {
            private var closed = false
            private fun ensureOpen() {
                if (closed) throw Exception("Stream closed")
            }
            override fun read(cbuf: CharArray, off: Int, len: Int): Int {
                ensureOpen()
                if (len == 0) return 0
                return -1
            }
            override fun close() {
                closed = true
            }

            override fun ready(): Boolean {
                ensureOpen()
                return false
            }
        }
    }

    /**
     * Reads up to [len] characters into [cbuf] starting at offset [off].
     * Returns the number of characters read, or -1 if the end of the stream is reached.
     */
    abstract fun read(cbuf: CharArray, off: Int, len: Int): Int

    /**
     * Closes the reader and releases any associated resources.
     */
    abstract override fun close()

    /**
     * Reads a single character.
     *
     * The default implementation uses [read] on a one-character buffer.
     */
    open fun read(): Int {
        val buf = CharArray(1)
        val n = read(buf, 0, 1)
        return if (n == -1) -1 else buf[0].code
    }

    /**
     * Reads all characters from this Reader and writes them to [out].
     * Returns the number of characters transferred.
     */
    open fun transferTo(out: Writer): Long {
        var transferred: Long = 0
        val buffer = CharArray(TRANSFER_BUFFER_SIZE)
        while (true) {
            val nRead = read(buffer, 0, TRANSFER_BUFFER_SIZE)
            if (nRead == -1) break
            out.write(buffer, 0, nRead)
            transferred += nRead
        }
        return transferred
    }

    /**
     * Reads characters into a [CharBuffer]. This default implementation reads into an array and then puts
     * the characters into the buffer.
     */
    override fun read(target: CharBuffer): Int {
        if (target.isReadOnly())
            throw Exception("Read-only buffer")
        val remaining = target.remaining()
        if (remaining == 0) return 0
        val temp = CharArray(remaining)
        val nRead = read(temp, 0, remaining)
        if (nRead > 0) {
            target.put(temp, 0, nRead)
        }
        return nRead
    }

    /**
     * Tells whether this stream is ready to be read.
     *
     * @return True if the next read() is guaranteed not to block for input,
     * false otherwise.  Note that returning false does not guarantee that the
     * next read will block.
     *
     * @throws     IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    open fun ready(): Boolean{
        return false
    }


    /**
     * Resets the stream.  If the stream has been marked, then attempt to
     * reposition it at the mark.  If the stream has not been marked, then
     * attempt to reset it in some way appropriate to the particular stream,
     * for example by repositioning it to its starting point.  Not all
     * character-input streams support the reset() operation, and some support
     * reset() without supporting mark().
     *
     * @throws     IOException  If the stream has not been marked,
     * or if the mark has been invalidated,
     * or if the stream does not support reset(),
     * or if some other I/O error occurs
     */
    @Throws(IOException::class)
    open fun reset() {
        throw IOException("reset() not supported")
    }
}
