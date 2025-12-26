package org.gnit.lucenekmp.analysis.util

import okio.IOException
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert

/**
 * Acts like a forever growing char[] as you read characters into it from the provided reader, but
 * internally it uses a circular buffer to only hold the characters that haven't been freed yet.
 * This is like a PushbackReader, except you don't have to specify up-front the max size of the
 * buffer, but you do have to periodically call [freeBefore].
 */
class RollingCharBuffer {

    private var reader: Reader? = null

    private var buffer = CharArray(512)

    // Next array index to write to in buffer:
    private var nextWrite = 0

    // Next absolute position to read from reader:
    private var nextPos = 0

    // How many valid chars (wrapped) are in the buffer:
    private var count = 0

    // True if we hit EOF
    private var end = false

    /** Clear array and switch to new reader. */
    fun reset(reader: Reader) {
        this.reader = reader
        nextPos = 0
        nextWrite = 0
        count = 0
        end = false
    }

    /**
     * Absolute position read. NOTE: pos must not jump ahead by more than 1! Ie, it's OK to read
     * arbitrarily far back (just not prior to the last [freeBefore]), but NOT ok to read
     * arbitrarily far ahead. Returns -1 if you hit EOF.
     */
    @Throws(IOException::class)
    fun get(pos: Int): Int {
        if (pos == nextPos) {
            if (end) {
                return -1
            }
            if (count == buffer.size) {
                // Grow
                val newBuffer = CharArray(ArrayUtil.oversize(1 + count, Character.BYTES))
                System.arraycopy(buffer, nextWrite, newBuffer, 0, buffer.size - nextWrite)
                System.arraycopy(buffer, 0, newBuffer, buffer.size - nextWrite, nextWrite)
                nextWrite = buffer.size
                buffer = newBuffer
            }
            if (nextWrite == buffer.size) {
                nextWrite = 0
            }

            val toRead = buffer.size - kotlin.math.max(count, nextWrite)
            val readCount = reader!!.read(buffer, nextWrite, toRead)
            if (readCount == -1) {
                end = true
                return -1
            }
            val ch = buffer[nextWrite].code
            nextWrite += readCount
            count += readCount
            nextPos += readCount
            return ch
        } else {
            // Cannot read from future (except by 1):
            assert(pos < nextPos)

            // Cannot read from already freed past:
            assert(nextPos - pos <= count) { "nextPos=$nextPos pos=$pos count=$count" }

            return buffer[getIndex(pos)].code
        }
    }

    // For assert:
    private fun inBounds(pos: Int): Boolean {
        return pos >= 0 && pos < nextPos && pos >= nextPos - count
    }

    private fun getIndex(pos: Int): Int {
        var index = nextWrite - (nextPos - pos)
        if (index < 0) {
            // Wrap:
            index += buffer.size
            assert(index >= 0)
        }
        return index
    }

    fun get(posStart: Int, length: Int): CharArray {
        assert(length > 0)
        assert(inBounds(posStart)) { "posStart=$posStart length=$length" }

        val startIndex = getIndex(posStart)
        val endIndex = getIndex(posStart + length)

        val result = CharArray(length)
        if (endIndex >= startIndex && length < buffer.size) {
            System.arraycopy(buffer, startIndex, result, 0, endIndex - startIndex)
        } else {
            // Wrapped:
            val part1 = buffer.size - startIndex
            System.arraycopy(buffer, startIndex, result, 0, part1)
            System.arraycopy(buffer, 0, result, buffer.size - startIndex, length - part1)
        }
        return result
    }

    /** Call this to notify us that no chars before this absolute position are needed anymore. */
    fun freeBefore(pos: Int) {
        assert(pos >= 0)
        assert(pos <= nextPos)
        val newCount = nextPos - pos
        assert(newCount <= count) { "newCount=$newCount count=$count" }
        assert(newCount <= buffer.size) { "newCount=$newCount buf.length=${buffer.size}" }
        count = newCount
    }
}
