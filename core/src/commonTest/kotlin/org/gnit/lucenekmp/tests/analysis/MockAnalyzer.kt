package org.gnit.lucenekmp.tests.analysis

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.automaton.CharacterRunAutomaton
import kotlin.random.Random

/** Analyzer for testing purposes. */
class MockAnalyzer(
    random: Random,
    private val runAutomaton: CharacterRunAutomaton,
    private val lowerCase: Boolean,
    private val filter: CharacterRunAutomaton
) : Analyzer(PER_FIELD_REUSE_STRATEGY) {

    private val random: Random = Random(random.nextLong())
    private var positionIncrementGap: Int = 0
    private var offsetGap: Int? = null
    private val previousMappings = mutableMapOf<String, Int>()
    private var enableChecks = true
    private var maxTokenLength = MockTokenizer.DEFAULT_MAX_TOKEN_LENGTH

    constructor(random: Random, runAutomaton: CharacterRunAutomaton, lowerCase: Boolean) :
            this(random, runAutomaton, lowerCase, MockTokenFilter.EMPTY_STOPSET)

    constructor(random: Random) : this(random, MockTokenizer.WHITESPACE, true)

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tokenizer = MockTokenizer(runAutomaton, lowerCase, maxTokenLength)
        tokenizer.setEnableChecks(enableChecks)
        val filt = MockTokenFilter(tokenizer, filter)
        return TokenStreamComponents(tokenizer, maybePayload(filt, fieldName))
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = `in`
        if (lowerCase) {
            result = MockLowerCaseFilter(result)
        }
        return result
    }

    private fun maybePayload(stream: TokenFilter, fieldName: String): TokenFilter {
        var valInt = previousMappings[fieldName]
        if (valInt == null) {
            var v = -1
            if (TestUtil.rarely(random)) {
                when (random.nextInt(3)) {
                    0 -> v = -1
                    1 -> v = Int.MAX_VALUE
                    2 -> v = random.nextInt(12)
                }
            }
            if (LuceneTestCase.VERBOSE) {
                when (v) {
                    Int.MAX_VALUE -> println("MockAnalyzer: field=$fieldName gets variable length payloads")
                    -1 -> {}
                    else -> println("MockAnalyzer: field=$fieldName gets fixed length=$v payloads")
                }
            }
            previousMappings[fieldName] = v
            valInt = v
        }
        return when (valInt) {
            -1 -> stream
            Int.MAX_VALUE -> MockVariableLengthPayloadFilter(random, stream)
            else -> MockFixedLengthPayloadFilter(random, stream, valInt!!)
        }
    }

    fun setPositionIncrementGap(gap: Int) {
        this.positionIncrementGap = gap
    }

    override fun getPositionIncrementGap(fieldName: String?): Int {
        return positionIncrementGap
    }

    fun setOffsetGap(gap: Int) {
        this.offsetGap = gap
    }

    override fun getOffsetGap(fieldName: String?): Int {
        return offsetGap ?: super.getOffsetGap(fieldName)
    }

    /** Toggle consumer workflow checking. */
    fun setEnableChecks(enable: Boolean) {
        this.enableChecks = enable
    }

    /** Toggle maxTokenLength for MockTokenizer */
    fun setMaxTokenLength(length: Int) {
        this.maxTokenLength = length
    }
}
