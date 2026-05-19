package org.gnit.lucenekmp.analysis.vi.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.vi.VietnameseAnalyzer
import org.gnit.lucenekmp.analysis.vi.VietnameseConfig
import org.gnit.lucenekmp.analysis.vi.VietnameseNormalizationFilter
import org.gnit.lucenekmp.analysis.vi.VietnameseStemFilter
import org.gnit.lucenekmp.analysis.vi.VietnameseTokenizer

/** Analyzer for Vietnamese bible text with Jesus-Christ compound expansion. */
class BibleVietnameseAnalyzer(
    private val config: VietnameseConfig,
    stopWords: CharArraySet = VietnameseAnalyzer.getDefaultStopSet()
) : StopwordAnalyzerBase(stopWords) {
    constructor() : this(VietnameseConfig())

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = VietnameseTokenizer(config)
        var result: TokenStream = StopFilter(source, stopwords)
        result = VietnameseNormalizationFilter(result)
        result = BibleVietnameseJesusChristFilter(result)
        result = VietnameseStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return VietnameseNormalizationFilter(`in`)
    }
}

private class BibleVietnameseJesusChristFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val pendingTerms = ArrayDeque<PendingTerm>()
    private var pendingState: State? = null

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (pendingTerms.isNotEmpty()) {
            restoreState(pendingState!!)
            val pending = pendingTerms.removeFirst()
            posIncAtt.setPositionIncrement(pending.posInc)
            termAtt.copyBuffer(pending.term, 0, pending.term.size)
            if (pendingTerms.isEmpty()) {
                pendingState = null
            }
            return true
        }

        if (!input.incrementToken()) {
            return false
        }

        if (matches(termAtt.buffer(), termAtt.length, JESUS_CHRIST)) {
            pendingState = captureState()
            pendingTerms.add(PendingTerm(CHRIST, 1))
            termAtt.copyBuffer(JESUS, 0, JESUS.size)
            return true
        }

        val currentToken = termAtt.buffer().concatToString(0, termAtt.length)
        val normalizedToken = currentToken.trim()
        val contextEmissions = mutableListOf<PendingTerm>()
        if (normalizedToken != "jesus" && containsWholeWord(normalizedToken, "jesus")) {
            contextEmissions.add(PendingTerm(JESUS, 0))
        }
        if (normalizedToken != "christ" && containsWholeWord(normalizedToken, "christ")) {
            contextEmissions.add(PendingTerm(CHRIST, 0))
        }
        if (contextEmissions.isNotEmpty()) {
            pendingState = captureState()
            contextEmissions.forEach { emission ->
                pendingTerms.add(emission)
            }
        }

        return true
    }

    override fun reset() {
        super.reset()
        pendingTerms.clear()
        pendingState = null
    }

    private fun matches(buffer: CharArray, length: Int, target: CharArray): Boolean {
        if (length != target.size) {
            return false
        }
        for (i in 0 until length) {
            if (buffer[i] != target[i]) {
                return false
            }
        }
        return true
    }

    private fun containsWholeWord(text: String, target: String): Boolean {
        if (text.isEmpty()) {
            return false
        }
        return text.split(' ').any { part -> part == target }
    }

    private data class PendingTerm(
        val term: CharArray,
        val posInc: Int
    )

    companion object {
        private val JESUS_CHRIST = charArrayOf('j', 'e', 's', 'u', 's', ' ', 'c', 'h', 'r', 'i', 's', 't')
        private val JESUS = charArrayOf('j', 'e', 's', 'u', 's')
        private val CHRIST = charArrayOf('c', 'h', 'r', 'i', 's', 't')
    }
}
