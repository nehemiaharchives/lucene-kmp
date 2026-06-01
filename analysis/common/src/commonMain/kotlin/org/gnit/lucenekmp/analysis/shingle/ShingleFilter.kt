package org.gnit.lucenekmp.analysis.shingle

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.util.AttributeSource

/**
 * A ShingleFilter constructs shingles (token n-grams) from a token stream. In other words, it
 * creates combinations of tokens as a single token.
 *
 * <p>For example, the sentence "please divide this sentence into shingles" might be tokenized into
 * shingles "please divide", "divide this", "this sentence", "sentence into", and "into shingles".
 *
 * <p>This filter handles position increments > 1 by inserting filler tokens (tokens with
 * termtext "_"). It does not handle a position increment of 0.
 */
class ShingleFilter : TokenFilter {
    companion object {
        /** filler token for when positionIncrement is more than 1 */
        const val DEFAULT_FILLER_TOKEN = "_"

        /** default maximum shingle size is 2. */
        const val DEFAULT_MAX_SHINGLE_SIZE = 2

        /** default minimum shingle size is 2. */
        const val DEFAULT_MIN_SHINGLE_SIZE = 2

        /** default token type attribute value is "shingle" */
        const val DEFAULT_TOKEN_TYPE = "shingle"

        /** The default string to use when joining adjacent tokens to form a shingle */
        const val DEFAULT_TOKEN_SEPARATOR = " "
    }

    /**
     * The sequence of input stream tokens (or filler tokens, if necessary) that will be composed to
     * form output shingles.
     */
    private val inputWindow = mutableListOf<InputWindowToken>()

    /** The number of input tokens in the next output token. This is the "n" in "token n-grams". */
    private var gramSize: CircularSequence

    /** Shingle and unigram text is composed here. */
    private val gramBuilder = StringBuilder()

    /** The token type attribute value to use - default is "shingle" */
    private var tokenType = DEFAULT_TOKEN_TYPE

    /** The string to use when joining adjacent tokens to form a shingle */
    private var tokenSeparator = DEFAULT_TOKEN_SEPARATOR

    /**
     * The string to insert for each position at which there is no token (i.e., when position
     * increment is greater than one).
     */
    private var fillerToken = DEFAULT_FILLER_TOKEN.toCharArray()

    /** By default, we output unigrams (individual tokens) as well as shingles (token n-grams). */
    private var outputUnigrams = true

    /** By default, we don't override behavior of outputUnigrams. */
    private var outputUnigramsIfNoShingles = false

    /** maximum shingle size (number of tokens) */
    private var maxShingleSize = 0

    /** minimum shingle size (number of tokens) */
    private var minShingleSize = 0

    /**
     * The remaining number of filler tokens to be inserted into the input stream from which shingles
     * are composed, to handle position increments greater than one.
     */
    private var numFillerTokensToInsert = 0

    /**
     * When the next input stream token has a position increment greater than one, it is stored in
     * this field until sufficient filler tokens have been inserted to account for the position
     * increment.
     */
    private var nextInputStreamToken: AttributeSource? = null

    /** Whether or not there is a next input stream token. */
    private var isNextInputStreamToken = false

    /** Whether at least one unigram or shingle has been output at the current position. */
    private var isOutputHere = false

    /** true if no shingles have been output yet (for outputUnigramsIfNoShingles). */
    var noShingleOutput = true

