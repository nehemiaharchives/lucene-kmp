package org.gnit.lucenekmp.jdkport

import kotlinx.io.Source
import kotlinx.io.IOException

/**
 * Multiplatform buffered character reader that mirrors the API of Java's BufferedReader&#8203;:contentReference[oaicite:0]{index=0}.
 * It reads text from a Source (byte stream) and buffers characters to provide efficient
 * reading of single characters, arrays, and lines&#8203;:contentReference[oaicite:1]{index=1}. The buffer size may be specified,
 * or a default size (8192) is used, which is large enough for most purposes&#8203;:contentReference[oaicite:2]{index=2}.
 *
 * This class uses a `kotlinx.io.Source` as the underlying input and decodes bytes to characters
 * using UTF-8. It supports marking a position in the input and
 * resetting back to that position, and is intended to behave like `java.io.BufferedReader`.
 */
class BufferedReader(
    private val source: Source,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE
): Reader() {
    companion object {
        /** Default buffer size (in chars) similar to java.io.BufferedReader (8192 chars)&#8203;:contentReference[oaicite:3]{index=3} */
        const val DEFAULT_BUFFER_SIZE: Int = 8192
    }

    private var buf: CharArray = CharArray(
        if (bufferSize > 0) bufferSize
        else throw IllegalArgumentException("Buffer size <= 0")
    )
    private var pos: Int = 0          // current position in buffer (next char to read)
    private var end: Int = 0          // index one past last char in buffer (buffer content length)
    private var markPos: Int = -1     // position of mark in buffer, -1 if no mark
    private var markLimit: Int = 0    // read-ahead limit for the current mark
    private var closed: Boolean = false

    /** Ensures the stream is not closed, throwing IOException if it is&#8203;:contentReference[oaicite:4]{index=4}. */
    private fun ensureOpen() {
        if (closed) {
            throw IOException("Stream closed")
        }
    }

    /**
     * Reads a single character from the stream&#8203;:contentReference[oaicite:5]{index=5}. Returns the character as an integer (0-65535)
     * or -1 if the end of the stream has been reached&#8203;:contentReference[oaicite:6]{index=6}.
     *
     * @throws IOException if an I/O error occurs.
     */
    override fun read(): Int {
        ensureOpen()
        if (pos >= end) {
            // Buffer is empty, attempt to fill
            if (fillBuffer() == -1) {
                return -1  // EOF
            }
        }
        // Return next character as int (0x00-0xffff range)
        val ch = buf[pos++]
        return ch.code
    }

    /**
     * Reads up to [len] characters into the specified portion of the character array [cbuf]&#8203;:contentReference[oaicite:7]{index=7}.
     * It returns the number of characters read, or -1 if the end of the stream has been reached before
     * any characters are read&#8203;:contentReference[oaicite:8]{index=8}. This method will block until at least one character is available
     * or end-of-stream is detected.
     *
     * @param cbuf the destination buffer.
     * @param off the start offset in [cbuf] at which to store characters.
     * @param len the maximum number of characters to read.
     * @return the number of characters read, or -1 if no characters were read because the stream is at EOF.
     * @throws IndexOutOfBoundsException if the parameters are invalid.
     * @throws IOException if an I/O error occurs.
     */
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        ensureOpen()
        if (off < 0 || len < 0 || len > cbuf.size - off) {
            throw IndexOutOfBoundsException("Offset and length out of bounds")
        }
        if (len == 0) {
            return 0
        }
        var charsRead = 0
        while (charsRead < len) {
            if (pos >= end) {
                // Buffer empty, fill it
                if (fillBuffer() == -1) {
                    // EOF reached
                    return if (charsRead > 0) charsRead else -1
                }
            }
            // Copy as much as possible from buffer to output
            val available = end - pos
            val toCopy = minOf(len - charsRead, available)
            buf.copyInto(destination = cbuf, destinationOffset = off + charsRead, startIndex = pos, endIndex = pos + toCopy)
            pos += toCopy
            charsRead += toCopy
            if (charsRead >= len) break  // filled the requested length
        }
        return charsRead
    }

    /**
     * Reads a line of text from the stream&#8203;:contentReference[oaicite:9]{index=9}. A line is considered to be terminated by any one
     * of a line feed (`'\n'`), a carriage return (`'\r'`), or a carriage return followed immediately by
     * a line feed&#8203;:contentReference[oaicite:10]{index=10}. The line-termination characters are not included in the returned string.
     *
     * @return a String containing the contents of the line, not including any line-termination characters,
     * or `null` if the end of the stream has been reached&#8203;:contentReference[oaicite:11]{index=11}.
     * @throws IOException if an I/O error occurs.
     */
    fun readLine(): String? {
        ensureOpen()
        var result: StringBuilder? = null
        while (true) {
            if (pos >= end) {
                // Buffer empty, try to fill
                if (fillBuffer() == -1) {
                    // EOF: if we have accumulated some characters, return them; otherwise null
                    return if (result != null && result.isNotEmpty()) {
                        result.toString()
                    } else {
                        null
                    }
                }
            }
            // Scan buffer for line terminator
            for (i in pos until end) {
                val c = buf[i]
                if (c == '\n' || c == '\r') {
                    // Terminator found
                    val line = buf.concatToString(pos, pos + (i - pos))
                    pos = i + 1  // consume the line terminator
                    if (c == '\r') {
                        // If CR, skip following LF if present (CRLF sequence)
                        if (pos < end || fillBuffer() != -1) {
                            if (pos < end && buf[pos] == '\n') {
                                pos++
                            }
                        }
                    }
                    // Return the line, with any previous partial content appended
                    if (result != null) {
                        result.append(line)
                        return result.toString()
                    } else {
                        return line
                    }
                }
            }
            // No newline found in buffer chunk, append it to result and continue
            val chunk = buf.concatToString(pos, pos + (end - pos))
            if (result == null) {
                result = StringBuilder(chunk)
            } else {
                result.append(chunk)
            }
            pos = end  // buffer fully consumed, loop to fill more
        }
    }

    /**
     * Tells whether this stream is ready to be read&#8203;:contentReference[oaicite:12]{index=12}. A buffered reader is ready if the buffer
     * is not empty or if the underlying source has more data available (not at end)&#8203;:contentReference[oaicite:13]{index=13}.
     *
     * @return `true` if the next read is guaranteed not to block for input, `false` otherwise&#8203;:contentReference[oaicite:14]{index=14}.
     * @throws IOException if an I/O error occurs.
     */
    fun ready(): Boolean {
        ensureOpen()
        if (pos < end) {
            return true  // buffer has data ready
        }
        // If buffer empty, check if source has any bytes readily available or if we're at EOF
        return !source.exhausted()  // if not exhausted, we assume data can be read (may block if no immediate data)
    }

    /**
     * Marks the present position in the stream&#8203;:contentReference[oaicite:15]{index=15}. Subsequent calls to [reset] will attempt to
     * reposition the stream to this point&#8203;:contentReference[oaicite:16]{index=16}. The [readAheadLimit] argument tells how many characters
     * may be read while still preserving the mark. If more than [readAheadLimit] chars are read after marking,
     * the mark is invalidated.
     *
     * A limit value larger than the buffer size will cause a new buffer to be allocated with a size not smaller
     * than the given limit&#8203;:contentReference[oaicite:17]{index=17}.
     *
     * @param readAheadLimit the number of characters to read while still preserving the mark position&#8203;:contentReference[oaicite:18]{index=18}.
     * @throws IllegalArgumentException if [readAheadLimit] is negative&#8203;:contentReference[oaicite:19]{index=19}.
     * @throws IOException if an I/O error occurs.
     */
    fun mark(readAheadLimit: Int) {
        ensureOpen()
        if (readAheadLimit < 0) {
            throw IllegalArgumentException("Read-ahead limit < 0")
        }
        markLimit = readAheadLimit
        markPos = pos
        // Expand buffer if required to accommodate readAheadLimit
        if (readAheadLimit > buf.size) {
            val newBuf = CharArray(readAheadLimit)
            // Copy current buffer content (unread chars) to the new buffer
            val unreadCount = end - pos
            buf.copyInto(destination = newBuf, destinationOffset = 0, startIndex = pos, endIndex = end)
            // Adjust positions for new buffer
            markPos = 0
            pos = 0
            end = unreadCount
            buf = newBuf
        }
    }

    /**
     * Resets the stream to the most recent mark&#8203;:contentReference[oaicite:20]{index=20}. After reset, the stream will read input from
     * the point where [mark] was last called. If the stream has not been marked or if the mark was invalidated
     * due to reading beyond the read-ahead limit, an IOException is thrown&#8203;:contentReference[oaicite:21]{index=21}.
     *
     * @throws IOException if the stream has never been marked, or if the mark has been invalidated&#8203;:contentReference[oaicite:22]{index=22}.
     */
    fun reset() {
        ensureOpen()
        if (markPos < 0) {
            throw IOException("Stream not marked")
        }
        pos = markPos  // reset position to mark
    }

    /**
     * Indicates whether this stream supports the [mark] operation, which it does&#8203;:contentReference[oaicite:23]{index=23}.
     *
     * @return always `true` for BufferedReader&#8203;:contentReference[oaicite:24]{index=24}.
     */
    fun markSupported(): Boolean = true

    /**
     * Skips over [n] characters in the stream&#8203;:contentReference[oaicite:25]{index=25}. This method may read and discard characters
     * in multiple chunks until the specified number of characters have been skipped or the end of stream is reached.
     * It returns the actual number of characters skipped.
     *
     * @param n the number of characters to skip.
     * @return the number of characters actually skipped&#8203;:contentReference[oaicite:26]{index=26}.
     * @throws IllegalArgumentException if [n] is negative&#8203;:contentReference[oaicite:27]{index=27}.
     * @throws IOException if an I/O error occurs.
     */
    fun skip(n: Long): Long {
        ensureOpen()
        if (n < 0) {
            throw IllegalArgumentException("skip value is negative")
        }
        var remaining = n
        var skipped: Long = 0
        if (remaining <= 0) return 0
        // First skip any characters already in the buffer
        if (pos < end) {
            val available = (end - pos).toLong()
            if (available >= remaining) {
                pos += remaining.toInt()
                return remaining
            } else {
                pos = end  // consume all buffered chars
                remaining -= available
                skipped += available
            }
        }
        // Now skip additional characters by reading and discarding
        val buffer = CharArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0) {
            if (fillBuffer() == -1) {
                break  // EOF
            }
            val toDiscard = minOf(remaining.toInt(), end - pos)
            pos += toDiscard
            remaining -= toDiscard
            skipped += toDiscard
        }
        return skipped
    }

    /**
     * Closes the stream and the underlying source, releasing any resources&#8203;:contentReference[oaicite:28]{index=28}. Once the stream is closed,
     * further read(), ready(), mark(), reset(), or skip() invocations will throw an IOException&#8203;:contentReference[oaicite:29]{index=29}.
     * Closing a previously closed stream has no effect&#8203;:contentReference[oaicite:30]{index=30}.
     *
     * @throws IOException if an I/O error occurs in the underlying source.
     */
    override fun close() {
        if (!closed) {
            // Close the source (it is safe to close more than once)&#8203;:contentReference[oaicite:31]{index=31}.
            try {
                source.close()
            } finally {
                closed = true
            }
        }
    }

    /**
     * Fills the internal buffer with data from the source. Returns the number of chars added, or -1 if EOF.
     * This method respects any active mark by preserving the buffer contents from the mark position&#8203;:contentReference[oaicite:32]{index=32}.
     */
    private fun fillBuffer(): Int {
        // If mark is not set or mark was invalidated, we can reuse buffer from scratch
        if (markPos < 0) {
            pos = 0
            end = 0
        } else if (pos - markPos >= markLimit) {
            // We have read beyond the allowed mark limit, invalidate mark
            markPos = -1
            markLimit = 0
            pos = 0
            end = 0
        } else if (markPos > 0) {
            // Slide the existing buffer contents back to start to make room (preserve marked section)
            val preserved = end - markPos
            buf.copyInto(destination = buf, destinationOffset = 0, startIndex = markPos, endIndex = end)
            pos -= markPos
            end = preserved
            markPos = 0
        }
        // At this point, pos is at buffer end (for new data), end is amount of preserved data (if any)
        val bytesCapacity = buf.size - end  // space left in char buffer
        if (bytesCapacity <= 0) {
            return 0  // no space (should not happen if mark logic expanded buffer appropriately)
        }
        // Read bytes from source
        val byteBuf = ByteArray(bytesCapacity)
        val bytesRead = source.readAtMost(byteBuf, 0, bytesCapacity)
        if (bytesRead == -1) {
            return -1  // EOF
        }
        // Decode bytes to characters (assuming UTF-8 or the specified charset)
        val chunk = byteBuf.decodeToString(0, bytesRead)  // default decodeToString uses UTF-8&#8203;:contentReference[oaicite:33]{index=33}
        chunk.toCharArray().copyInto(destination = buf, destinationOffset = end)
        val charsAdded = chunk.length
        end += charsAdded
        return charsAdded
    }
}

/* Extension function for Source to read up to a given byte array (kotlinx-io uses readAtMostTo) */
private fun Source.readAtMost(buffer: ByteArray, startIndex: Int, endIndex: Int): Int {
    // Using readAtMostTo from kotlinx-io Source&#8203;:contentReference[oaicite:34]{index=34}.
    return this.readAtMostTo(buffer, startIndex, endIndex)
}
