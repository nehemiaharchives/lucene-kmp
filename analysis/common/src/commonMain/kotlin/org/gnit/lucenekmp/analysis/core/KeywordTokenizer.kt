package org.gnit.lucenekmp.analysis.core

import okio.IOException
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.util.AttributeFactory

/** Emits the entire input as a single token.  */
class KeywordTokenizer : Tokenizer {
    private var done = false
    private var finalOffset = 0
    private val termAtt: CharTermAttribute =
        addAttribute(CharTermAttribute::class)
    private val offsetAtt: OffsetAttribute =
        addAttribute(OffsetAttribute::class)

    constructor(bufferSize: Int = DEFAULT_BUFFER_SIZE) {
        require(!(bufferSize > StandardTokenizer.MAX_TOKEN_LENGTH_LIMIT || bufferSize <= 0)) {
            ("maxTokenLen must be greater than 0 and less than "
                    + StandardTokenizer.MAX_TOKEN_LENGTH_LIMIT
                    + " passed: "
                    + bufferSize)
        }
        termAtt.resizeBuffer(bufferSize)
    }

    constructor(
        factory: AttributeFactory,
        bufferSize: Int
    ) : super(factory) {
        require(!(bufferSize > StandardTokenizer.MAX_TOKEN_LENGTH_LIMIT || bufferSize <= 0)) {
            ("maxTokenLen must be greater than 0 and less than "
                    + StandardTokenizer.MAX_TOKEN_LENGTH_LIMIT
                    + " passed: "
                    + bufferSize)
        }
        termAtt.resizeBuffer(bufferSize)
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!done) {
            clearAttributes()
            done = true
            var upto = 0
            var buffer: CharArray = termAtt.buffer()
            while (true) {
                val length: Int = input.read(buffer, upto, buffer.size - upto)
                if (length == -1) break
                upto += length
                if (upto == buffer.size) buffer = termAtt.resizeBuffer(1 + buffer.size)
            }
            termAtt.setLength(upto)
            finalOffset = correctOffset(upto)
            offsetAtt.setOffset(correctOffset(0), finalOffset)
            return true
        }
        return false
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        // set final offset
        offsetAtt.setOffset(finalOffset, finalOffset)
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        this.done = false
    }

    companion object {
        /** Default read buffer size  */
        const val DEFAULT_BUFFER_SIZE: Int = 256
    }
}
