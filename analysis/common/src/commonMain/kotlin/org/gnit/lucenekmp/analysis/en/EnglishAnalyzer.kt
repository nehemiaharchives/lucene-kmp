package org.gnit.lucenekmp.analysis.en

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer

/** Analyzer for English. */
class EnglishAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the default stop words. */
    constructor() : this(ENGLISH_STOP_WORDS_SET)

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /** Builds an analyzer with the given stop words and a stemming exclusion set. */
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
        result = PorterStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** An unmodifiable set containing common English stop words. */
        val ENGLISH_STOP_WORDS_SET: CharArraySet

        init {
            val stopWords = mutableListOf<Any>(
                "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is",
                "it", "no", "not", "of", "on", "or", "such", "that", "the", "their", "then", "there",
                "these", "they", "this", "to", "was", "will", "with"
            )
            val stopSet = CharArraySet(stopWords, false)
            ENGLISH_STOP_WORDS_SET = CharArraySet.unmodifiableSet(stopSet)
        }

        /** Returns an unmodifiable instance of the default stop words set. */
        fun getDefaultStopSet(): CharArraySet {
            return ENGLISH_STOP_WORDS_SET
        }
    }
}
