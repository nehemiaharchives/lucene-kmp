package org.gnit.lucenekmp.analysis.shingle

import okio.IOException
import org.gnit.lucenekmp.analysis.GraphTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute

/**
 * A FixedShingleFilter constructs shingles (token n-grams) from a token stream. In other words, it
 * creates combinations of tokens as a single token.
 *
 * <p>Unlike the [ShingleFilter], FixedShingleFilter only emits shingles of a fixed size, and
 * never emits unigrams, even at the end of a TokenStream. In addition, if the filter encounters
 * stacked tokens (eg synonyms), then it will output stacked shingles
 *
 * <p>For example, the sentence "please divide this sentence into shingles" might be tokenized into
 * shingles "please divide", "divide this", "this sentence", "sentence into", and "into shingles".
 *
 * <p>This filter handles position increments > 1 by inserting filler tokens (tokens with
 * termtext "_").
 */
class FixedShingleFilter : GraphTokenFilter {
    companion object {
        private const val MAX_SHINGLE_SIZE = 4
    }

    private val shingleSize: Int
    private val tokenSeparator: String
    private val fillerToken: String

    private val incAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)

    private val buffer: CharTermAttribute = CharTermAttributeImpl()

    /**
     * Creates a FixedShingleFilter over an input token stream
     *
     * @param input the input stream
     * @param shingleSize the shingle size
     */
    constructor(input: TokenStream, shingleSize: Int) : this(input, shingleSize, " ", "_")

    /**
     * Creates a FixedShingleFilter over an input token stream
     *
     * @param input the input tokenstream
     * @param shingleSize the shingle size
     * @param tokenSeparator a String to use as a token separator
     * @param fillerToken a String to use to represent gaps in the input stream (due to eg stopwords)
     */
    constructor(input: TokenStream, shingleSize: Int, tokenSeparator: String, fillerToken: String) : super(input) {
        require(shingleSize > 1 && shingleSize <= MAX_SHINGLE_SIZE) {
            "Shingle size must be between 2 and $MAX_SHINGLE_SIZE, got $shingleSize"
        }
        this.shingleSize = shingleSize
        this.tokenSeparator = tokenSeparator
        this.fillerToken = fillerToken
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        var shinglePosInc: Int
        var startOffset: Int
        var endOffset: Int

        outer@ while (true) {
            if (!incrementGraph()) {
                if (!incrementBaseToken()) {
                    return false
                }
                shinglePosInc = incAtt.getPositionIncrement()
            } else {
                shinglePosInc = 0
            }

            startOffset = offsetAtt.startOffset()
            endOffset = offsetAtt.endOffset()
            buffer.setEmpty()
            buffer.append(termAtt)

            var i = 1
            while (i < shingleSize) {
                if (!incrementGraphToken()) {
                    val trailingPositions = getTrailingPositions()
                    if (i + trailingPositions < shingleSize) {
                        continue@outer
                    }
                    while (i < shingleSize) {
                        buffer.append(tokenSeparator).append(fillerToken)
                        i++
                    }
                    break
                }
                var posInc = incAtt.getPositionIncrement()
                if (posInc > 1) {
                    if (i + posInc > shingleSize) {
                        while (i < shingleSize) {
                            buffer.append(tokenSeparator).append(fillerToken)
                            i++
                        }
                        break
                    }
                    while (posInc > 1) {
                        buffer.append(tokenSeparator).append(fillerToken)
                        posInc--
                        i++
                    }
                }
                buffer.append(tokenSeparator).append(termAtt)
                endOffset = offsetAtt.endOffset()
                i++
            }
            break
        }
        clearAttributes()
        offsetAtt.setOffset(startOffset, endOffset)
        incAtt.setPositionIncrement(shinglePosInc)
        termAtt.setEmpty()!!.append(buffer)
        typeAtt.setType("shingle")
        return true
    }
}
