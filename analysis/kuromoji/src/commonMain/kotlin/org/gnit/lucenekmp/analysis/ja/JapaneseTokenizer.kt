package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.ja.dict.CharacterDefinition
import org.gnit.lucenekmp.analysis.ja.dict.ConnectionCosts
import org.gnit.lucenekmp.analysis.ja.dict.JaMorphData
import org.gnit.lucenekmp.analysis.ja.dict.TokenInfoDictionary
import org.gnit.lucenekmp.analysis.ja.dict.TokenInfoFST
import org.gnit.lucenekmp.analysis.ja.dict.UnknownDictionary
import org.gnit.lucenekmp.analysis.ja.dict.UserDictionary
import org.gnit.lucenekmp.analysis.ja.tokenattributes.BaseFormAttribute
import org.gnit.lucenekmp.analysis.ja.tokenattributes.InflectionAttribute
import org.gnit.lucenekmp.analysis.ja.tokenattributes.PartOfSpeechAttribute
import org.gnit.lucenekmp.analysis.ja.tokenattributes.ReadingAttribute
import org.gnit.lucenekmp.analysis.morph.GraphvizFormatter
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.analysis.ja.tokenattributes.BaseFormAttributeImpl
import org.gnit.lucenekmp.analysis.ja.tokenattributes.InflectionAttributeImpl
import org.gnit.lucenekmp.analysis.ja.tokenattributes.PartOfSpeechAttributeImpl
import org.gnit.lucenekmp.analysis.ja.tokenattributes.ReadingAttributeImpl
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.jdkport.StringReader

/**
 * Tokenizer for Japanese that uses morphological analysis.
 */
class JapaneseTokenizer : Tokenizer {

    enum class Mode {
        NORMAL,
        SEARCH,
        EXTENDED
    }

    companion object {
        val DEFAULT_MODE: Mode = Mode.SEARCH

        internal fun isPunctuation(ch: Char): Boolean = KuromojiViterbiNBest.isPunctuation(ch)

        private val DEFAULT_TOKEN_ATTRIBUTE_FACTORY: AttributeFactory =
            object : AttributeFactory() {
                override fun createAttributeInstance(attClass: kotlin.reflect.KClass<out Attribute>): AttributeImpl {
                    return when (attClass) {
                        BaseFormAttribute::class -> BaseFormAttributeImpl()
                        PartOfSpeechAttribute::class -> PartOfSpeechAttributeImpl()
                        ReadingAttribute::class -> ReadingAttributeImpl()
                        InflectionAttribute::class -> InflectionAttributeImpl()
                        else -> TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY.createAttributeInstance(attClass)
                    }
                }
            }
    }

    private var lastTokenPos: Int = -1

