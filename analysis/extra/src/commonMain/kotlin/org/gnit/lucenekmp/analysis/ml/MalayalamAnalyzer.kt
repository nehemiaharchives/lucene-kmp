package org.gnit.lucenekmp.analysis.ml

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

/** Analyzer for Malayalam. */
class MalayalamAnalyzer : StopwordAnalyzerBase {
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
        result = MalayalamNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = MalayalamStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        result = MalayalamNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Malayalam stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Source: Malayalam grammar/NLP references plus common Malayalam function words.
ആണ്
ആകുന്നു
ആയിരുന്നു
ആയി
ഒരു
ഈ
ആ
ഇത്
അത്
ഇവ
അവ
അവൻ
അവൾ
അവർ
ഞാൻ
നീ
നിങ്ങൾ
ഞങ്ങൾ
നമ്മൾ
എൻ
എന്റെ
നിന്റെ
അവന്റെ
അവളുടെ
അവരുടെ
ഇല്ല
അല്ല
ഉണ്ട്
എന്ന്
എന്ന
എന്നാൽ
എന്നും
എങ്കിൽ
അല്ലെങ്കിൽ
മറ്റു
മറ്റ്
കൂടി
മാത്രം
വരെ
മുതൽ
കൂടെ
പോലെ
വേണ്ടി
ശേഷം
മുമ്പ്
കാരണം
അതുകൊണ്ട്
പിന്നെ
പക്ഷേ
അതിനാൽ
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
