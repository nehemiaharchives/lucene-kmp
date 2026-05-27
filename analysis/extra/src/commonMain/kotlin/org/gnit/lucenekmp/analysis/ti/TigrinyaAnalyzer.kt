package org.gnit.lucenekmp.analysis.ti

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/** Analyzer for Tigrinya. */
class TigrinyaAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the given stop words and stem exclusions. */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /** Builds an analyzer with the default stop words: [DEFAULT_STOPWORD_FILE]. */
    constructor() : this(DefaultSetHolder.DEFAULT_STOP_SET)

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = DecimalDigitFilter(result)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = TigrinyaNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = TigrinyaStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = TigrinyaNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Tigrinya stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Curated Tigrinya function words, articles, pronouns, prepositions, conjunctions and particles.
ሀደ
ሓደ
ሕጂ
ምስ
ምእንቲ
ምኽንያቱ
ምኽንያት
ስለ
ቅድሚ
ብ
ብዛዕባ
በቲ
በታ
በቶም
በተን
ነቲ
ነታ
ነቶም
ነተን
ን
ንሕና
ንሱ
ንሳ
ንሳቶም
ንስኺ
ንስኻ
ንስኻትኩም
ንስኻትክን
ንስኽን
ንስኹም
ናብ
ናይ
ኣብ
ኣብቲ
ኣብታ
ኣብቶም
ኣብተን
ኣብዚ
ኣብዛ
ኣብዞም
ኣብዘን
ኣነ
ኣይ
እቲ
እታ
እቶም
እተን
እንተ
እኳ
እወ
እዚ
እዛ
እዞም
እዘን
እዩ
እያ
እየ
እዮም
እየን
ከም
ከምዚ
ከምዛ
ከምኡ
ከኣ
ኩሉ
ኩላ
ኩሎም
ኩለን
ካብ
ካብቲ
ካብታ
ካብቶም
ካብተን
ወይ
ዘይ
ዝ
ድሕሪ
ግን
፣
።
"""

        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), STOPWORDS_COMMENT)
                } catch (ex: IOException) {
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}
