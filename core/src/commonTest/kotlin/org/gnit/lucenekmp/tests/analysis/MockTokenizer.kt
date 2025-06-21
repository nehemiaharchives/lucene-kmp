package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.automaton.CharacterRunAutomaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.isHighSurrogate
import org.gnit.lucenekmp.jdkport.isLowSurrogate
import org.gnit.lucenekmp.jdkport.toCodePoint
import kotlin.random.Random

/**
 * Tokenizer for testing.
 *
 * This tokenizer is a replacement for WHITESPACE, SIMPLE, and KEYWORD tokenizers.
 * It provides extra checks useful for testing.
 */
class MockTokenizer : Tokenizer {
    companion object {
        /** Acts similar to WhitespaceTokenizer */
        val WHITESPACE: CharacterRunAutomaton = CharacterRunAutomaton(
            Operations.determinize(RegExp("[^ \t\r\n]+").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        )

        /** Acts similar to KeywordTokenizer */
        val KEYWORD: CharacterRunAutomaton = CharacterRunAutomaton(
            Operations.determinize(RegExp(".*").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        )

        /** Acts like LetterTokenizer (partial Unicode [:Letter:]) */
        val SIMPLE: CharacterRunAutomaton = CharacterRunAutomaton(
            Operations.determinize(
                RegExp("[A-Za-zªµºÀ-ÖØ-öø-ˁ一-鿌]+").toAutomaton(),
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
            )
        )

        /** Limit the default token length. */
        const val DEFAULT_MAX_TOKEN_LENGTH = 255
    }

    private val runAutomaton: CharacterRunAutomaton
    private val lowerCase: Boolean
    private val maxTokenLength: Int
    private var state = 0

    private val termAtt = addAttribute(CharTermAttribute::class)
    private val offsetAtt = addAttribute(OffsetAttribute::class)
    private var off = 0

    private var bufferedCodePoint = -1
    private var bufferedOff = -1

    private enum class State { SETREADER, RESET, INCREMENT, INCREMENT_FALSE, END, CLOSE }
    private var streamState = State.CLOSE
    private var lastOffset = 0
    private var enableChecks = true

    private val random = Random(LuceneTestCase.random().nextLong())

    constructor(
        factory: AttributeFactory,
        runAutomaton: CharacterRunAutomaton,
        lowerCase: Boolean,
        maxTokenLength: Int = DEFAULT_MAX_TOKEN_LENGTH
    ) : super(factory) {
        this.runAutomaton = runAutomaton
        this.lowerCase = lowerCase
        this.maxTokenLength = maxTokenLength
    }

    constructor(
        runAutomaton: CharacterRunAutomaton,
        lowerCase: Boolean,
        maxTokenLength: Int = DEFAULT_MAX_TOKEN_LENGTH
    ) : super() {
        this.runAutomaton = runAutomaton
        this.lowerCase = lowerCase
        this.maxTokenLength = maxTokenLength
    }

    constructor() : this(WHITESPACE, true)

    constructor(factory: AttributeFactory) : this(factory, WHITESPACE, true)

    private fun fail(message: String) {
        if (enableChecks) {
            throw IllegalStateException(message)
        }
    }

    private fun failAlways(message: String): Nothing {
        throw IllegalStateException(message)
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (streamState != State.RESET && streamState != State.INCREMENT) {
            fail("incrementToken() called while in wrong state: $streamState")
        }
        clearAttributes()
        while (true) {
            val startOffset: Int
            var cp: Int
            if (bufferedCodePoint >= 0) {
                cp = bufferedCodePoint
                startOffset = bufferedOff
                bufferedCodePoint = -1
            } else {
                startOffset = off
                cp = readCodePoint()
            }
            if (cp < 0) {
                break
            } else if (isTokenChar(cp)) {
                val chars = CharArray(2)
                var endOffset: Int
                do {
                    val len = Character.toChars(normalize(cp), chars, 0)
                    for (i in 0 until len) {
                        termAtt.append(chars[i])
                    }
                    endOffset = off
                    if (termAtt.length >= maxTokenLength) {
                        break
                    }
                    cp = readCodePoint()
                } while (cp >= 0 && isTokenChar(cp))

                if (termAtt.length < maxTokenLength) {
                    bufferedCodePoint = cp
                    bufferedOff = endOffset
                } else {
                    bufferedCodePoint = -1
                }
                val correctedStartOffset = correctOffset(startOffset)
                val correctedEndOffset = correctOffset(endOffset)
                if (correctedStartOffset < 0) {
                    failAlways("invalid start offset: $correctedStartOffset, before correction: $startOffset")
                }
                if (correctedEndOffset < 0) {
                    failAlways("invalid end offset: $correctedEndOffset, before correction: $endOffset")
                }
                if (correctedStartOffset < lastOffset) {
                    failAlways("start offset went backwards: $correctedStartOffset, before correction: $startOffset, lastOffset: $lastOffset")
                }
                lastOffset = correctedStartOffset
                if (correctedEndOffset < correctedStartOffset) {
                    failAlways("end offset: $correctedEndOffset is before start offset: $correctedStartOffset")
                }
                offsetAtt.setOffset(correctedStartOffset, correctedEndOffset)
                if (state == -1 || runAutomaton.isAccept(state)) {
                    streamState = State.INCREMENT
                    return true
                }
            }
        }
        streamState = State.INCREMENT_FALSE
        return false
    }

    @Throws(IOException::class)
    protected open fun readCodePoint(): Int {
        val ch = readChar()
        return if (ch < 0) {
            ch
        } else {
            if (ch.toChar().isLowSurrogate()) {
                failAlways("unpaired low surrogate: ${ch.toString(16)}")
            }
            off++
            if (ch.toChar().isHighSurrogate()) {
                val ch2 = readChar()
                if (ch2 >= 0) {
                    off++
                    if (!ch2.toChar().isLowSurrogate()) {
                        failAlways(
                            "unpaired high surrogate: ${ch.toString(16)}, followed by: ${ch2.toString(16)}"
                        )
                    }
                    return toCodePoint(ch.toChar(), ch2.toChar())
                } else {
                    failAlways("stream ends with unpaired high surrogate: ${ch.toString(16)}")
                }
            }
            ch
        }
    }

    @Throws(IOException::class)
    protected open fun readChar(): Int {
        return when (random.nextInt(10)) {
            0 -> {
                val c = CharArray(1)
                val ret = input.read(c, 0, 1)
                if (ret < 0) ret else c[0].code
            }
            1 -> {
                val c = CharArray(2)
                val ret = input.read(c, 1, 1)
                if (ret < 0) ret else c[1].code
            }
            2 -> {
                val c = CharArray(1)
                val cb = org.gnit.lucenekmp.jdkport.CharBuffer.wrap(c)
                val ret = input.read(cb)
                if (ret < 0) ret else c[0].code
            }
            else -> input.read()
        }
    }

    protected open fun isTokenChar(c: Int): Boolean {
        if (state < 0) {
            state = 0
        }
        state = runAutomaton.step(state, c)
        return state >= 0
    }

    protected open fun normalize(c: Int): Int = if (lowerCase) Character.toLowerCase(c) else c

    @Throws(IOException::class)
    override fun reset() {
        try {
            super.reset()
            state = 0
            lastOffset = 0
            off = 0
            bufferedCodePoint = -1
            if (streamState == State.RESET) {
                fail("double reset()")
            }
        } finally {
            streamState = State.RESET
        }
    }

    override fun close() {
        try {
            super.close()
            if (streamState != State.END && streamState != State.CLOSE) {
                fail("close() called in wrong state: $streamState")
            }
        } finally {
            streamState = State.CLOSE
        }
    }

    override fun end() {
        try {
            super.end()
            val finalOffset = correctOffset(off)
            offsetAtt.setOffset(finalOffset, finalOffset)
            if (streamState != State.INCREMENT_FALSE) {
                fail("end() called in wrong state=$streamState!")
            }
        } finally {
            streamState = State.END
        }
    }


    /** Toggle consumer workflow checking */
    fun setEnableChecks(enable: Boolean) {
        this.enableChecks = enable
    }
}
