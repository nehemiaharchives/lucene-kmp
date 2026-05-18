package org.gnit.lucenekmp.analysis.pt.ct

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.pt.PortugueseAnalyzer
import org.gnit.lucenekmp.analysis.pt.PortugueseLightStemFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer

/** Analyzer for Portuguese bible text with Jesus/Christ name normalization. */
class BiblePortugueseAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    constructor() : this(PortugueseAnalyzer.getDefaultStopSet())

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
        result = SetKeywordMarkerFilter(result, buildBibleNameExclusionSet())
        result = PortugueseLightStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    private fun buildBibleNameExclusionSet(): CharArraySet {
        val keywordSet = CharArraySet(stemExclusionSet.size + 2, true)
        for (item in stemExclusionSet) {
            keywordSet.add(item)
        }
        keywordSet.add("jesus")
        keywordSet.add("cristo")
        return keywordSet
    }
}
