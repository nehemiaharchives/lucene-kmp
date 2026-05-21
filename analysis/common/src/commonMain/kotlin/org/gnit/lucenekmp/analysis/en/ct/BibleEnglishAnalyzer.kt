package org.gnit.lucenekmp.analysis.en.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.en.EnglishAnalyzer
import org.gnit.lucenekmp.analysis.en.EnglishPossessiveFilter
import org.gnit.lucenekmp.analysis.en.PorterStemFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

class BibleEnglishAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    constructor() : this(EnglishAnalyzer.getDefaultStopSet())

    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = EnglishPossessiveFilter(source)
        result = LowerCaseFilter(result)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = BibleEnglishIrregularVerbFilter(result, emitOriginal = false)
        result = PorterStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = BibleEnglishIrregularVerbFilter(result, emitOriginal = false)
        return result
    }
}

private class BibleEnglishIrregularVerbFilter(
    input: TokenStream,
    private val emitOriginal: Boolean
) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private var pendingState: State? = null

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (pendingState != null) {
            restoreState(pendingState!!)
            pendingState = null
            posIncAtt.setPositionIncrement(0)
            termAtt.copyBuffer(WEEP, 0, WEEP.size)
            return true
        }

        if (!input.incrementToken()) {
            return false
        }

        if (matches(termAtt.buffer(), termAtt.length, WEPT)) {
            if (emitOriginal) {
                pendingState = captureState()
            } else {
                termAtt.copyBuffer(WEEP, 0, WEEP.size)
            }
        }

        return true
    }

    override fun reset() {
        super.reset()
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
        private val WEPT = charArrayOf('w', 'e', 'p', 't')
        private val WEEP = charArrayOf('w', 'e', 'e', 'p')
    }
}
