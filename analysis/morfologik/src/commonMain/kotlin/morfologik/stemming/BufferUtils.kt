package morfologik.stemming

import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.CharBuffer
import org.gnit.lucenekmp.jdkport.CharacterCodingException
import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.CharsetDecoder
import org.gnit.lucenekmp.jdkport.CharsetEncoder
import org.gnit.lucenekmp.jdkport.CoderResult
import org.gnit.lucenekmp.jdkport.CodingErrorAction
import org.gnit.lucenekmp.jdkport.fromByteArray

object BufferUtils {
    /**
     * Ensure the buffer's capacity is large enough to hold a given number
     * of elements. If the input buffer is not large enough, a new buffer is allocated
     * and returned.
     */
    fun clearAndEnsureCapacity(buffer: ByteBuffer?, elements: Int): ByteBuffer {
        return if (buffer == null || buffer.capacity < elements) {
            ByteBuffer.allocate(elements)
        } else {
            buffer.clear()
            buffer
        }
    }

    /**
     * Ensure the buffer's capacity is large enough to hold a given number
     * of elements. If the input buffer is not large enough, a new buffer is allocated
     * and returned.
     */
    fun clearAndEnsureCapacity(buffer: CharBuffer?, elements: Int): CharBuffer {
        return if (buffer == null || buffer.capacity < elements) {
            CharBuffer.allocate(elements)
        } else {
            buffer.clear()
            buffer
        }
    }

    /**
     * @param buffer The buffer to convert to a string.
     * @param charset The charset to use when converting bytes to characters.
     * @return A string representation of buffer's content.
     */
    fun toString(buffer: ByteBuffer, charset: Charset): String {
        var slice = buffer.slice()
        val buf = ByteArray(slice.remaining())
        slice.get(buf)
        return String.fromByteArray(buf, charset)
    }

    fun toString(buffer: CharBuffer): String {
        var slice = buffer.slice()
        val buf = CharArray(slice.remaining())
        slice.get(buf, 0, buf.size)
        return buf.concatToString()
    }

    /**
     * @param buffer The buffer to read from.
     * @return Returns the remaining bytes from the buffer copied to an array.
     */
    fun toArray(buffer: ByteBuffer): ByteArray {
        val dst = ByteArray(buffer.remaining())
        buffer.mark()
        buffer.get(dst, 0, dst.size)
        buffer.reset()
        return dst
    }

    /**
     * Compute the length of the shared prefix between two byte sequences.
     */
    fun sharedPrefixLength(a: ByteBuffer, aStart: Int, b: ByteBuffer, bStart: Int): Int {
        var i = 0
        val max = kotlin.math.min(a.remaining() - aStart, b.remaining() - bStart)
        var aIndex = aStart + a.position
        var bIndex = bStart + b.position
        while (i < max && a.get(aIndex++) == b.get(bIndex++)) {
            i++
        }
        return i
    }

    /**
     * Compute the length of the shared prefix between two byte sequences.
     */
    fun sharedPrefixLength(a: ByteBuffer, b: ByteBuffer): Int {
        return sharedPrefixLength(a, 0, b, 0)
    }

    /**
     * Convert byte buffer's content into characters. The input buffer's bytes are not
     * consumed (mark is set and reset).
     */
    fun bytesToChars(decoder: CharsetDecoder, bytes: ByteBuffer, chars: CharBuffer?): CharBuffer {
        require(decoder.malformedInputAction() == CodingErrorAction.REPORT)

        var target = clearAndEnsureCapacity(chars, (bytes.remaining() * decoder.maxCharsPerByte()).toInt())

        bytes.mark()
        decoder.reset()
        var cr: CoderResult = decoder.decode(bytes, target, true)
        if (cr.isError) {
            bytes.reset()
            try {
                cr.throwException()
            } catch (e: CharacterCodingException) {
                throw RuntimeException(
                    "Input cannot be mapped to bytes using encoding ${decoder.charset().name()}: ${toArray(bytes).contentToString()}",
                    e
                )
            }
        }

        require(cr.isUnderflow)
        cr = decoder.flush(target)
        require(cr.isUnderflow)

        target.flip()
        bytes.reset()

        return target
    }

    /**
     * Convert chars into bytes.
     */
    @Throws(UnmappableInputException::class)
    fun charsToBytes(encoder: CharsetEncoder, chars: CharBuffer, bytes: ByteBuffer?): ByteBuffer {
        require(encoder.malformedInputAction() == CodingErrorAction.REPORT)

        var target = clearAndEnsureCapacity(bytes, (chars.remaining() * encoder.maxBytesPerChar()).toInt())

        chars.mark()
        encoder.reset()

        var cr: CoderResult = encoder.encode(chars, target, true)
        if (cr.isError) {
            chars.reset()
            try {
                cr.throwException()
            } catch (e: CharacterCodingException) {
                throw UnmappableInputException(
                    "Input cannot be mapped to characters using encoding ${encoder.charset().name()}: ${toArray(target).contentToString()}",
                    e
                )
            }
        }

        require(cr.isUnderflow)
        cr = encoder.flush(target)
        require(cr.isUnderflow)

        target.flip()
        chars.reset()

        return target
    }
}
