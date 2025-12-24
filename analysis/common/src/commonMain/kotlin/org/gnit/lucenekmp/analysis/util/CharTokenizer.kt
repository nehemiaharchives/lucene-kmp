package org.gnit.lucenekmp.analysis.util

import okio.IOException
import org.gnit.lucenekmp.analysis.CharacterUtils
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.AttributeFactory


/**
 * An abstract base class for simple, character-oriented tokenizers.
 *
 *
 * The base class also provides factories to create instances of `CharTokenizer` using Java
 * 8 lambdas or method references. It is possible to create an instance which behaves exactly like
 * [LetterTokenizer]:
 *
 * <pre class="prettyprint lang-java">
 * Tokenizer tok = CharTokenizer.fromTokenCharPredicate(Character::isLetter);
</pre> *
 */
abstract class CharTokenizer : Tokenizer {
    /** Creates a new [CharTokenizer] instance  */
    constructor() {
        this.maxTokenLen = DEFAULT_MAX_WORD_LEN
    }

    /**
     * Creates a new [CharTokenizer] instance
     *
     * @param factory the attribute factory to use for this [Tokenizer]
     */
    constructor(factory: AttributeFactory) : super(factory) {
        this.maxTokenLen = DEFAULT_MAX_WORD_LEN
    }

    /**
     * Creates a new [CharTokenizer] instance
     *
     * @param factory the attribute factory to use for this [Tokenizer]
     * @param maxTokenLen maximum token length the tokenizer will emit. Must be greater than 0 and
     * less than MAX_TOKEN_LENGTH_LIMIT (1024*1024)
     * @throws IllegalArgumentException if maxTokenLen is invalid.
     */
    constructor(
        factory: AttributeFactory,
        maxTokenLen: Int
    ) : super(factory) {
        require(!(maxTokenLen > StandardTokenizer.MAX_TOKEN_LENGTH_LIMIT || maxTokenLen <= 0)) {
            ("maxTokenLen must be greater than 0 and less than "
                    + StandardTokenizer.MAX_TOKEN_LENGTH_LIMIT
                    + " passed: "
                    + maxTokenLen)
        }
        this.maxTokenLen = maxTokenLen
    }

    private var offset = 0
    private var bufferIndex = 0
    private var dataLen = 0
    private var finalOffset = 0
    private val maxTokenLen: Int

    private val termAtt: CharTermAttribute =
        addAttribute<CharTermAttribute>(CharTermAttribute::class)
    private val offsetAtt: OffsetAttribute =
        addAttribute<OffsetAttribute>(OffsetAttribute::class)

    private val ioBuffer: CharacterUtils.CharacterBuffer = CharacterUtils.newCharacterBuffer(IO_BUFFER_SIZE)

    /**
     * Returns true iff a codepoint should be included in a token. This tokenizer generates as tokens
     * adjacent sequences of codepoints which satisfy this predicate. Codepoints for which this is
     * false are used to define token boundaries and are not included in tokens.
     */
    protected abstract fun isTokenChar(c: Int): Boolean

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        clearAttributes()
        var length = 0
        var start = -1 // this variable is always initialized
        var end = -1
        var buffer: CharArray = termAtt.buffer()
        while (true) {
            if (bufferIndex >= dataLen) {
                offset += dataLen
                CharacterUtils.fill(
                    ioBuffer,
                    input
                ) // read supplementary char aware with CharacterUtils
                if (ioBuffer.length == 0) {
                    dataLen = 0 // so next offset += dataLen won't decrement offset
                    if (length > 0) {
                        break
                    } else {
                        finalOffset = correctOffset(offset)
                        return false
                    }
                }
                dataLen = ioBuffer.length
                bufferIndex = 0
            }
            // use CharacterUtils here to support < 3.1 UTF-16 code unit behavior if the char based
            // methods are gone
            val c: Int = Character.codePointAt(
                ioBuffer.buffer,
                bufferIndex,
                ioBuffer.length
            )
            val charCount: Int = Character.charCount(c)
            bufferIndex += charCount

            if (isTokenChar(c)) { // if it's a token char
                if (length == 0) { // start of token
                    assert(start == -1)
                    start = offset + bufferIndex - charCount
                    end = start
                } else if (length >= buffer.size - 1) { // supplementary could run out of bounds
                    // make sure a supplementary fits in the buffer
                    buffer = termAtt.resizeBuffer(2 + length)
                }
                end += charCount
                length += Character.toChars(c, buffer, length) // buffer it, normalized
                // buffer overflow! make sure to check for >= surrogate pair could break == test
                if (length >= maxTokenLen) {
                    break
                }
            } else if (length > 0) { // at non-Letter w/ chars
                break // return 'em
            }
        }

