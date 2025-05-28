package org.gnit.lucenekmp.analysis

import okio.IOException
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Reader
import kotlin.jvm.JvmOverloads


/**
 * Utility class to write tokenizers or token filters.
 *
 * @lucene.internal
 */
object CharacterUtils {
    /**
     * Creates a new [CharacterBuffer] and allocates a `char[]` of the given
     * bufferSize.
     *
     * @param bufferSize the internal char buffer size, must be `>= 2`
     * @return a new [CharacterBuffer] instance.
     */
    fun newCharacterBuffer(bufferSize: Int): CharacterBuffer {
        require(bufferSize >= 2) { "buffersize must be >= 2" }
        return CharacterBuffer(CharArray(bufferSize), 0, 0)
    }

    /**
     * Converts each unicode codepoint to lowerCase via [Character.toLowerCase] starting at
     * the given offset.
     *
     * @param buffer the char buffer to lowercase
     * @param offset the offset to start at
     * @param limit the max char in the buffer to lower case
     */
    fun toLowerCase(buffer: CharArray, offset: Int, limit: Int) {
        require(buffer.size >= limit)
        require(0 <= offset && offset <= buffer.size)
        var i = offset
        while (i < limit) {
            i +=
                Character.toChars(
                    Character.toLowerCase(Character.codePointAt(buffer, i, limit)),
                    buffer,
                    i
                )
        }
    }

    /**
     * Converts each unicode codepoint to UpperCase via [Character.toUpperCase] starting at
     * the given offset.
     *
     * @param buffer the char buffer to UPPERCASE
     * @param offset the offset to start at
     * @param limit the max char in the buffer to lower case
     */
    fun toUpperCase(buffer: CharArray, offset: Int, limit: Int) {
        require(buffer.size >= limit)
        require(0 <= offset && offset <= buffer.size)
        var i = offset
        while (i < limit) {
            i +=
                Character.toChars(
                    Character.toUpperCase(Character.codePointAt(buffer, i, limit)),
                    buffer,
                    i
                )
        }
    }

    /**
     * Converts a sequence of Java characters to a sequence of unicode code points.
     *
     * @return the number of code points written to the destination buffer
     */
    fun toCodePoints(src: CharArray, srcOff: Int, srcLen: Int, dest: IntArray, destOff: Int): Int {
        require(srcLen >= 0) { "srcLen must be >= 0" }
        var codePointCount = 0
        var i = 0
        while (i < srcLen) {
            val cp: Int = Character.codePointAt(src, srcOff + i, srcOff + srcLen)
            val charCount: Int = Character.charCount(cp)
            dest[destOff + codePointCount++] = cp
            i += charCount
        }
        return codePointCount
    }

    /**
     * Converts a sequence of unicode code points to a sequence of Java characters.
     *
     * @return the number of chars written to the destination buffer
     */
    fun toChars(src: IntArray, srcOff: Int, srcLen: Int, dest: CharArray, destOff: Int): Int {
        require(srcLen >= 0) { "srcLen must be >= 0" }
        var written = 0
        for (i in 0..<srcLen) {
            written += Character.toChars(src[srcOff + i], dest, destOff + written)
        }
        return written
    }

    /**
     * Fills the [CharacterBuffer] with characters read from the given reader [Reader].
     * This method tries to read `numChars` characters into the [CharacterBuffer],
     * each call to fill will start filling the buffer from offset `0` up to `numChars
    ` * . In case code points can span across 2 java characters, this method may only fill
     * `numChars - 1` characters in order not to split in the middle of a surrogate pair,
     * even if there are remaining characters in the [Reader].
     *
     *
     * This method guarantees that the given [CharacterBuffer] will never contain a high
     * surrogate character as the last element in the buffer unless it is the last available character
     * in the reader. In other words, high and low surrogate pairs will always be preserved across
     * buffer boarders.
     *
     *
     * A return value of `false` means that this method call exhausted the reader, but
     * there may be some bytes which have been read, which can be verified by checking whether `
     * buffer.getLength() > 0`.
     *
     * @param buffer the buffer to fill.
     * @param reader the reader to read characters from.
     * @param numChars the number of chars to read
     * @return `false` if and only if reader.read returned -1 while trying to fill the
     * buffer
     * @throws IOException if the reader throws an [IOException].
     */
    /** Convenience method which calls `fill(buffer, reader, buffer.buffer.length)`.  */
    @JvmOverloads
    @Throws(IOException::class)
    fun fill(buffer: CharacterBuffer, reader: Reader, numChars: Int = buffer.buffer.size): Boolean {
        require(buffer.buffer.size >= 2)
        require(!(numChars < 2 || numChars > buffer.buffer.size)) { "numChars must be >= 2 and <= the buffer size" }
        val charBuffer = buffer.buffer
        buffer.offset = 0
        val offset: Int

        // Install the previously saved ending high surrogate:
        if (buffer.lastTrailingHighSurrogate.code != 0) {
            charBuffer[0] = buffer.lastTrailingHighSurrogate
            buffer.lastTrailingHighSurrogate = 0.toChar()
            offset = 1
        } else {
            offset = 0
        }

        val read = readFully(reader, charBuffer, offset, numChars - offset)

        buffer.length = offset + read
        val result = buffer.length == numChars
        if (buffer.length < numChars) {
            // We failed to fill the buffer. Even if the last char is a high
            // surrogate, there is nothing we can do
            return result
        }

        if (Character.isHighSurrogate(charBuffer[buffer.length - 1])) {
            buffer.lastTrailingHighSurrogate = charBuffer[--buffer.length]
        }
        return result
    }

    @Throws(IOException::class)
    fun readFully(reader: Reader, dest: CharArray, offset: Int, len: Int): Int {
        var read = 0
        while (read < len) {
            val r: Int = reader.read(dest, offset + read, len - read)
            if (r == -1) {
                break
            }
            read += r
        }
        return read
    }

    /** A simple IO buffer to use with [CharacterUtils.fill].  */
    class CharacterBuffer internal constructor(
        /**
         * Returns the internal buffer
         *
         * @return the buffer
         */
        val buffer: CharArray,
        /**
         * Returns the data offset in the internal buffer.
         *
         * @return the offset
         */
        var offset: Int,
        /**
         * Return the length of the data in the internal buffer starting at [.getOffset]
         *
         * @return the length
         */
        var length: Int
    ) {
        // NOTE: not private so outer class can access without
        // $access methods:
        var lastTrailingHighSurrogate: Char = 0.toChar()

        /** Resets the CharacterBuffer. All internals are reset to its default values.  */
        fun reset() {
            offset = 0
            length = 0
            lastTrailingHighSurrogate = 0.toChar()
        }
    }
}
