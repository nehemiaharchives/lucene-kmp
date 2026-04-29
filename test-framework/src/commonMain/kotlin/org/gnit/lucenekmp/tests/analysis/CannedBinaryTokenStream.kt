package org.gnit.lucenekmp.tests.analysis

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.BytesTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.BytesRef

class CannedBinaryTokenStream : TokenStream {
    /** Represents a binary token.  */
    class BinaryToken {
        var term: BytesRef
        var posInc: Int
        var posLen: Int
        var startOffset: Int = 0
        var endOffset: Int = 0

        constructor(term: BytesRef) {
            this.term = term
            this.posInc = 1
            this.posLen = 1
        }

        constructor(term: BytesRef, posInc: Int, posLen: Int) {
            this.term = term
            this.posInc = posInc
            this.posLen = posLen
        }
    }

    private val tokens: Array<out BinaryToken>
    private var upto = 0
    private val termAtt: BytesTermAttribute = addAttribute(BytesTermAttribute::class)
    private val posIncrAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val posLengthAtt: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

    constructor(vararg tokens: BinaryToken) : super(Token.TOKEN_ATTRIBUTE_FACTORY) {
        this.tokens = tokens
        assert(termAtt === getAttribute(TermToBytesRefAttribute::class))
    }

    override fun incrementToken(): Boolean {
        if (upto < tokens.size) {
            val token = tokens[upto++]
            // TODO: can we just capture/restoreState so
            // we get all attrs...
            clearAttributes()
            termAtt.setBytesRef(token.term)
            posIncrAtt.setPositionIncrement(token.posInc)
            posLengthAtt.positionLength = token.posLen
            offsetAtt.setOffset(token.startOffset, token.endOffset)
            return true
        } else {
            return false
        }
    }
}