        termAtt.setLength(length)
        assert(start != -1)
        offsetAtt.setOffset(correctOffset(start), correctOffset(end).also { finalOffset = it })
        return true
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        // set final offset
        offsetAtt.setOffset(finalOffset, finalOffset)
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        bufferIndex = 0
        offset = 0
        dataLen = 0
        finalOffset = 0
        ioBuffer.reset() // make sure to reset the IO buffer!!
    }

    companion object {
        /**
         * Creates a new instance of CharTokenizer using a custom predicate, supplied as method reference
         * or lambda expression. The predicate should return `true` for all valid token characters.
         *
         *
         * This factory is intended to be used with lambdas or method references. E.g., an elegant way
         * to create an instance which behaves exactly as [LetterTokenizer] is:
         *
         * <pre class="prettyprint lang-java">
         * Tokenizer tok = CharTokenizer.fromTokenCharPredicate(Character::isLetter);
        </pre> *
         */
        fun fromTokenCharPredicate(tokenCharPredicate: (Int) -> Boolean /*java.util.function.IntPredicate*/): CharTokenizer {
            return fromTokenCharPredicate(
                TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
                tokenCharPredicate
            )
        }

        /**
         * Creates a new instance of CharTokenizer with the supplied attribute factory using a custom
         * predicate, supplied as method reference or lambda expression. The predicate should return
         * `true` for all valid token characters.
         *
         *
         * This factory is intended to be used with lambdas or method references. E.g., an elegant way
         * to create an instance which behaves exactly as [LetterTokenizer] is:
         *
         * <pre class="prettyprint lang-java">
         * Tokenizer tok = CharTokenizer.fromTokenCharPredicate(factory, Character::isLetter);
        </pre> *
         */
        fun fromTokenCharPredicate(
            factory: AttributeFactory,
            tokenCharPredicate: (Int) -> Boolean /*java.util.function.IntPredicate*/
        ): CharTokenizer {
            return object : CharTokenizer(factory) {
                override fun isTokenChar(c: Int): Boolean {
                    return tokenCharPredicate(c)
                }
            }
        }

        /**
         * Creates a new instance of CharTokenizer using a custom predicate, supplied as method reference
         * or lambda expression. The predicate should return `true` for all valid token separator
         * characters. This method is provided for convenience to easily use predicates that are negated
         * (they match the separator characters, not the token characters).
         *
         *
         * This factory is intended to be used with lambdas or method references. E.g., an elegant way
         * to create an instance which behaves exactly as [WhitespaceTokenizer] is:
         *
         * <pre class="prettyprint lang-java">
         * Tokenizer tok = CharTokenizer.fromSeparatorCharPredicate(Character::isWhitespace);
        </pre> *
         */
        fun fromSeparatorCharPredicate(
            separatorCharPredicate: (Int) -> Boolean /*java.util.function.IntPredicate*/
        ): CharTokenizer {
            return fromSeparatorCharPredicate(
                DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
                separatorCharPredicate
            )
        }

        /**
         * Creates a new instance of CharTokenizer with the supplied attribute factory using a custom
         * predicate, supplied as method reference or lambda expression. The predicate should return
         * `true` for all valid token separator characters.
         *
         *
         * This factory is intended to be used with lambdas or method references. E.g., an elegant way
         * to create an instance which behaves exactly as [WhitespaceTokenizer] is:
         *
         * <pre class="prettyprint lang-java">
         * Tokenizer tok = CharTokenizer.fromSeparatorCharPredicate(factory, Character::isWhitespace);
        </pre> *
         */
        fun fromSeparatorCharPredicate(
            factory: AttributeFactory,
            separatorCharPredicate: (Int) -> Boolean /*java.util.function.IntPredicate*/
        ): CharTokenizer {
            return fromTokenCharPredicate(factory, separatorCharPredicate.negate())
        }

        private fun ((Int) -> Boolean).negate(): (Int) -> Boolean = { value -> !this(value) }

        const val DEFAULT_MAX_WORD_LEN: Int = 255
        private const val IO_BUFFER_SIZE = 4096
    }
}
