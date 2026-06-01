package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.util.AttributeSource

/**
 * Filter outputs a single token which is a concatenation of the sorted and de-duplicated set of
 * input tokens. This can be useful for clustering/linking use cases.
 */
class FingerprintFilter : TokenFilter {
    companion object {
        const val DEFAULT_MAX_OUTPUT_TOKEN_SIZE = 1024
        const val DEFAULT_SEPARATOR = ' '
    }

    private val termAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAtt = addAttribute(OffsetAttribute::class)
    private val posIncrAtt = addAttribute(PositionIncrementAttribute::class)
    private val posLenAtt = addAttribute(PositionLengthAttribute::class)
    private val typeAtt = addAttribute(TypeAttribute::class)

    private var uniqueTerms: CharArraySet? = null
    private val maxOutputTokenSize: Int
    private var finalState: AttributeSource.State? = null

    private val separator: Char
    private var inputEnded = false

    /** Create a new FingerprintFilter with default settings */
    constructor(input: TokenStream) : this(input, DEFAULT_MAX_OUTPUT_TOKEN_SIZE, DEFAULT_SEPARATOR)

    /**
     * Create a new FingerprintFilter with control over all settings
     *
     * @param input the source of tokens to be summarized into a single token
     * @param maxOutputTokenSize the maximum length of the summarized output token. If exceeded, no
     * output token is emitted
     * @param separator the character used to separate tokens combined into the single output token
     */
    constructor(input: TokenStream, maxOutputTokenSize: Int, separator: Char) : super(input) {
        this.maxOutputTokenSize = maxOutputTokenSize
        this.separator = separator
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (inputEnded) {
            return false
        }
        val result = buildSingleOutputToken()
        finalState = captureState()
        return result
    }

    /**
     * Gathers all tokens from input, de-duplicates, sorts then concatenates.
     *
     * @return false for end of stream; true otherwise
     */
    @Throws(IOException::class)
    private fun buildSingleOutputToken(): Boolean {
        inputEnded = false

        var clonedLastTerm: CharArray? = null
        uniqueTerms = CharArraySet(8, false)
        var outputTokenSize = 0
        while (input.incrementToken()) {
            if (outputTokenSize > maxOutputTokenSize) {
                continue
            }

            val term = termAttribute.buffer()
            val length = termAttribute.length

            if (!uniqueTerms!!.contains(term, 0, length)) {
                clonedLastTerm = CharArray(length)
                term.copyInto(clonedLastTerm, 0, 0, length)
                if (uniqueTerms!!.size > 0) {
                    outputTokenSize++
                }
                uniqueTerms!!.add(clonedLastTerm)
                outputTokenSize += length
            }
        }
        input.end()
        inputEnded = true

        offsetAtt.setOffset(0, offsetAtt.endOffset())
        posLenAtt.positionLength = 1
        posIncrAtt.setPositionIncrement(1)
        typeAtt.setType("fingerprint")

        if (uniqueTerms!!.size < 1) {
            termAttribute.setEmpty()
            return false
        }

        if (outputTokenSize > maxOutputTokenSize) {
            termAttribute.setEmpty()
            uniqueTerms!!.clear()
            return false
        }

        if (uniqueTerms!!.size == 1) {
            termAttribute.setEmpty()!!.append(String.fromCharArray(clonedLastTerm!!))
            uniqueTerms!!.clear()
            return true
        }

        val items = mutableListOf<CharArray>()
        for (item in uniqueTerms!!) {
            items.add(item as CharArray)
        }
        items.sortWith { v1, v2 ->
            val len1 = v1.size
            val len2 = v2.size
            val lim = minOf(len1, len2)

            var k = 0
            while (k < lim) {
                val c1 = v1[k]
                val c2 = v2[k]
                if (c1 != c2) {
                    return@sortWith c1 - c2
                }
                k++
            }
            len1 - len2
        }

        val sb = StringBuilder()
        for (item in items) {
            if (sb.isNotEmpty()) {
                sb.append(separator)
            }
            sb.append(item)
        }
        termAttribute.setEmpty()!!.append(sb)
        uniqueTerms!!.clear()
        return true
    }

    @Throws(IOException::class)
    override fun end() {
        if (!inputEnded) {
            input.end()
            inputEnded = true
        }

        if (finalState != null) {
            restoreState(finalState)
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        inputEnded = false
        uniqueTerms = null
        finalState = null
    }
}
