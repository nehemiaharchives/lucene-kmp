package org.gnit.lucenekmp.analysis.es.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.es.SpanishAnalyzer
import org.gnit.lucenekmp.analysis.es.SpanishLightStemFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

/** Analyzer for Spanish bible text with Jesucristo compound expansion. */
class BibleSpanishAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    constructor() : this(SpanishAnalyzer.getDefaultStopSet())

    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = BibleSpanishJesusChristFilter(result, emitOriginal = true)
        result = SpanishLightStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }
}

private class BibleSpanishJesusChristFilter(
    input: TokenStream,
    private val emitOriginal: Boolean
) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val pendingTerms = ArrayDeque<CharArray>()
    private var pendingState: State? = null

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (pendingTerms.isNotEmpty()) {
            restoreState(pendingState!!)
            val pending = pendingTerms.removeFirst()
            posIncAtt.setPositionIncrement(0)
            termAtt.copyBuffer(pending, 0, pending.size)
            if (pendingTerms.isEmpty()) {
                pendingState = null
            }
            return true
        }

        if (!input.incrementToken()) {
            return false
        }

        if (matches(termAtt.buffer(), termAtt.length, JESUCRISTO)) {
            if (emitOriginal) {
                pendingState = captureState()
                pendingTerms.add(JESUS)
                pendingTerms.add(CRISTO)
            } else {
                termAtt.copyBuffer(JESUCRISTO, 0, JESUCRISTO.size)
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

    companion object {
        private val JESUCRISTO = charArrayOf('j', 'e', 's', 'u', 'c', 'r', 'i', 's', 't', 'o')
        private val JESUS = charArrayOf('j', 'e', 's', 'u', 's')
        private val CRISTO = charArrayOf('c', 'r', 'i', 's', 't', 'o')
    }
}