    /** Holds the State after input.end() was called, so we can restore it in our end() impl. */
    private var endState: State? = null

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val posIncrAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val posLenAtt: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)
    private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)

    private var exhausted = false

    /**
     * Constructs a ShingleFilter with the specified shingle size from the [TokenStream] `input`
     *
     * @param input input stream
     * @param minShingleSize minimum shingle size produced by the filter.
     * @param maxShingleSize maximum shingle size produced by the filter.
     */
    constructor(input: TokenStream, minShingleSize: Int, maxShingleSize: Int) : super(input) {
        setMaxShingleSize(maxShingleSize)
        setMinShingleSize(minShingleSize)
        gramSize = CircularSequence()
    }

    /**
     * Constructs a ShingleFilter with the specified shingle size from the [TokenStream] `input`
     *
     * @param input input stream
     * @param maxShingleSize maximum shingle size produced by the filter.
     */
    constructor(input: TokenStream, maxShingleSize: Int) : this(input, DEFAULT_MIN_SHINGLE_SIZE, maxShingleSize)

    /**
     * Construct a ShingleFilter with default shingle size: 2.
     *
     * @param input input stream
     */
    constructor(input: TokenStream) : this(input, DEFAULT_MIN_SHINGLE_SIZE, DEFAULT_MAX_SHINGLE_SIZE)

    /**
     * Construct a ShingleFilter with the specified token type for shingle tokens and the default
     * shingle size: 2
     *
     * @param input input stream
     * @param tokenType token type for shingle tokens
     */
    constructor(input: TokenStream, tokenType: String) : this(input, DEFAULT_MIN_SHINGLE_SIZE, DEFAULT_MAX_SHINGLE_SIZE) {
        setTokenType(tokenType)
    }

    /**
     * Set the type of the shingle tokens produced by this filter. (default: "shingle")
     *
     * @param tokenType token tokenType
     */
    fun setTokenType(tokenType: String) {
        this.tokenType = tokenType
    }

    /**
     * Shall the output stream contain the input tokens (unigrams) as well as shingles? (default:
     * true.)
     *
     * @param outputUnigrams Whether or not the output stream shall contain the input tokens
     *     (unigrams)
     */
    fun setOutputUnigrams(outputUnigrams: Boolean) {
        this.outputUnigrams = outputUnigrams
        gramSize = CircularSequence()
    }

    /**
     * Shall we override the behavior of outputUnigrams==false for those times when no shingles are
     * available (because there are fewer than minShingleSize tokens in the input stream)? (default:
     * false.)
     *
     * <p>Note that if outputUnigrams==true, then unigrams are always output, regardless of whether
     * any shingles are available.
     *
     * @param outputUnigramsIfNoShingles Whether or not to output a single unigram when no shingles
     *     are available.
     */
    fun setOutputUnigramsIfNoShingles(outputUnigramsIfNoShingles: Boolean) {
        this.outputUnigramsIfNoShingles = outputUnigramsIfNoShingles
    }

    /**
     * Set the max shingle size (default: 2)
     *
     * @param maxShingleSize max size of output shingles
     */
    fun setMaxShingleSize(maxShingleSize: Int) {
        require(maxShingleSize >= 2) { "Max shingle size must be >= 2" }
        this.maxShingleSize = maxShingleSize
    }

    /**
     * Set the min shingle size (default: 2).
     *
     * <p>This method requires that the passed in minShingleSize is not greater than maxShingleSize,
     * so make sure that maxShingleSize is set before calling this method.
     *
     * <p>The unigram output option is independent of the min shingle size.
     *
     * @param minShingleSize min size of output shingles
     */
    fun setMinShingleSize(minShingleSize: Int) {
        require(minShingleSize >= 2) { "Min shingle size must be >= 2" }
        require(minShingleSize <= maxShingleSize) { "Min shingle size must be <= max shingle size" }
        this.minShingleSize = minShingleSize
        gramSize = CircularSequence()
    }

    /**
     * Sets the string to use when joining adjacent tokens to form a shingle
     *
     * @param tokenSeparator used to separate input stream tokens in output shingles
     */
    fun setTokenSeparator(tokenSeparator: String?) {
        this.tokenSeparator = tokenSeparator ?: ""
    }

    /**
     * Sets the string to insert for each position at which there is no token (i.e., when position
     * increment is greater than one).
     *
     * @param fillerToken string to insert at each position where there is no token
     */
    fun setFillerToken(fillerToken: String?) {
        this.fillerToken = fillerToken?.toCharArray() ?: CharArray(0)
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        var tokenAvailable = false
        var builtGramSize = 0
        if (gramSize.atMinValue() || inputWindow.size < gramSize.value) {
            shiftInputWindow()
            gramBuilder.setLength(0)
        } else {
            builtGramSize = gramSize.previousValue
        }
        if (inputWindow.size >= gramSize.value) {
            var isAllFiller = true
            var nextToken: InputWindowToken? = null
            var gramNum = 1
            for (inputWindowToken in inputWindow) {
                if (builtGramSize >= gramSize.value) {
                    break
                }
                nextToken = inputWindowToken
                if (builtGramSize < gramNum) {
                    if (builtGramSize > 0) {
                        gramBuilder.append(tokenSeparator)
                    }
                    gramBuilder.append(nextToken.termAtt.toString())
                    ++builtGramSize
                }
                if (isAllFiller && nextToken.isFiller) {
                    if (gramNum == gramSize.value) {
                        gramSize.advance()
                    }
                } else {
                    isAllFiller = false
                }
                ++gramNum
            }
            if (!isAllFiller && builtGramSize == gramSize.value) {
                inputWindow.first().attSource.copyTo(this)
                posIncrAtt.setPositionIncrement(if (isOutputHere) 0 else 1)
                termAtt.setEmpty()!!.append(gramBuilder)
                if (gramSize.value > 1) {
                    typeAtt.setType(tokenType)
                    noShingleOutput = false
                }
                offsetAtt.setOffset(offsetAtt.startOffset(), nextToken!!.offsetAtt.endOffset())
                if (outputUnigrams) {
                    posLenAtt.positionLength = builtGramSize
                } else {
                    posLenAtt.positionLength = maxOf(1, (builtGramSize - minShingleSize) + 1)
                }
                isOutputHere = true
                gramSize.advance()
                tokenAvailable = true
            }
        }
        return tokenAvailable
    }

    /**
     * Get the next token from the input stream.
     *
     * <p>If the next token has `positionIncrement > 1`, `positionIncrement - 1`
     * [fillerToken]s are inserted first.
     *
     * @param target Where to put the new token; if null, a new instance is created.
     * @return On success, the populated token; null otherwise
     * @throws IOException if the input stream has a problem
     */
    @Throws(IOException::class)
    private fun getNextToken(target: InputWindowToken?): InputWindowToken? {
        var newTarget = target
        if (numFillerTokensToInsert > 0) {
            if (target == null) {
                newTarget = InputWindowToken(nextInputStreamToken!!.cloneAttributes())
            } else {
                nextInputStreamToken!!.copyTo(target.attSource)
            }
            newTarget.offsetAtt.setOffset(newTarget.offsetAtt.startOffset(), newTarget.offsetAtt.startOffset())
            newTarget.termAtt.copyBuffer(fillerToken, 0, fillerToken.size)
            newTarget.isFiller = true
            --numFillerTokensToInsert
        } else if (isNextInputStreamToken) {
            if (target == null) {
                newTarget = InputWindowToken(nextInputStreamToken!!.cloneAttributes())
            } else {
                nextInputStreamToken!!.copyTo(target.attSource)
            }
            isNextInputStreamToken = false
            newTarget.isFiller = false
        } else if (!exhausted) {
            if (input.incrementToken()) {
                if (target == null) {
                    newTarget = InputWindowToken(cloneAttributes())
                } else {
                    copyTo(target.attSource)
                    newTarget = target
                }
                if (posIncrAtt.getPositionIncrement() > 1) {
                    numFillerTokensToInsert = minOf(posIncrAtt.getPositionIncrement() - 1, maxShingleSize - 1)
                    if (nextInputStreamToken == null) {
                        nextInputStreamToken = cloneAttributes()
                    } else {
                        copyTo(nextInputStreamToken!!)
                    }
                    isNextInputStreamToken = true
                    newTarget.offsetAtt.setOffset(offsetAtt.startOffset(), offsetAtt.startOffset())
                    newTarget.termAtt.copyBuffer(fillerToken, 0, fillerToken.size)
                    newTarget.isFiller = true
                    --numFillerTokensToInsert
                } else {
                    newTarget.isFiller = false
                }
            } else {
                exhausted = true
                input.end()
                endState = captureState()
                numFillerTokensToInsert = minOf(posIncrAtt.getPositionIncrement(), maxShingleSize - 1)
                if (numFillerTokensToInsert > 0) {
                    nextInputStreamToken = AttributeSource(attributeFactory)
                    nextInputStreamToken!!.addAttribute(CharTermAttribute::class)
                    val newOffsetAtt = nextInputStreamToken!!.addAttribute(OffsetAttribute::class)
                    newOffsetAtt.setOffset(offsetAtt.endOffset(), offsetAtt.endOffset())
                    return getNextToken(target)
                } else {
                    newTarget = null
                }
            }
        } else {
            newTarget = null
        }
        return newTarget
    }

    @Throws(IOException::class)
    override fun end() {
        if (!exhausted) {
            super.end()
        } else {
            restoreState(endState)
        }
    }

    /**
     * Fills [inputWindow] with input stream tokens, if available, shifting to the right if the
     * window was previously full.
     *
     * <p>Resets [gramSize] to its minimum value.
     *
     * @throws IOException if there's a problem getting the next token
     */
    @Throws(IOException::class)
    private fun shiftInputWindow() {
        var firstToken: InputWindowToken? = null
        if (inputWindow.isNotEmpty()) {
            firstToken = inputWindow.removeAt(0)
        }
        while (inputWindow.size < maxShingleSize) {
            if (firstToken != null) {
                if (getNextToken(firstToken) != null) {
                    inputWindow.add(firstToken)
                    firstToken = null
                } else {
                    break
                }
            } else {
                val nextToken = getNextToken(null)
                if (nextToken != null) {
                    inputWindow.add(nextToken)
                } else {
                    break
                }
            }
        }
        if (outputUnigramsIfNoShingles && noShingleOutput && gramSize.minValue > 1 && inputWindow.size < minShingleSize) {
            gramSize.minValue = 1
        }
        gramSize.reset()
        isOutputHere = false
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        gramSize.reset()
        inputWindow.clear()
        nextInputStreamToken = null
        isNextInputStreamToken = false
        numFillerTokensToInsert = 0
        isOutputHere = false
        noShingleOutput = true
        exhausted = false
        endState = null
        if (outputUnigramsIfNoShingles && !outputUnigrams) {
            gramSize.minValue = minShingleSize
        }
    }

    /**
     * An instance of this class is used to maintain the number of input stream tokens that will be
     * used to compose the next unigram or shingle: [gramSize].
     *
     * <p>`gramSize` will take on values from the circular sequence **{ [ 1, ] [minShingleSize]
     * [ , ... , [maxShingleSize] ] }**.
     *
     * <p>1 is included in the circular sequence only if [outputUnigrams] = true.
     */
    private inner class CircularSequence {
        var value: Int
            private set
        var previousValue: Int
            private set
        var minValue: Int

        init {
            minValue = if (outputUnigrams) 1 else minShingleSize
            previousValue = 0
            value = 0
            reset()
        }

        /**
         * Increments this circular number's value to the next member in the circular sequence
         * `gramSize` will take on values from the circular sequence **{ [ 1, ] [minShingleSize]
         * [ , ... , [maxShingleSize] ] }**.
         *
         * <p>1 is included in the circular sequence only if [outputUnigrams] = true.
         */
        fun advance() {
            previousValue = value
            value =
                if (value == 1) {
                    minShingleSize
                } else if (value == maxShingleSize) {
                    reset()
                    value
                } else {
                    value + 1
                }
        }

        /**
         * Sets this circular number's value to the first member of the circular sequence
         *
         * <p>`gramSize` will take on values from the circular sequence **{ [ 1, ] [minShingleSize]
         * [ , ... , [maxShingleSize] ] }**.
         *
         * <p>1 is included in the circular sequence only if [outputUnigrams] = true.
         */
        fun reset() {
            previousValue = minValue
            value = minValue
        }

        /**
         * Returns true if the current value is the first member of the circular sequence.
         *
         * <p>If [outputUnigrams] = true, the first member of the circular sequence will be 1;
         * otherwise, it will be [minShingleSize].
         *
         * @return true if the current value is the first member of the circular sequence; false
         *     otherwise
         */
        fun atMinValue(): Boolean {
            return value == minValue
        }
    }

    private class InputWindowToken(val attSource: AttributeSource) {
        val termAtt: CharTermAttribute = attSource.getAttribute(CharTermAttribute::class)!!
        val offsetAtt: OffsetAttribute = attSource.getAttribute(OffsetAttribute::class)!!
        var isFiller = false
    }
}
