package org.gnit.lucenekmp.analysis.ngram

import okio.IOException
import org.gnit.lucenekmp.analysis.CharacterUtils
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * Tokenizes the input into n-grams of the given size(s).
 *
 * <p>On the contrary to [NGramTokenFilter], this class sets offsets so that characters between
 * startOffset and endOffset in the original stream are the same as the term chars.
 *
 * <p>For example, "abcde" would be tokenized as (minGram=2, maxGram=3):
 *
 * <table>
 * <caption>ngram tokens example</caption>
 * <tr><th>Term</th><td>ab</td><td>abc</td><td>bc</td><td>bcd</td><td>cd</td><td>cde</td><td>de</td></tr>
 * <tr><th>Position increment</th><td>1</td><td>1</td><td>1</td><td>1</td><td>1</td><td>1</td><td>1</td></tr>
 * <tr><th>Position length</th><td>1</td><td>1</td><td>1</td><td>1</td><td>1</td><td>1</td><td>1</td></tr>
 * <tr><th>Offsets</th><td>[0,2[</td><td>[0,3[</td><td>[1,3[</td><td>[1,4[</td><td>[2,4[</td><td>[2,5[</td><td>[3,5[</td></tr>
 * </table>
 *
 * <a id="version"></a>
 *
 * <p>This tokenizer changed a lot in Lucene 4.4 in order to:
 *
 * <ul>
 *   <li>tokenize in a streaming fashion to support streams which are larger than 1024 chars (limit
 *       of the previous version),
 *   <li>count grams based on unicode code points instead of java chars (and never split in the
 *       middle of surrogate pairs),
 *   <li>give the ability to [isTokenChar] pre-tokenize the stream before computing
 *       n-grams.
 * </ul>
 *
 * <p>Additionally, this class doesn't trim trailing whitespaces and emits tokens in a different
 * order, tokens are now emitted by increasing start offsets while they used to be emitted by
 * increasing lengths (which prevented from supporting large input streams).
 */
open class NGramTokenizer : Tokenizer {
    companion object {
        const val DEFAULT_MIN_NGRAM_SIZE = 1
        const val DEFAULT_MAX_NGRAM_SIZE = 2
    }

    private lateinit var charBuffer: CharacterUtils.CharacterBuffer
    private lateinit var buffer: IntArray
    private var bufferStart = 0
    private var bufferEnd = 0
    private var offset = 0
    private var gramSize = 0
    private var minGram = 0
    private var maxGram = 0
    private var exhausted = false
    private var lastCheckedChar = 0
    private var lastNonTokenChar = 0
    private var edgesOnly = false

    private val termAtt = addAttribute(CharTermAttribute::class)
    private val posIncAtt = addAttribute(PositionIncrementAttribute::class)
    private val posLenAtt = addAttribute(PositionLengthAttribute::class)
    private val offsetAtt = addAttribute(OffsetAttribute::class)

    internal constructor(minGram: Int, maxGram: Int, edgesOnly: Boolean) : super() {
        init(minGram, maxGram, edgesOnly)
    }

    constructor(minGram: Int, maxGram: Int) : this(minGram, maxGram, false)

    internal constructor(factory: AttributeFactory, minGram: Int, maxGram: Int, edgesOnly: Boolean) : super(factory) {
        init(minGram, maxGram, edgesOnly)
    }

    constructor(factory: AttributeFactory, minGram: Int, maxGram: Int) : this(factory, minGram, maxGram, false)

    constructor() : this(DEFAULT_MIN_NGRAM_SIZE, DEFAULT_MAX_NGRAM_SIZE)

    private fun init(minGram: Int, maxGram: Int, edgesOnly: Boolean) {
        require(minGram >= 1) { "minGram must be greater than zero" }
        require(minGram <= maxGram) { "minGram must not be greater than maxGram" }
        this.minGram = minGram
        this.maxGram = maxGram
        this.edgesOnly = edgesOnly
        charBuffer = CharacterUtils.newCharacterBuffer(2 * maxGram + 1024)
        buffer = IntArray(charBuffer.buffer.size)
        termAtt.resizeBuffer(2 * maxGram)
    }

    @Throws(IOException::class)
    final override fun incrementToken(): Boolean {
        clearAttributes()

        while (true) {
            if (bufferStart >= bufferEnd - maxGram - 1 && !exhausted) {
                buffer.copyInto(buffer, 0, bufferStart, bufferEnd)
                bufferEnd -= bufferStart
                lastCheckedChar -= bufferStart
                lastNonTokenChar -= bufferStart
                bufferStart = 0

                exhausted = !CharacterUtils.fill(charBuffer, input, buffer.size - bufferEnd)
                bufferEnd += CharacterUtils.toCodePoints(charBuffer.buffer, 0, charBuffer.length, buffer, bufferEnd)
            }

            if (gramSize > maxGram || bufferStart + gramSize > bufferEnd) {
                if (bufferStart + 1 + minGram > bufferEnd) {
                    return false
                }
                consume()
                gramSize = minGram
            }

            updateLastNonTokenChar()

            val termContainsNonTokenChar =
                lastNonTokenChar >= bufferStart && lastNonTokenChar < (bufferStart + gramSize)
            val isEdgeAndPreviousCharIsTokenChar =
                edgesOnly && lastNonTokenChar != bufferStart - 1
            if (termContainsNonTokenChar || isEdgeAndPreviousCharIsTokenChar) {
                consume()
                gramSize = minGram
                continue
            }

            val length = CharacterUtils.toChars(buffer, bufferStart, gramSize, termAtt.buffer(), 0)
            termAtt.setLength(length)
            posIncAtt.setPositionIncrement(1)
            posLenAtt.positionLength = 1
            offsetAtt.setOffset(correctOffset(offset), correctOffset(offset + length))
            ++gramSize
            return true
        }
    }

    private fun updateLastNonTokenChar() {
        val termEnd = bufferStart + gramSize - 1
        if (termEnd > lastCheckedChar) {
            for (i in termEnd downTo lastCheckedChar + 1) {
                if (!isTokenChar(buffer[i])) {
                    lastNonTokenChar = i
                    break
                }
            }
            lastCheckedChar = termEnd
        }
    }

    /** Consume one code point. */
    private fun consume() {
        offset += Character.charCount(buffer[bufferStart++])
    }

    /** Only collect characters which satisfy this condition. */
    protected open fun isTokenChar(chr: Int): Boolean {
        return true
    }

    @Throws(IOException::class)
    final override fun end() {
        super.end()
        var endOffset = offset
        for (i in bufferStart until bufferEnd) {
            endOffset += Character.charCount(buffer[i])
        }
        endOffset = correctOffset(endOffset)
        offsetAtt.setOffset(endOffset, endOffset)
    }

    @Throws(IOException::class)
    final override fun reset() {
        super.reset()
        bufferStart = buffer.size
        bufferEnd = buffer.size
        lastNonTokenChar = bufferStart - 1
        lastCheckedChar = bufferStart - 1
        offset = 0
        gramSize = minGram
        exhausted = false
        charBuffer.reset()
    }
}
