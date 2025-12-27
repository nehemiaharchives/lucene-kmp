package org.gnit.lucenekmp.analysis.ta

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
import org.gnit.lucenekmp.analysis.snowball.SnowballFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.tartarus.snowball.ext.TamilStemmer

/** Analyzer for Tamil. */
class TamilAnalyzer : StopwordAnalyzerBase {
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
        result = StopFilter(result, stopwords)
        result = SnowballFilter(result, TamilStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Tamil stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /** Returns an unmodifiable instance of the default stop-words set. */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# tamil stopwords from https://github.com/AshokR/TamilNLP
ஒரு
என்று
மற்றும்
இந்த
இது
என்ற
கொண்டு
என்பது
பல
ஆகும்
அல்லது
அவர்
நான்
உள்ள
அந்த
இவர்
என
முதல்
என்ன
இருந்து
சில
என்
போன்ற
வேண்டும்
வந்து
இதன்
அது
அவன்
தான்
பலரும்
என்னும்
மேலும்
பின்னர்
கொண்ட
இருக்கும்
தனது
உள்ளது
போது
என்றும்
அதன்
தன்
பிறகு
அவர்கள்
வரை
அவள்
நீ
ஆகிய
இருந்தது
உள்ளன
வந்த
இருந்த
மிகவும்
இங்கு
மீது
ஓர்
இவை
இந்தக்
பற்றி
வரும்
வேறு
இரு
இதில்
போல்
இப்போது
அவரது
மட்டும்
இந்தப்
எனும்
மேல்
பின்
சேர்ந்த
ஆகியோர்
எனக்கு
இன்னும்
அந்தப்
அன்று
ஒரே
மிக
அங்கு
பல்வேறு
விட்டு
பெரும்
அதை
பற்றிய
உன்
அதிக
அந்தக்
பேர்
இதனால்
அவை
அதே
ஏன்
முறை
யார்
என்பதை
எல்லாம்
மட்டுமே
இங்கே
அங்கே
இடம்
இடத்தில்
அதில்
நாம்
அதற்கு
எனவே
பிற
சிறு
மற்ற
விட
எந்த
எனவும்
எனப்படும்
எனினும்
அடுத்த
இதனை
இதை
கொள்ள
இந்தத்
இதற்கு
அதனால்
தவிர
போல
வரையில்
சற்று
எனக்
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
