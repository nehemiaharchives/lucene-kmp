package org.gnit.lucenekmp.analysis.vi

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import okio.IOException

class VietnameseTokenizer(config: VietnameseConfig) : Tokenizer() {
    private val termAtt = addAttribute(CharTermAttribute::class)
    private val offsetAtt = addAttribute(OffsetAttribute::class)
    private val typeAtt = addAttribute(TypeAttribute::class)
    private val posIncrAtt = addAttribute(PositionIncrementAttribute::class)

    private val tokenizer = VietnameseTokenizerImpl(config, input)
    private var offset = 0

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        clearAttributes()
        val vietnameseToken: VietnameseToken? = tokenizer.getNextToken()
        if (vietnameseToken != null) {
            posIncrAtt.setPositionIncrement(1)
            typeAtt.setType("<${vietnameseToken.getType()}>")
            val text = vietnameseToken.getText()
            termAtt.copyBuffer(text.toCharArray(), 0, text.length)
            offsetAtt.setOffset(correctOffset(vietnameseToken.getPos()), correctOffset(vietnameseToken.getEndPos()))
            return true
        }
        return false
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        val finalOffset = correctOffset(offset)
        offsetAtt.setOffset(finalOffset, finalOffset)
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        tokenizer.reset(input)
        offset = 0
    }
}