    private val viterbi: KuromojiViterbiNBest

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val posLengthAtt: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)

    init {
        // Ensure Kuromoji-specific attributes are registered before addAttribute() is called.
        BaseFormAttributeImpl.ensureRegistered()
        PartOfSpeechAttributeImpl.ensureRegistered()
        ReadingAttributeImpl.ensureRegistered()
        InflectionAttributeImpl.ensureRegistered()
        addAttributeImpl(BaseFormAttributeImpl())
        addAttributeImpl(PartOfSpeechAttributeImpl())
        addAttributeImpl(ReadingAttributeImpl())
        addAttributeImpl(InflectionAttributeImpl())
    }

    private val basicFormAtt: BaseFormAttribute = addAttribute(BaseFormAttribute::class)
    private val posAtt: PartOfSpeechAttribute = addAttribute(PartOfSpeechAttribute::class)
    private val readingAtt: ReadingAttribute = addAttribute(ReadingAttribute::class)
    private val inflectionAtt: InflectionAttribute = addAttribute(InflectionAttribute::class)

    constructor(userDictionary: UserDictionary?, discardPunctuation: Boolean, mode: Mode) : this(
        DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
        userDictionary,
        discardPunctuation,
        true,
        mode
    )

    constructor(userDictionary: UserDictionary?, discardPunctuation: Boolean, discardCompoundToken: Boolean, mode: Mode) : this(
        DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
        userDictionary,
        discardPunctuation,
        discardCompoundToken,
        mode
    )

    constructor(factory: AttributeFactory, userDictionary: UserDictionary?, discardPunctuation: Boolean, mode: Mode) : this(
        factory,
        TokenInfoDictionary.getInstance(),
        UnknownDictionary.getInstance(),
        ConnectionCosts.getInstance(),
        userDictionary,
        discardPunctuation,
        true,
        mode
    )

    constructor(
        factory: AttributeFactory,
        userDictionary: UserDictionary?,
        discardPunctuation: Boolean,
        discardCompoundToken: Boolean,
        mode: Mode
    ) : this(
        factory,
        TokenInfoDictionary.getInstance(),
        UnknownDictionary.getInstance(),
        ConnectionCosts.getInstance(),
        userDictionary,
        discardPunctuation,
        discardCompoundToken,
        mode
    )

    constructor(
        factory: AttributeFactory,
        systemDictionary: TokenInfoDictionary,
        unkDictionary: UnknownDictionary,
        connectionCosts: ConnectionCosts,
        userDictionary: UserDictionary?,
        discardPunctuation: Boolean,
        discardCompoundToken: Boolean,
        mode: Mode
    ) : super(factory) {

        val fst: TokenInfoFST = systemDictionary.getFST()
        val fstReader: FST.BytesReader = fst.getBytesReader()

        val userFST: TokenInfoFST? = userDictionary?.getFST()
        val userFSTReader: FST.BytesReader? = userFST?.getBytesReader()

        val (searchMode, extendedMode, outputCompounds) = when (mode) {
            Mode.SEARCH -> Triple(true, false, !discardCompoundToken)
            Mode.EXTENDED -> Triple(true, true, !discardCompoundToken)
            Mode.NORMAL -> Triple(false, false, false)
        }

        val characterDefinition: CharacterDefinition = unkDictionary.getCharacterDefinition()

        viterbi = KuromojiViterbiNBest(
            fst,
            fstReader,
            systemDictionary,
            userFST,
            userFSTReader,
            userDictionary,
            connectionCosts,
            unkDictionary,
            characterDefinition,
            discardPunctuation,
            searchMode,
            extendedMode,
            outputCompounds
        )
        viterbi.resetBuffer(this.input)
        viterbi.resetState()
    }

    /** Expert: set this to produce graphviz (dot) output of the Viterbi lattice */
    fun setGraphvizFormatter(dotOut: GraphvizFormatter<JaMorphData>) {
        viterbi.setGraphvizFormatter(dotOut)
    }

    override fun close() {
        super.close()
        viterbi.resetBuffer(input)
    }

    override fun reset() {
        super.reset()
        viterbi.resetBuffer(input)
        viterbi.resetState()
        lastTokenPos = -1
    }

    override fun end() {
        super.end()
        val finalOffset = correctOffset(viterbi.pos)
        offsetAtt.setOffset(finalOffset, finalOffset)
    }

    override fun incrementToken(): Boolean {
        // forward() can return without producing tokens (e.g., all punctuation), so loop.
        while (viterbi.pending.size == 0) {
            if (viterbi.isEndOfInput()) {
                return false
            }
            viterbi.forward()
        }

        val token = viterbi.pending.removeAt(viterbi.pending.size - 1)

        val length = token.length
        clearAttributes()
        termAtt.copyBuffer(token.surfaceForm, token.offset, length)
        offsetAtt.setOffset(correctOffset(token.startOffset), correctOffset(token.endOffset))
        basicFormAtt.setToken(token)
        posAtt.setToken(token)
        readingAtt.setToken(token)
        inflectionAtt.setToken(token)

        if (token.startOffset == lastTokenPos) {
            posIncAtt.setPositionIncrement(0)
            posLengthAtt.positionLength = token.positionLength
        } else if (viterbi.isOutputNBestEnabled()) {
            posIncAtt.setPositionIncrement(1)
            posLengthAtt.positionLength = token.positionLength
        } else {
            posIncAtt.setPositionIncrement(1)
            posLengthAtt.positionLength = 1
        }
        lastTokenPos = token.startOffset
        return true
    }

    @Throws(IOException::class)
    private fun probeDelta(inText: String, requiredToken: String): Int {
        val start = inText.indexOf(requiredToken)
        if (start < 0) {
            return -1
        }

        var delta = Int.MAX_VALUE
        val saveNBestCost = viterbi.getNBestCostValue()
        setReader(StringReader(inText))
        reset()
        try {
            setNBestCost(1)
            var prevRootBase = -1
            while (incrementToken()) {
                if (viterbi.getLatticeRootBase() != prevRootBase) {
                    prevRootBase = viterbi.getLatticeRootBase()
                    delta = minOf(delta, viterbi.probeDelta(start, start + requiredToken.length))
                }
            }
        } finally {
            end()
            close()
            setNBestCost(saveNBestCost)
        }

        return if (delta == Int.MAX_VALUE) -1 else delta
    }

    fun calcNBestCost(examples: String): Int {
        var maxDelta = 0
        for (example in examples.split("/")) {
            if (example.isNotEmpty()) {
                val pair = example.split("-")
                if (pair.size != 2) {
                    throw RuntimeException("Unexpected example form: $example (expected two '-')")
                } else {
                    try {
                        maxDelta = maxOf(maxDelta, probeDelta(pair[0], pair[1]))
                    } catch (e: IOException) {
                        throw RuntimeException("Internal error calculating best costs from examples. Got ", e)
                    }
                }
            }
        }
        return maxDelta
    }

    fun setNBestCost(value: Int) {
        viterbi.setNBestCostValue(value)
    }
}
