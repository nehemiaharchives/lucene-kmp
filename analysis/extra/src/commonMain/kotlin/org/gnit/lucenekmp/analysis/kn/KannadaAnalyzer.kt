package org.gnit.lucenekmp.analysis.kn

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
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

/** Analyzer for Kannada. */
class KannadaAnalyzer : StopwordAnalyzerBase {
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
        result = IndicNormalizationFilter(result)
        result = KannadaNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = KannadaStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        result = KannadaNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Kannada stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /** Returns an unmodifiable instance of the default stop-words set. */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Source: Spark NLP stopwords_iso kn metadata plus common Kannada function words.
ಮತ್ತು
ಅಥವಾ
ಆದರೆ
ಆದರೂ
ಏಕೆಂದರೆ
ಆದ್ದರಿಂದ
ಆದಾಗ್ಯೂ
ಈ
ಆ
ಇದು
ಅದು
ಇವು
ಅವು
ಇಲ್ಲಿ
ಅಲ್ಲಿ
ಎಲ್ಲಿ
ಇಂದು
ನಿನ್ನೆ
ನಾಳೆ
ಈಗ
ನಂತರ
ಮೊದಲು
ಮೇಲೆ
ಕೆಳಗೆ
ಒಳಗೆ
ಹೊರಗೆ
ಮಧ್ಯೆ
ನಾನು
ನನ್ನ
ನಾವು
ನಮ್ಮ
ನೀನು
ನೀವು
ನಿಮ್ಮ
ಅವನು
ಅವಳು
ಅವರು
ಅವರ
ಅವನ
ಅವಳ
ಯಾರು
ಯಾವ
ಯಾವುದು
ಯಾವಾಗ
ಏನು
ಏಕೆ
ಹೇಗೆ
ಎಂದು
ಎಂಬ
ಹಾಗೂ
ಕೂಡ
ಸಹ
ಮಾತ್ರ
ಎಲ್ಲಾ
ಕೆಲವು
ಒಂದು
ಎರಡು
ಹೆಚ್ಚು
ಕಡಿಮೆ
ಬಹಳ
ತುಂಬಾ
ಇದೆ
ಇವೆ
ಇದ್ದ
ಇತ್ತು
ಇಲ್ಲ
ಅಲ್ಲ
ಆಗಿದೆ
ಆಗಿ
ಆಗ
ಮಾಡಿ
ಮಾಡಿದ
ಮಾಡುತ್ತದೆ
ಮಾಡಲು
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
