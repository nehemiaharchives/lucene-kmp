package org.gnit.lucenekmp.analysis.cjk

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.AttributeSource

/**
 * Forms bigrams of CJK terms that are generated from StandardTokenizer or ICUTokenizer.
 *
 * CJK types are set by these tokenizers, but you can also use [CJKBigramFilter] to explicitly
 * control which of the CJK scripts are turned into bigrams.
 *
 * By default, when a CJK character has no adjacent characters to form a bigram, it is output in
 * unigram form. If you want to always output both unigrams and bigrams, set the [outputUnigrams]
 * flag in [CJKBigramFilter].
 *
 * Unlike ICUTokenizer, StandardTokenizer does not split at script boundaries. Korean Hangul
 * characters are treated the same as many other scripts' letters, and as a result, StandardTokenizer
 * can produce tokens that mix Hangul and non-Hangul characters, e.g. "한국abc".
 *
 * In all cases, all non-CJK input is passed thru unmodified.
 */
class CJKBigramFilter : TokenFilter {
    // these are set to either their type or NO if we want to pass them thru
    private val doHan: Any
    private val doHiragana: Any
    private val doKatakana: Any
    private val doHangul: Any

    // true if we should output unigram tokens always
    private val outputUnigrams: Boolean
    private var ngramState = false // false = output unigram, true = output bigram

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val posLengthAtt: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)

    // buffers containing codepoint and offsets in parallel
    private var buffer: IntArray = IntArray(8)
    private var startOffset: IntArray = IntArray(8)
    private var endOffset: IntArray = IntArray(8)

    // length of valid buffer
    private var bufferLen = 0

    // current buffer index
    private var index = 0

    // the last end offset, to determine if we should bigram across tokens
    private var lastEndOffset = 0

    private var exhausted = false

    // rarely used: only for "lone cjk characters", where we emit unigrams
    private var loneState: AttributeSource.State? = null

    /**
     * Calls [CJKBigramFilter] with default flags.
     */
    constructor(input: TokenStream) : this(input, HAN or HIRAGANA or KATAKANA or HANGUL)

    /**
     * Calls [CJKBigramFilter] with [outputUnigrams] set to false.
     */
    constructor(input: TokenStream, flags: Int) : this(input, flags, false)

    /**
     * Create a new CJKBigramFilter, specifying which writing systems should be bigrammed, and
     * whether or not unigrams should also be output.
     *
     * @param flags OR'ed set from [HAN], [HIRAGANA], [KATAKANA], [HANGUL]
     * @param outputUnigrams true if unigrams for the selected writing systems should also be output.
     * when this is false, this is only done when there are no adjacent characters to form a bigram.
     */
    constructor(input: TokenStream, flags: Int, outputUnigrams: Boolean) : super(input) {
        doHan = if (flags and HAN == 0) NO else HAN_TYPE
        doHiragana = if (flags and HIRAGANA == 0) NO else HIRAGANA_TYPE
        doKatakana = if (flags and KATAKANA == 0) NO else KATAKANA_TYPE
        doHangul = if (flags and HANGUL == 0) NO else HANGUL_TYPE
        this.outputUnigrams = outputUnigrams
    }

    /*
     * much of this complexity revolves around handling the special case of a
     * "lone cjk character" where cjktokenizer would output a unigram. this
     * is also the only time we ever have to captureState.
     */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        while (true) {
            if (hasBufferedBigram()) {
                // case 1: we have multiple remaining codepoints buffered,
                // so we can emit a bigram here.
                if (outputUnigrams) {
                    // when also outputting unigrams, we output the unigram first,
                    // then rewind back to revisit the bigram.
                    // so an input of ABC is A + (rewind)AB + B + (rewind)BC + C
                    // the logic in hasBufferedUnigram ensures we output the C,
                    // even though it did actually have adjacent CJK characters.
                    if (ngramState) {
                        flushBigram()
                    } else {
                        flushUnigram()
                        index--
                    }
                    ngramState = !ngramState
                } else {
                    flushBigram()
                }
                return true
            } else if (doNext()) {
                // case 2: look at the token type. should we form any n-grams?
                val type: String = typeAtt.type()
                if (type == doHan || type == doHiragana || type == doKatakana || type == doHangul) {
                    // acceptable CJK type: we form n-grams from these.
                    // as long as the offsets are aligned, we just add these to our current buffer.
                    // otherwise, we clear the buffer and start over.
                    if (offsetAtt.startOffset() != lastEndOffset) { // unaligned, clear queue
                        if (hasBufferedUnigram()) {
                            // we have a buffered unigram, and we peeked ahead to see if we could form
                            // a bigram, but we can't, because the offsets are unaligned. capture the state
                            // of this peeked data to be revisited next time thru the loop, and dump our unigram.
                            loneState = captureState()
                            flushUnigram()
                            return true
                        }
                        index = 0
                        bufferLen = 0
                    }
                    refill()
                } else {
                    // not a CJK type: we just return these as-is.
                    if (hasBufferedUnigram()) {
                        // we have a buffered unigram, and we peeked ahead to see if we could form
                        // a bigram, but we can't, because it's not a CJK type. capture the state
                        // of this peeked data to be revisited next time thru the loop, and dump our unigram.
                        loneState = captureState()
                        flushUnigram()
                        return true
                    }
                    return true
                }
            } else {
                // case 3: we have only zero or 1 codepoints buffered,
                // so not enough to form a bigram. But, we also have no
                // more input. So if we have a buffered codepoint, emit
                // a unigram, otherwise, it's end of stream.
                if (hasBufferedUnigram()) {
                    flushUnigram() // flush our remaining unigram
                    return true
                }
                return false
            }
        }
    }

    /** looks at next input token, returning false is none is available */
    @Throws(IOException::class)
    private fun doNext(): Boolean {
        if (loneState != null) {
            restoreState(loneState)
            loneState = null
            return true
        } else if (exhausted) {
            return false
        } else if (input.incrementToken()) {
            return true
        } else {
            exhausted = true
            return false
        }
    }

    /** refills buffers with new data from the current token. */
    private fun refill() {
        // compact buffers to keep them smallish if they become large
        // just a safety check, but technically we only need the last codepoint
        if (bufferLen > 64) {
            val last = bufferLen - 1
            buffer[0] = buffer[last]
            startOffset[0] = startOffset[last]
            endOffset[0] = endOffset[last]
            bufferLen = 1
            index -= last
        }

        val termBuffer = termAtt.buffer()
        val len = termAtt.length
        var start = offsetAtt.startOffset()
        val end = offsetAtt.endOffset()

        val newSize = bufferLen + len
        buffer = ArrayUtil.grow(buffer, newSize)
        startOffset = ArrayUtil.grow(startOffset, newSize)
        endOffset = ArrayUtil.grow(endOffset, newSize)
        lastEndOffset = end

        if (end - start != len) {
            // crazy offsets (modified by synonym or charfilter): just preserve
            var i = 0
            var cp = 0
            while (i < len) {
                cp = Character.codePointAt(termBuffer, i, len)
                buffer[bufferLen] = cp
                startOffset[bufferLen] = start
                endOffset[bufferLen] = end
                bufferLen++
                i += Character.charCount(cp)
            }
        } else {
            // normal offsets
            var i = 0
            var cp = 0
            var cpLen = 0
            while (i < len) {
                cp = Character.codePointAt(termBuffer, i, len)
                buffer[bufferLen] = cp
                cpLen = Character.charCount(cp)
                startOffset[bufferLen] = start
                endOffset[bufferLen] = start + cpLen
                start = start + cpLen
                bufferLen++
                i += cpLen
            }
        }
    }

    /**
     * Flushes a bigram token to output from our buffer This is the normal case, e.g. ABC -> AB BC
     */
    private fun flushBigram() {
        clearAttributes()
        val termBuffer =
            termAtt.resizeBuffer(4) // maximum bigram length in code units (2 supplementaries)
        val len1 = Character.toChars(buffer[index], termBuffer, 0)
        val len2 = len1 + Character.toChars(buffer[index + 1], termBuffer, len1)
        termAtt.setLength(len2)
        offsetAtt.setOffset(startOffset[index], endOffset[index + 1])
        typeAtt.setType(DOUBLE_TYPE)
        // when outputting unigrams, all bigrams are synonyms that span two unigrams
        if (outputUnigrams) {
            posIncAtt.setPositionIncrement(0)
            posLengthAtt.positionLength = 2
        }
        index++
    }

    /**
     * Flushes a unigram token to output from our buffer. This happens when we encounter isolated CJK
     * characters, either the whole CJK string is a single character, or we encounter a CJK character
     * surrounded by space, punctuation, english, etc, but not beside any other CJK.
     */
    private fun flushUnigram() {
        clearAttributes()
        val termBuffer = termAtt.resizeBuffer(2) // maximum unigram length (2 surrogates)
        val len = Character.toChars(buffer[index], termBuffer, 0)
        termAtt.setLength(len)
        offsetAtt.setOffset(startOffset[index], endOffset[index])
        typeAtt.setType(SINGLE_TYPE)
        index++
    }

    /** True if we have multiple codepoints sitting in our buffer */
    private fun hasBufferedBigram(): Boolean {
        return bufferLen - index > 1
    }

    /**
     * True if we have a single codepoint sitting in our buffer, where its future (whether it is
     * emitted as unigram or forms a bigram) depends upon not-yet-seen inputs.
     */
    private fun hasBufferedUnigram(): Boolean {
        return if (outputUnigrams) {
            // when outputting unigrams always
            bufferLen - index == 1
        } else {
            // otherwise it's only when we have a lone CJK character
            bufferLen == 1 && index == 0
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        bufferLen = 0
        index = 0
        lastEndOffset = 0
        loneState = null
        exhausted = false
        ngramState = false
    }

    companion object {
        // configuration
        /** bigram flag for Han Ideographs */
        const val HAN: Int = 1

        /** bigram flag for Hiragana */
        const val HIRAGANA: Int = 2

        /** bigram flag for Katakana */
        const val KATAKANA: Int = 4

        /** bigram flag for Hangul */
        const val HANGUL: Int = 8

        /** when we emit a bigram, it's then marked as this type */
        const val DOUBLE_TYPE: String = "<DOUBLE>"

        /** when we emit a unigram, it's then marked as this type */
        const val SINGLE_TYPE: String = "<SINGLE>"

        // the types from standardtokenizer
        private val HAN_TYPE: String = StandardTokenizer.TOKEN_TYPES[StandardTokenizer.IDEOGRAPHIC]
        private val HIRAGANA_TYPE: String = StandardTokenizer.TOKEN_TYPES[StandardTokenizer.HIRAGANA]
        private val KATAKANA_TYPE: String = StandardTokenizer.TOKEN_TYPES[StandardTokenizer.KATAKANA]
        private val HANGUL_TYPE: String = StandardTokenizer.TOKEN_TYPES[StandardTokenizer.HANGUL]

        // sentinel value for ignoring a script
        private val NO: Any = Any()
    }
}
