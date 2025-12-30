package org.gnit.lucenekmp.analysis.cn.smart

import okio.IOException
import org.gnit.lucenekmp.analysis.cn.smart.hhmm.SegToken
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.analysis.util.SegmentingTokenizerBase
import org.gnit.lucenekmp.jdkport.BreakIterator
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * Tokenizer for Chinese or mixed Chinese-English text.
 */
class HMMChineseTokenizer : SegmentingTokenizerBase {
    private val termAtt = addAttribute(CharTermAttribute::class)
    private val offsetAtt = addAttribute(OffsetAttribute::class)
    private val typeAtt = addAttribute(TypeAttribute::class)

    private val wordSegmenter = WordSegmenter()
    private var tokens: Iterator<SegToken>? = null

    companion object {
        private val sentenceProto: BreakIterator = BreakIterator.getSentenceInstance(Locale.ROOT)
    }

    constructor() : this(DEFAULT_TOKEN_ATTRIBUTE_FACTORY)

    constructor(factory: AttributeFactory) : super(factory, sentenceProto.clone() as BreakIterator)

    override fun setNextSentence(sentenceStart: Int, sentenceEnd: Int) {
        val sentence = buffer.concatToString(sentenceStart, sentenceEnd)
        tokens = wordSegmenter.segmentSentence(sentence, offset + sentenceStart).iterator()
    }

    override fun incrementWord(): Boolean {
        val iterator = tokens
        if (iterator == null || !iterator.hasNext()) {
            return false
        }
        val token = iterator.next()
        clearAttributes()
        termAtt.copyBuffer(token.charArray, 0, token.charArray.size)
        offsetAtt.setOffset(correctOffset(token.startOffset), correctOffset(token.endOffset))
        typeAtt.setType("word")
        return true
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        tokens = null
    }
}
