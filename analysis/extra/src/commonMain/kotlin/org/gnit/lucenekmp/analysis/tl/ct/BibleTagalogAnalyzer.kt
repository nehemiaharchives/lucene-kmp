package org.gnit.lucenekmp.analysis.tl.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tl.TagalogAnalyzer
import org.gnit.lucenekmp.analysis.tl.TagalogNormalizationFilter
import org.gnit.lucenekmp.analysis.tl.TagalogStemFilter
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

/** Analyzer for Tagalog bible text with Jesucristo compound expansion. */
class BibleTagalogAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    constructor() : this(TagalogAnalyzer.getDefaultStopSet())

    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = DecimalDigitFilter(result)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = TagalogNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = BibleTagalogJesusChristFilter(result)
        result = TagalogStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = TagalogNormalizationFilter(result)
        return result
    }
}

private class BibleTagalogJesusChristFilter(input: TokenStream) : TokenFilter(input) {
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
            pendingState = captureState()
            pendingTerms.add(JESUS)
            pendingTerms.add(CRISTO)
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
