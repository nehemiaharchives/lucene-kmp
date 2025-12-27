package org.gnit.lucenekmp.analysis.te

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.`in`.IndicNormalizationFilter
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/** Analyzer for Telugu. */
class TeluguAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /** Builds an analyzer with the default stop words: [DEFAULT_STOPWORD_FILE]. */
    constructor() : this(DefaultSetHolder.DEFAULT_STOP_SET)

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = DecimalDigitFilter(source)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = IndicNormalizationFilter(result)
        result = TeluguNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = TeluguStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = DecimalDigitFilter(`in`)
        result = IndicNormalizationFilter(result)
        result = TeluguNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Telugu stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /** Returns an unmodifiable instance of the default stop-words set. */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Telugu stop word list
చేయగలిగింది
గురించి
పై
ప్రకారం
అనుగుణంగా
అడ్డంగా
నిజంగా
తర్వాత
తర్వాత
మళ్ళీ
వ్యతిరేకంగా
కాదు
అందరూ
అనుమతించు
అనుమతిస్తుంది
దాదాపు
మాత్రమే
వెంట
ఇప్పటికే
కూడా
అయితే
ఎప్పుడు
వద్ద
మధ్య
మధ్య
ఒక
మరియు
మరొక
ఏ
ఎవరో ఒకరు
ఏమైనప్పటికి
ఎవరైనా
ఏదైనా
ఏమైనప్పటికి
ఎక్కడైనా
వేరుగా
కనిపిస్తాయి
మెచ్చుకో
తగిన
ఉన్నారు
కాదు
చుట్టూ
గా
ఒక ప్రక్కన
అడగండి
అడగడం
సంబంధం
వద్ద
అందుబాటులో
దూరంగా
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
