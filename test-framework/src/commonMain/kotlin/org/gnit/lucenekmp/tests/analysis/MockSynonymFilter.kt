package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.util.AttributeSource

/** adds synonym of "dog" for "dogs", and synonym of "cavy" for "guinea pig". */
class MockSynonymFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val posLenAtt: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)
    private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)
    private val tokenQueue: MutableList<AttributeSource> = ArrayList()
    private var endOfInput = false

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        tokenQueue.clear()
        endOfInput = false
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (tokenQueue.size > 0) {
            tokenQueue.removeAt(0).copyTo(this)
            return true
        }
        if (!endOfInput && input.incrementToken()) {
            val term = termAtt!!.toString()
            if (term == "dogs") {
                addSynonymAndRestoreOrigToken("dog", 1, offsetAtt.endOffset())
            } else if (term == "guinea") {
                val firstSavedToken = cloneAttributes()
                if (input.incrementToken()) {
                    val nextTerm = termAtt.toString()
                    if (nextTerm == "pig") {
                        val secondSavedToken = cloneAttributes()
                        val secondEndOffset = offsetAtt.endOffset()
                        firstSavedToken.copyTo(this)
                        addSynonym("cavy", 2, secondEndOffset)
                        tokenQueue.add(secondSavedToken)
                    } else if (nextTerm == "dogs") {
                        tokenQueue.add(cloneAttributes())
                        addSynonym("dog", 1, offsetAtt.endOffset())
                    }
                } else {
                    endOfInput = true
                }
                firstSavedToken.copyTo(this)
            }
            return true
        } else {
            endOfInput = true
            return false
        }
    }

    private fun addSynonym(synonymText: String, posLen: Int, endOffset: Int) {
        termAtt.setEmpty()!!.append(synonymText)
        posIncAtt.setPositionIncrement(0)
        posLenAtt.positionLength = posLen
        offsetAtt.setOffset(offsetAtt.startOffset(), endOffset)
        typeAtt.setType("SYNONYM")
        tokenQueue.add(cloneAttributes())
    }

    private fun addSynonymAndRestoreOrigToken(synonymText: String, posLen: Int, endOffset: Int) {
        val origToken = cloneAttributes()
        addSynonym(synonymText, posLen, endOffset)
        origToken.copyTo(this)
    }
}
