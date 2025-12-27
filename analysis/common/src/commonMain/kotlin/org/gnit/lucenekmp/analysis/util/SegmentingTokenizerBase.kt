package org.gnit.lucenekmp.analysis.util

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.jdkport.BreakIterator
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * Breaks text into sentences with a [BreakIterator] and allows subclasses to decompose these
 * sentences into words.
 *
 * @lucene.experimental
 */
abstract class SegmentingTokenizerBase : Tokenizer {
    protected val buffer: CharArray = CharArray(BUFFERMAX)

    /** true length of text in the buffer */
    private var length: Int = 0

    /** length in buffer that can be evaluated safely, up to a safe end point */
    private var usableLength: Int = 0

    /** accumulated offset of previous buffers for this reader, for offsetAtt */
    protected var offset: Int = 0

    private val iterator: BreakIterator
    private val wrapper: CharArrayIterator = CharArrayIterator.newSentenceInstance()

    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

    protected constructor(iterator: BreakIterator) : this(TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY, iterator)

    protected constructor(factory: AttributeFactory, iterator: BreakIterator) : super(factory) {
        this.iterator = iterator
    }

    @Throws(IOException::class)
    final override fun incrementToken(): Boolean {
        if (length == 0 || !incrementWord()) {
            while (!incrementSentence()) {
                refill()
                if (length <= 0) {
                    return false
                }
            }
        }
        return true
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        wrapper.setText(buffer, 0, 0)
        iterator.setText(wrapper)
        length = 0
        usableLength = 0
        offset = 0
    }

    @Throws(IOException::class)
    final override fun end() {
        super.end()
        val finalOffset = correctOffset(if (length < 0) offset else offset + length)
        offsetAtt.setOffset(finalOffset, finalOffset)
    }

    /** Returns the last unambiguous break position in the text. */
    private fun findSafeEnd(): Int {
        for (i in length - 1 downTo 0) {
            if (isSafeEnd(buffer[i])) {
                return i + 1
            }
        }
        return -1
    }

    /** For sentence tokenization, these are the unambiguous break positions. */
    protected open fun isSafeEnd(ch: Char): Boolean {
        return when (ch.code) {
            0x000D, 0x000A, 0x0085, 0x2028, 0x2029 -> true
            else -> false
        }
    }

    /**
     * Refill the buffer, accumulating the offset and setting usableLength to the last unambiguous
     * break position.
     */
    @Throws(IOException::class)
    private fun refill() {
        offset += usableLength
        val leftover = length - usableLength
        if (leftover > 0) {
            buffer.copyInto(buffer, 0, usableLength, length)
        }
        val requested = buffer.size - leftover
        val returned = read(input, buffer, leftover, requested)
        length = if (returned < 0) leftover else returned + leftover
        usableLength = if (returned < requested) {
            length
        } else {
            val safeEnd = findSafeEnd()
            if (safeEnd < 0) length else safeEnd
        }

        wrapper.setText(buffer, 0, kotlin.math.max(0, usableLength))
        iterator.setText(wrapper)
    }

    @Throws(IOException::class)
    private fun incrementSentence(): Boolean {
        if (length == 0) {
            return false
        }

        while (true) {
            val start = iterator.current()
            if (start == BreakIterator.DONE) {
                return false
            }

            val end = iterator.next()
            if (end == BreakIterator.DONE) {
                return false
            }

            setNextSentence(start, end)
            if (incrementWord()) {
                return true
            }
        }
    }

    /** Provides the next input sentence for analysis. */
    protected abstract fun setNextSentence(sentenceStart: Int, sentenceEnd: Int)

    /** Returns true if another word is available. */
    @Throws(IOException::class)
    protected abstract fun incrementWord(): Boolean

    companion object {
        protected const val BUFFERMAX: Int = 1024

        @Throws(IOException::class)
        private fun read(input: Reader, buffer: CharArray, offset: Int, length: Int): Int {
            require(length >= 0) { "length must not be negative: $length" }

            var remaining = length
            while (remaining > 0) {
                val location = length - remaining
                val count = input.read(buffer, offset + location, remaining)
                if (count == -1) {
                    break
                }
                remaining -= count
            }
            return length - remaining
        }
    }
}
