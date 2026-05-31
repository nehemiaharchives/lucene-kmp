package org.gnit.lucenekmp.analysis.classic

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.util.AttributeFactory
import okio.IOException

/**
 * A grammar-based tokenizer constructed with JFlex
 *
 * <p>This should be a good tokenizer for most European-language documents:
 *
 * <ul>
 *   <li>Splits words at punctuation characters, removing punctuation. However, a dot that's not
 *       followed by whitespace is considered part of a token.
 *   <li>Splits words at hyphens, unless there's a number in the token, in which case the whole
 *       token is interpreted as a product number and is not split.
 *   <li>Recognizes email addresses and internet hostnames as one token.
 * </ul>
 *
 * <p>Many applications have specific tokenizer needs. If this tokenizer does not suit your
 * application, please consider copying this source code directory to your project and maintaining
 * your own grammar-based tokenizer.
 *
 * <p>ClassicTokenizer was named StandardTokenizer in Lucene versions prior to 3.1. As of 3.1,
 * [org.gnit.lucenekmp.analysis.standard.StandardTokenizer] implements Unicode text segmentation,
 * as specified by UAX#29.
 */
class ClassicTokenizer : Tokenizer {
    /** A private instance of the JFlex-constructed scanner */
    private lateinit var scanner: ClassicTokenizerImpl
    private var skippedPositions: Int = 0
    private var maxTokenLength = StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH

    /** Set the max allowed token length. Any token longer than this is skipped. */
    fun setMaxTokenLength(length: Int) {
        require(length >= 1) { "maxTokenLength must be greater than zero" }
        maxTokenLength = length
    }

    /**
     * @see setMaxTokenLength
     */
    fun getMaxTokenLength(): Int = maxTokenLength

    /**
     * Creates a new instance of the [org.gnit.lucenekmp.analysis.classic.ClassicTokenizer].
     * Attaches the `input` to the newly created JFlex scanner.
     *
     * <p>See http://issues.apache.org/jira/browse/LUCENE-1068
     */
    constructor() {
        init()
    }

    /** Creates a new ClassicTokenizer with a given [org.gnit.lucenekmp.util.AttributeFactory] */
    constructor(factory: AttributeFactory) : super(factory) {
        init()
    }

    private fun init() {
        scanner = ClassicTokenizerImpl(input)
    }

    // this tokenizer generates three attributes:
    // term offset, positionIncrement and type
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val posIncrAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)

    /*
     * (non-Javadoc)
     *
     * @see org.apache.lucene.analysis.TokenStream#next()
     */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        clearAttributes()
        skippedPositions = 0
        while (true) {
            val tokenType = scanner.getNextToken()
            if (tokenType == ClassicTokenizerImpl.YYEOF) {
                return false
            }
            if (scanner.yylength() <= maxTokenLength) {
                posIncrAtt.setPositionIncrement(skippedPositions + 1)
                scanner.getText(termAtt)
                val start = scanner.yychar()
                offsetAtt.setOffset(correctOffset(start), correctOffset(start + termAtt.length))
                if (tokenType == ACRONYM_DEP) {
                    typeAtt.setType(TOKEN_TYPES[HOST])
                    termAtt.setLength(termAtt.length - 1)
                } else {
                    typeAtt.setType(TOKEN_TYPES[tokenType])
                }
                return true
            } else {
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
        /** String token types that correspond to token type int constants */
        const val ALPHANUM: Int = 0
        const val APOSTROPHE: Int = 1
        const val ACRONYM: Int = 2
        const val COMPANY: Int = 3
        const val EMAIL: Int = 4
        const val HOST: Int = 5
        const val NUM: Int = 6
        const val CJ: Int = 7
        const val ACRONYM_DEP: Int = 8

        /** String token types that correspond to token type int constants */
        val TOKEN_TYPES = arrayOf(
            "<ALPHANUM>",
            "<APOSTROPHE>",
            "<ACRONYM>",
            "<COMPANY>",
            "<EMAIL>",
            "<HOST>",
            "<NUM>",
            "<CJ>",
            "<ACRONYM_DEP>"
        )
    }
}
