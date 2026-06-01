package org.gnit.lucenekmp.analysis.ngram

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.jdkport.Character

/**
 * Tokenizes the given token into n-grams of given size(s).
 *
 * <p>This [TokenFilter] create n-grams from the beginning edge of a input token.
 *
 * <p><a id="match_version"></a>As of Lucene 4.4, this filter handles correctly supplementary
 * characters.
 */
class EdgeNGramTokenFilter(
    input: TokenStream,
    private val minGram: Int,
    private val maxGram: Int,
    private val preserveOriginal: Boolean,
) : TokenFilter(input) {
    companion object {
        const val DEFAULT_PRESERVE_ORIGINAL = false
    }

    private var curTermBuffer: CharArray? = null
    private var curTermLength = 0
    private var curTermCodePointCount = 0
    private var curGramSize = 0
    private var curPosIncr = 0
    private var state: State? = null

    private val termAtt = addAttribute(CharTermAttribute::class)
    private val posIncrAtt = addAttribute(PositionIncrementAttribute::class)

    constructor(input: TokenStream, gramSize: Int) : this(input, gramSize, gramSize, DEFAULT_PRESERVE_ORIGINAL)

    init {
        require(minGram >= 1) { "minGram must be greater than zero" }
        require(minGram <= maxGram) { "minGram must not be greater than maxGram" }
    }

    @Throws(IOException::class)
    final override fun incrementToken(): Boolean {
        while (true) {
            if (curTermBuffer == null) {
                if (!input.incrementToken()) {
                    return false
                }
                state = captureState()

                curTermLength = termAtt.length
                curTermCodePointCount = Character.codePointCount(termAtt.buffer(), 0, curTermLength)
                curPosIncr += posIncrAtt.getPositionIncrement()

                if (preserveOriginal && curTermCodePointCount < minGram) {
                    posIncrAtt.setPositionIncrement(curPosIncr)
                    curPosIncr = 0
                    return true
                }

                curTermBuffer = termAtt.buffer().copyOf()
                curGramSize = minGram
            }

            if (curGramSize <= curTermCodePointCount) {
                if (curGramSize <= maxGram) {
                    restoreState(state)
                    posIncrAtt.setPositionIncrement(curPosIncr)
                    curPosIncr = 0

                    val charLength = Character.offsetByCodePoints(curTermBuffer!!, 0, curTermLength, 0, curGramSize)
                    termAtt.copyBuffer(curTermBuffer!!, 0, charLength)
                    curGramSize++
                    return true
                } else if (preserveOriginal) {
                    restoreState(state)
                    posIncrAtt.setPositionIncrement(0)
                    termAtt.copyBuffer(curTermBuffer!!, 0, curTermLength)
                    curTermBuffer = null
                    return true
                }
            }
            curTermBuffer = null
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        curTermBuffer = null
        curPosIncr = 0
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        posIncrAtt.setPositionIncrement(curPosIncr)
    }
}
