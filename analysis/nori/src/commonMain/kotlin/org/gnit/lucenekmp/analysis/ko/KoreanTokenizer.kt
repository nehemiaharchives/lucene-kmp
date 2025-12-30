package org.gnit.lucenekmp.analysis.ko

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.ko.dict.ConnectionCosts
import org.gnit.lucenekmp.analysis.ko.dict.KoMorphData
import org.gnit.lucenekmp.analysis.ko.dict.TokenInfoDictionary
import org.gnit.lucenekmp.analysis.ko.dict.TokenInfoFST
import org.gnit.lucenekmp.analysis.ko.dict.UnknownDictionary
import org.gnit.lucenekmp.analysis.ko.dict.UserDictionary
import org.gnit.lucenekmp.analysis.ko.tokenattributes.PartOfSpeechAttribute
import org.gnit.lucenekmp.analysis.ko.tokenattributes.ReadingAttribute
import org.gnit.lucenekmp.analysis.morph.GraphvizFormatter
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.util.AttributeFactory
import okio.IOException

/**
 * Tokenizer for Korean that uses morphological analysis.
 */
class KoreanTokenizer : Tokenizer {
    /**
     * Decompound mode: this determines how the tokenizer handles [POS.Type.COMPOUND],
     * [POS.Type.INFLECT] and [POS.Type.PREANALYSIS] tokens.
     */
    enum class DecompoundMode {
        /** No decomposition for compound. */
        NONE,
        /** Decompose compounds and discards the original form (default). */
        DISCARD,
        /** Decompose compounds and keeps the original form. */
        MIXED
    }

    companion object {
        /** Default mode for the decompound of tokens ([DecompoundMode.DISCARD]. */
        val DEFAULT_DECOMPOUND: DecompoundMode = DecompoundMode.DISCARD
        private const val VERBOSE = false
    }

    private val viterbi: Viterbi

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val posLengthAtt: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)
    init {
        addAttributeImpl(org.gnit.lucenekmp.analysis.ko.tokenattributes.PartOfSpeechAttributeImpl())
        addAttributeImpl(org.gnit.lucenekmp.analysis.ko.tokenattributes.ReadingAttributeImpl())
    }

    private val posAtt: PartOfSpeechAttribute = addAttribute(PartOfSpeechAttribute::class)
    private val readingAtt: ReadingAttribute = addAttribute(ReadingAttribute::class)

    /** Creates a new KoreanTokenizer with default parameters. */
    constructor() : this(DEFAULT_TOKEN_ATTRIBUTE_FACTORY, null, DEFAULT_DECOMPOUND, false, true)

    /**
     * Create a new KoreanTokenizer using the system and unknown dictionaries shipped with Lucene.
     */
    constructor(
        factory: AttributeFactory,
        userDictionary: UserDictionary?,
        mode: DecompoundMode,
        outputUnknownUnigrams: Boolean
    ) : this(factory, userDictionary, mode, outputUnknownUnigrams, true)

    /**
     * Create a new KoreanTokenizer using the system and unknown dictionaries shipped with Lucene.
     */
    constructor(
        factory: AttributeFactory,
        userDictionary: UserDictionary?,
        mode: DecompoundMode,
        outputUnknownUnigrams: Boolean,
        discardPunctuation: Boolean
    ) : this(
        factory,
        TokenInfoDictionary.getInstance(),
        UnknownDictionary.getInstance(),
        ConnectionCosts.getInstance(),
        userDictionary,
        mode,
        outputUnknownUnigrams,
        discardPunctuation
    )

    /**
     * Create a new KoreanTokenizer supplying a custom system dictionary and unknown dictionary.
     */
    constructor(
        factory: AttributeFactory,
        systemDictionary: TokenInfoDictionary,
        unkDictionary: UnknownDictionary,
        connectionCosts: ConnectionCosts,
        userDictionary: UserDictionary?,
        mode: DecompoundMode,
        outputUnknownUnigrams: Boolean,
        discardPunctuation: Boolean
    ) : super(factory) {
        val fst: TokenInfoFST = systemDictionary.getFST()
        val fstReader = fst.getBytesReader()
        var userFST: TokenInfoFST? = null
        var userFSTReader = null as org.gnit.lucenekmp.util.fst.FST.BytesReader?
        if (userDictionary != null) {
            userFST = userDictionary.getFST()
            userFSTReader = userFST.getBytesReader()
        }
        viterbi = Viterbi(
            fst,
            fstReader,
            systemDictionary,
            userFST,
            userFSTReader,
            userDictionary,
            connectionCosts,
            unkDictionary,
            unkDictionary.getCharacterDefinition(),
            discardPunctuation,
            mode,
            outputUnknownUnigrams
        )
        viterbi.resetBuffer(input)
        viterbi.resetState()
    }

    override fun close() {
        super.close()
        viterbi.resetBuffer(input)
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        viterbi.resetBuffer(input)
        viterbi.resetState()
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        val finalOffset = correctOffset(viterbi.pos)
        offsetAtt.setOffset(finalOffset, finalOffset)
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        while (viterbi.pending.isEmpty()) {
            if (viterbi.isEnd()) {
                return false
            }
            viterbi.forward()
        }

        val token = viterbi.pending.removeAt(viterbi.pending.size - 1)
        val length = token.length
        clearAttributes()
        termAtt.copyBuffer(token.surfaceForm, token.offset, length)
        offsetAtt.setOffset(correctOffset(token.startOffset), correctOffset(token.endOffset))
        posAtt.setToken(token)
        readingAtt.setToken(token)
        posIncAtt.setPositionIncrement(token.positionIncrement)
        posLengthAtt.positionLength = token.positionLength
        if (VERBOSE) {
            println("incToken: return token=$token")
        }
        return true
    }

    /** Expert: set this to produce graphviz (dot) output of the Viterbi lattice */
    fun setGraphvizFormatter(dotOut: GraphvizFormatter<KoMorphData>) {
        viterbi.setGraphvizFormatter(dotOut)
    }
}
