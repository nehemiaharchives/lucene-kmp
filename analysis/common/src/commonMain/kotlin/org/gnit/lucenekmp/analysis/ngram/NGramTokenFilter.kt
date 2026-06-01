package org.gnit.lucenekmp.analysis.ngram

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.jdkport.Character

/**
 * Tokenizes the input into n-grams of the given size(s). As of Lucene 4.4, this token filter:
 *
 * <ul>
 *   <li>handles supplementary characters correctly,
 *   <li>emits all n-grams for the same token at the same position,
 *   <li>does not modify offsets,
 *   <li>sorts n-grams by their offset in the original token first, then increasing length (meaning
 *       that "abc" will give "a", "ab", "abc", "b", "bc", "c").
 * </ul>
 *
 * <p>If you were using this [TokenFilter] to perform partial highlighting, this won't work anymore
 * since this filter doesn't update offsets. You should modify your analysis chain to use
 * [NGramTokenizer], and potentially override [NGramTokenizer.isTokenChar] to perform
 * pre-tokenization.
 */
class NGramTokenFilter(
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
    private var curPos = 0
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
                curTermCodePointCount = Character.codePointCount(termAtt.buffer(), 0, termAtt.length)
                curPosIncr += posIncrAtt.getPositionIncrement()
                curPos = 0

                if (preserveOriginal && curTermCodePointCount < minGram) {
                    posIncrAtt.setPositionIncrement(curPosIncr)
                    curPosIncr = 0
                    return true
                }

                curTermBuffer = termAtt.buffer().copyOf()
                curGramSize = minGram
            }

            if (curGramSize > maxGram || (curPos + curGramSize) > curTermCodePointCount) {
                ++curPos
                curGramSize = minGram
            }
            if ((curPos + curGramSize) <= curTermCodePointCount) {
                restoreState(state)
                val start = Character.offsetByCodePoints(curTermBuffer!!, 0, curTermLength, 0, curPos)
                val end = Character.offsetByCodePoints(curTermBuffer!!, 0, curTermLength, start, curGramSize)
                termAtt.copyBuffer(curTermBuffer!!, start, end - start)
                posIncrAtt.setPositionIncrement(curPosIncr)
                curPosIncr = 0
                curGramSize++
                return true
            } else if (preserveOriginal && curTermCodePointCount > maxGram) {
                restoreState(state)
                posIncrAtt.setPositionIncrement(0)
                termAtt.copyBuffer(curTermBuffer!!, 0, curTermLength)
                curTermBuffer = null
                return true
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
