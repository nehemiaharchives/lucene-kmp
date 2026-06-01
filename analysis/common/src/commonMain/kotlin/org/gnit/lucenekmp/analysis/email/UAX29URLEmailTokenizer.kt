package org.gnit.lucenekmp.analysis.email

import okio.IOException
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * This class implements Word Break rules from the Unicode Text Segmentation algorithm, as specified
 * in [Unicode Standard Annex #29](http://unicode.org/reports/tr29/) URLs and email
 * addresses are also tokenized according to the relevant RFCs.
 */
class UAX29URLEmailTokenizer : Tokenizer {
    /** A private instance of the JFlex-constructed scanner */
    private val scanner: UAX29URLEmailTokenizerImpl

    private var skippedPositions = 0

    private var maxTokenLength = StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH

    /**
     * Set the max allowed token length. Tokens larger than this will be chopped up at this token
     * length and emitted as multiple tokens. If you need to skip such large tokens, you could
     * increase this max length, and then use `LengthFilter` to remove long tokens. The default
     * is [UAX29URLEmailAnalyzer.DEFAULT_MAX_TOKEN_LENGTH].
     *
     * @throws IllegalArgumentException if the given length is outside of the range [1, [MAX_TOKEN_LENGTH_LIMIT]].
     */
    fun setMaxTokenLength(length: Int) {
        require(length >= 1) { "maxTokenLength must be greater than zero" }
        require(length <= MAX_TOKEN_LENGTH_LIMIT) { "maxTokenLength may not exceed $MAX_TOKEN_LENGTH_LIMIT" }
        if (length != maxTokenLength) {
            maxTokenLength = length
            scanner.setBufferSize(length)
        }
    }

    /**
     * @see setMaxTokenLength
     */
    fun getMaxTokenLength(): Int {
        return maxTokenLength
    }

    /**
     * Creates a new instance of the UAX29URLEmailTokenizer. Attaches the `input` to the
     * newly created JFlex scanner.
     */
    constructor() {
        scanner = getScanner()
    }

    /** Creates a new UAX29URLEmailTokenizer with a given [AttributeFactory] */
    constructor(factory: AttributeFactory) : super(factory) {
        scanner = getScanner()
    }

    private fun getScanner(): UAX29URLEmailTokenizerImpl {
        return UAX29URLEmailTokenizerImpl(input)
    }

    // this tokenizer generates three attributes:
    // term offset, positionIncrement and type
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val posIncrAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        clearAttributes()
        skippedPositions = 0

        while (true) {
            val tokenType = scanner.getNextToken()

            if (tokenType == UAX29URLEmailTokenizerImpl.YYEOF) {
                return false
            }

            if (scanner.yylength() <= maxTokenLength) {
                posIncrAtt.setPositionIncrement(skippedPositions + 1)
                scanner.getText(termAtt)
                val start = scanner.yychar()
                offsetAtt.setOffset(correctOffset(start), correctOffset(start + termAtt.length))
                typeAtt.setType(TOKEN_TYPES[tokenType])
                return true
            } else {
                // When we skip a too-long term, we still increment the
                // position increment
                skippedPositions++
            }
        }
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        // set final offset
        val finalOffset = correctOffset(scanner.yychar() + scanner.yylength())
        offsetAtt.setOffset(finalOffset, finalOffset)
        // adjust any skipped tokens
        posIncrAtt.setPositionIncrement(posIncrAtt.getPositionIncrement() + skippedPositions)
    }

    override fun close() {
        super.close()
        scanner.yyreset(input)
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        scanner.yyreset(input)
        skippedPositions = 0
    }

    companion object {
        /** Alpha/numeric token type */
        const val ALPHANUM: Int = 0

        /** Numeric token type */
        const val NUM: Int = 1

        /** Southeast Asian token type */
        const val SOUTHEAST_ASIAN: Int = 2

        /** Ideographic token type */
        const val IDEOGRAPHIC: Int = 3

        /** Hiragana token type */
        const val HIRAGANA: Int = 4

        /** Katakana token type */
        const val KATAKANA: Int = 5

        /** Hangul token type */
        const val HANGUL: Int = 6

        /** URL token type */
        const val URL: Int = 7

        /** Email token type */
        const val EMAIL: Int = 8

        /** Emoji token type. */
        const val EMOJI: Int = 9

        /** String token types that correspond to token type int constants */
        val TOKEN_TYPES: Array<String> = arrayOf(
            StandardTokenizer.TOKEN_TYPES[StandardTokenizer.ALPHANUM],
            StandardTokenizer.TOKEN_TYPES[StandardTokenizer.NUM],
            StandardTokenizer.TOKEN_TYPES[StandardTokenizer.SOUTHEAST_ASIAN],
            StandardTokenizer.TOKEN_TYPES[StandardTokenizer.IDEOGRAPHIC],
            StandardTokenizer.TOKEN_TYPES[StandardTokenizer.HIRAGANA],
            StandardTokenizer.TOKEN_TYPES[StandardTokenizer.KATAKANA],
            StandardTokenizer.TOKEN_TYPES[StandardTokenizer.HANGUL],
            "<URL>",
            "<EMAIL>",
            StandardTokenizer.TOKEN_TYPES[StandardTokenizer.EMOJI]
        )

        /** Absolute maximum sized token */
        const val MAX_TOKEN_LENGTH_LIMIT: Int = 1024 * 1024
    }
}
