package org.gnit.lucenekmp.analysis.pa

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

/** Analyzer for Punjabi. */
class PunjabiAnalyzer : StopwordAnalyzerBase {
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
        var result: TokenStream = LowerCaseFilter(source)
        result = DecimalDigitFilter(result)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = IndicNormalizationFilter(result)
        result = PunjabiNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = PunjabiStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        result = PunjabiNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Punjabi stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Source: gurpejsingh13/Punjabi_Stopwords local clone: ../Punjabi_Stopwords
ਅਗਲੀ
ਅਤੇ
ਅਿਜਹੇ
ਅੰਦਰ
ਅੱਸੀ
ਆ
ਆਈ
ਆਖ
ਆਦੀ
ਆਪ
ਆਪਣਾ
ਆਮ
ਆਵੇ
ਇਸ
ਇਹ
ਇੱਕ
ਇੱਥੇ
ਉਏ
ਉਠ
ਉਸ
ਉਸਦੀ
ਉਸਨੇ
ਉਹ
ਉੱਤੇ
ਉੱਤੋਂ
ਏ
ਏਥੇ
ਏਧਰ
ਏਸ
ਐਹੋ
ਓਥੇ
ਕਈ
ਕਦ
ਕਦੀ
ਕਦੇ
ਕਰ
ਕਰਕੇ
ਕਰਣ
ਕਰਵਾਈ
ਕਰੀ
ਕਾਫ਼ੀ
ਕਿ
ਕਿਤੇ
ਕਿਸ
ਕਿਸੇ
ਕਿਹ
ਕਿਹਾ
ਕਿੰਨਾ
ਕੀ
ਕੀਤਾ
ਕੁਝ
ਕੁਲ
ਕੇ
ਕੋਈ
ਕੋਲੋਂ
ਕੌਣ
ਗਈ
ਗਿਆ
ਗੱਲ
ਚਕੇ
ਚਲਾ
ਚਾਹੇ
ਚੋ
ਜਦ
ਜਦੋਂ
ਜਾਂ
ਜਾਂਦਾ
ਜਾਵਣ
ਜਾਵੇ
ਜਿਨਾ
ਜਿਨਾਂ
ਜਿਨੂ
ਜਿਨ੍ਹਾਂਨੂੰ
ਜਿਵੇਂ
ਜਿਸ
ਜਿਹਾ
ਜਿੱਥੇ
ਜੀ
ਜੇ
ਜੇਕਰ
ਜੇਹੜਾ
ਤਕ
ਤਦ
ਤਰ੍ਹਾਂ
ਤਾਂ
ਤੁਸਾ
ਤੁਸੀ
ਤੂੰ
ਤੇ
ਤੇਨੂੰ
ਤੇਰਾ
ਤੋਂ
ਤੱਦ
ਦਾ
ਦੀਆਂ
ਦੁਆਰਾ
ਦੇ
ਦੇਖ
ਦੇਣੀ
ਦੌਰਾਨ
ਨਹੀਂ
ਨਾ
ਨਾਲ
ਨਾਲੇ
ਨੂੰ
ਨੇ
ਨੇਂ
ਪਰ
ਪਾਸੋ
ਪਿਆ
ਪਿਛੋਂ
ਪੀ
ਪੂਰਾ
ਪੈਣ
ਫਿਰ
ਫੇਰ
ਬਣ
ਬਣਾ
ਬਣਾਏ
ਬਣੋ
ਬਹਤੁ
ਬਾਅਦ
ਬਾਰੇ
ਬਿਲਕੁਲ
ਭਾਵੇਂ
ਭੀ
ਮਗਰ
ਮੇਰਾ
ਮੈਂ
ਰਹੀ
ਰਹੇ
ਰਿਹ
ਰਿਹਾ
ਰੱਖ
ਲਈ
ਲਗ
ਲਗਾਉਦਾ
ਲਾ
ਲਾਇਆ
ਲਿਆ
ਲੈ
ਲੱਗ
ਵਰਗ
ਵਰਗਾ
ਵਲੋਂ
ਵਾਂਗ
ਵਿਚ
ਵੀ
ਵੇਖ
ਵੇਲੇ
ਵਗ਼ੈਰਾ
ਸਕਦੇ
ਸਦਾ
ਸਨ
ਸਭ
ਸਾਂ
ਸਾਬੁਤ
ਸਾਰਾ
ਸਾਰੇ
ਸੀ
ਸੁਣ
ਹਣੁ
ਹਣੇ
ਹਨ
ਹਾਲ
ਹੀ
ਹੁੰਦਾ
ਹੇਠਾਂ
ਹੈ
ਹੈ।
ਹੈਂ
ਹੋ
ਹੋਇਆ
ਹੋਏ
ਹੋਣਾ
ਹੋਵੇ
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
