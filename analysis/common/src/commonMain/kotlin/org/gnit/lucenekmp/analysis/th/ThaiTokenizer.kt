package org.gnit.lucenekmp.analysis.th

import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.util.CharArrayIterator
import org.gnit.lucenekmp.analysis.util.SegmentingTokenizerBase
import org.gnit.lucenekmp.jdkport.BreakIterator
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * Tokenizer that uses [BreakIterator] to tokenize Thai text.
 */
class ThaiTokenizer : SegmentingTokenizerBase {
    private val wordBreaker: BreakIterator
    private val wrapper: CharArrayIterator = CharArrayIterator.newWordInstance()

    private var sentenceStart: Int = 0
    private var sentenceEnd: Int = 0

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

    constructor() : this(DEFAULT_TOKEN_ATTRIBUTE_FACTORY)

    constructor(factory: AttributeFactory) : super(factory, sentenceProto.clone() as BreakIterator) {
        if (!DBBI_AVAILABLE) {
            throw UnsupportedOperationException("This JRE does not have support for Thai segmentation")
        }
        wordBreaker = proto.clone() as BreakIterator
    }

    override fun setNextSentence(sentenceStart: Int, sentenceEnd: Int) {
        this.sentenceStart = sentenceStart
        this.sentenceEnd = sentenceEnd
        wrapper.setText(buffer, sentenceStart, sentenceEnd - sentenceStart)
        wordBreaker.setText(wrapper)
    }

    override fun incrementWord(): Boolean {
        var start = wordBreaker.current()
        if (start == BreakIterator.DONE) {
            return false
        }

        var end = wordBreaker.next()
        while (end != BreakIterator.DONE && !isLetterOrDigit(
                Character.codePointAt(buffer, sentenceStart + start, sentenceEnd)
            )
        ) {
            start = end
            end = wordBreaker.next()
        }

        if (end == BreakIterator.DONE) {
            return false
        }

        clearAttributes()
        termAtt.copyBuffer(buffer, sentenceStart + start, end - start)
        offsetAtt.setOffset(
            correctOffset(offset + sentenceStart + start),
            correctOffset(offset + sentenceStart + end)
        )
        return true
    }

    private fun isLetterOrDigit(codePoint: Int): Boolean {
        return when (Character.getType(codePoint)) {
            Character.UPPERCASE_LETTER.toInt(),
            Character.LOWERCASE_LETTER.toInt(),
            Character.TITLECASE_LETTER.toInt(),
            Character.MODIFIER_LETTER.toInt(),
            Character.OTHER_LETTER.toInt(),
            Character.DECIMAL_DIGIT_NUMBER.toInt() -> true
            else -> false
        }
    }

    companion object {
        /**
         * True if the JRE supports a working dictionary-based breakiterator for Thai.
         */
        val DBBI_AVAILABLE: Boolean

        private val proto: BreakIterator = BreakIterator.getWordInstance(Locale("th"))
        private val sentenceProto: BreakIterator = BreakIterator.getSentenceInstance(Locale.ROOT)

        init {
            proto.setText("ภาษาไทย")
            DBBI_AVAILABLE = proto.isBoundary(4)
        }
    }
}
