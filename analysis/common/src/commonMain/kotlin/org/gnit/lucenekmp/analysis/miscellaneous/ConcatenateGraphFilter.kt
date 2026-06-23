package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.TokenStreamToAutomaton
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.LimitedFiniteStringsIterator
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.TooComplexToDeterminizeException
import org.gnit.lucenekmp.util.automaton.Transition
import org.gnit.lucenekmp.util.fst.Util

/**
 * Concatenates/Joins every incoming token with a separator into one output token for every path
 * through the token stream (which is a graph). In simple cases this yields one token, but in the
 * presence of any tokens with a zero positionIncrmeent (e.g. synonyms) it will be more. This filter
 * uses the token bytes, position increment, and position length of the incoming stream. Other
 * attributes are not used or manipulated.
 *
 * @lucene.experimental
 */
class ConcatenateGraphFilter(
    private val inputTokenStream: TokenStream,
    private val tokenSeparator: Char?,
    private val preservePositionIncrements: Boolean,
    private val maxGraphExpansions: Int
) : TokenStream(
    object : AttributeFactory.StaticImplementationAttributeFactory<BytesRefBuilderTermAttributeImpl>(
        AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY,
        BytesRefBuilderTermAttributeImpl::class
    ) {
        override fun createInstance(): BytesRefBuilderTermAttributeImpl {
            return BytesRefBuilderTermAttributeImpl()
        }
    }
) {
    /*
     * Token stream which converts a provided token stream to an automaton.
     * The accepted strings enumeration from the automaton are available through the
     * {@link org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute} attribute
     * The token stream uses a {@link org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute} to store
     * a completion's payload (see {@link ConcatenateGraphFilter#setPayload(org.gnit.lucenekmp.util.BytesRef)})
     */

    companion object {
        init {
            AttributeSource.registerAttributeInterfaces(
                BytesRefBuilderTermAttributeImpl::class,
                arrayOf(BytesRefBuilderTermAttribute::class, TermToBytesRefAttribute::class)
            )
        }

        /** Represents the default separator between tokens. */
        const val SEP_LABEL = TokenStreamToAutomaton.POS_SEP

        const val DEFAULT_MAX_GRAPH_EXPANSIONS = Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        val DEFAULT_TOKEN_SEPARATOR: Char = SEP_LABEL.toChar()
        const val DEFAULT_PRESERVE_SEP = true
        const val DEFAULT_PRESERVE_POSITION_INCREMENTS = true

        // Replaces SEP with epsilon or remaps them if
        // we were asked to preserve them:
        private fun replaceSep(a: Automaton, tokenSeparator: Char?): Automaton {
            val result = Automaton()

            val numStates = a.numStates
            for (s in 0 until numStates) {
                result.createState()
                result.setAccept(s, a.isAccept(s))
            }

            val t = Transition()
            val topoSortStates = Operations.topoSortStates(a)
            for (i in topoSortStates.indices) {
                val state = topoSortStates[topoSortStates.size - 1 - i]
                val count = a.initTransition(state, t)
                repeat(count) {
                    a.getNextTransition(t)
                    if (t.min == TokenStreamToAutomaton.POS_SEP) {
                        check(t.max == TokenStreamToAutomaton.POS_SEP)
                        if (tokenSeparator != null) {
                            result.addTransition(state, t.dest, tokenSeparator.code)
                        } else {
                            result.addEpsilon(state, t.dest)
                        }
                    } else if (t.min == TokenStreamToAutomaton.HOLE) {
                        check(t.max == TokenStreamToAutomaton.HOLE)
                        result.addEpsilon(state, t.dest)
                    } else {
                        result.addTransition(state, t.dest, t.min, t.max)
                    }
                }
            }

            result.finishState()
            return result
        }
    }

    /**
     * Attribute providing access to the term builder and UTF-16 conversion
     *
     * @lucene.internal
     */
    interface BytesRefBuilderTermAttribute : TermToBytesRefAttribute {
        /** Returns the builder from which the term is derived. */
        fun builder(): BytesRefBuilder

        /** Returns the term represented as UTF-16 */
        fun toUTF16(): CharSequence
    }

    /**
     * Implementation of [BytesRefBuilderTermAttribute]
     *
     * @lucene.internal
     */
    class BytesRefBuilderTermAttributeImpl : AttributeImpl(), BytesRefBuilderTermAttribute, TermToBytesRefAttribute {
        private val bytes = BytesRefBuilder()
        private var charsRef: CharsRefBuilder? = null

        override fun builder(): BytesRefBuilder {
            return bytes
        }

        override val bytesRef: BytesRef
            get() = bytes.get()

        override fun clear() {
            bytes.clear()
        }

        override fun copyTo(target: AttributeImpl) {
            val other = target as BytesRefBuilderTermAttributeImpl
            other.bytes.copyBytes(bytes)
        }

        override fun reflectWith(reflector: AttributeReflector) {
            reflector.reflect(TermToBytesRefAttribute::class, "bytes", bytesRef)
        }

        override fun toUTF16(): CharSequence {
            if (charsRef == null) {
                charsRef = CharsRefBuilder()
            }
            charsRef!!.copyUTF8Bytes(bytesRef)
            return charsRef!!.get()
        }

        override fun newInstance(): AttributeImpl {
            return BytesRefBuilderTermAttributeImpl()
        }
    }

    private val bytesAtt: BytesRefBuilderTermAttribute = addAttribute(BytesRefBuilderTermAttribute::class)
    private val charTermAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val posIncrAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

    private var finiteStrings: LimitedFiniteStringsIterator? = null
    private var charTermAttribute: CharTermAttribute? = null
    private var wasReset = false
    private var endOffset = -1

    /**
     * Creates a token stream to convert `input` to a token stream of accepted strings by
     * its token stream graph.
     *
     * This constructor uses the default settings of the constants in this class.
     */
    constructor(inputTokenStream: TokenStream) : this(
        inputTokenStream,
        DEFAULT_TOKEN_SEPARATOR,
        DEFAULT_PRESERVE_POSITION_INCREMENTS,
        DEFAULT_MAX_GRAPH_EXPANSIONS
    )

    /**
     * Creates a token stream to convert `input` to a token stream of accepted strings by
     * its token stream graph.
     *
     * @param inputTokenStream The input/incoming TokenStream
     * @param tokenSeparator Separator to use for concatenation. Can be null, in this case tokens will
     *     be concatenated without any separators.
     * @param preservePositionIncrements Whether to add an empty token for missing positions. The
     *     effect is a consecutive [SEP_LABEL]. When false, it's as if there were no missing
     *     positions (we pretend the surrounding tokens were adjacent).
     * @param maxGraphExpansions If the tokenStream graph has more than this many possible paths
     *     through, then we'll throw [TooComplexToDeterminizeException] to preserve the
     *     stability and memory of the machine.
     * @throws TooComplexToDeterminizeException if the tokenStream graph has more than `maxGraphExpansions` expansions
     */
    /**
     * Calls [ConcatenateGraphFilter]
     *
     * @param preserveSep Whether [SEP_LABEL] should separate the input tokens in the
     *     concatenated token
     */
    constructor(
        inputTokenStream: TokenStream,
        preserveSep: Boolean,
        preservePositionIncrements: Boolean,
        maxGraphExpansions: Int
    ) : this(
        inputTokenStream,
        if (preserveSep) DEFAULT_TOKEN_SEPARATOR else null,
        preservePositionIncrements,
        maxGraphExpansions
    )

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        charTermAttribute = charTermAtt
        check(getAttribute(TermToBytesRefAttribute::class) is BytesRefBuilderTermAttributeImpl)
        wasReset = true
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (finiteStrings == null) {
            check(wasReset) { "reset() missing before incrementToken" }
            val automaton = toAutomaton()
            finiteStrings = LimitedFiniteStringsIterator(automaton, maxGraphExpansions)
            endOffset = inputTokenStream.getAttribute(OffsetAttribute::class)!!.endOffset()
        }

        val string: IntsRef = finiteStrings!!.next() ?: return false

        clearAttributes()

        if (finiteStrings!!.size() > 1) {
            posIncrAtt.setPositionIncrement(0)
        }

        offsetAtt.setOffset(0, endOffset)

        Util.toBytesRef(string, bytesAtt.builder())
        charTermAttribute?.let {
            it.setLength(0)
            it.append(bytesAtt.toUTF16())
        }

        return true
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        if (finiteStrings == null) {
            inputTokenStream.end()
        }
        if (endOffset != -1) {
            offsetAtt.setOffset(0, endOffset)
        }
    }

    override fun close() {
        super.close()
        inputTokenStream.close()
        finiteStrings = null
        wasReset = false
        endOffset = -1
    }

    /**
     * Converts the tokenStream to an automaton, treating the transition labels as utf-8. Does *not*
     * close it.
     */
    @Throws(IOException::class)
    fun toAutomaton(): Automaton {
        return toAutomaton(false)
    }

    /** Converts the tokenStream to an automaton. Does *not* close it. */
    @Throws(IOException::class)
    fun toAutomaton(unicodeAware: Boolean): Automaton {
        val tsta =
            if (tokenSeparator != null) {
                EscapingTokenStreamToAutomaton(tokenSeparator.code)
            } else {
                TokenStreamToAutomaton()
            }
        tsta.setPreservePositionIncrements(preservePositionIncrements)
        tsta.setUnicodeArcs(unicodeAware)

        var automaton = tsta.toAutomaton(inputTokenStream)
        automaton = replaceSep(automaton, tokenSeparator)
        return Operations.determinize(automaton, maxGraphExpansions)
    }

    /** Just escapes the [SEP_LABEL] byte with an extra. */
    private class EscapingTokenStreamToAutomaton(sepLabel: Int) : TokenStreamToAutomaton() {
        private val spare = BytesRefBuilder()
        private val sepLabel: Byte

        init {
            check(sepLabel <= Byte.MAX_VALUE)
            this.sepLabel = sepLabel.toByte()
        }

        override fun changeToken(`in`: BytesRef): BytesRef {
            var upto = 0
            for (i in 0 until `in`.length) {
                val b = `in`.bytes[`in`.offset + i]
                if (b == sepLabel) {
                    spare.grow(upto + 2)
                    spare.setByteAt(upto++, sepLabel)
                    spare.setByteAt(upto++, b)
                } else {
                    spare.grow(upto + 1)
                    spare.setByteAt(upto++, b)
                }
            }
            spare.setLength(upto)
            return spare.get()
        }
    }
}
