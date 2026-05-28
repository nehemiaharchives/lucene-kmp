package org.gnit.lucenekmp.analysis.`as`

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

/** Analyzer for Assamese. */
class AssameseAnalyzer : StopwordAnalyzerBase {
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
        result = AssameseNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = AssameseStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        result = AssameseNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Assamese stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /** Returns an unmodifiable instance of the default stop-words set. */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Source: CLARIN Assamese stopword dataset metadata plus common Assamese function words.
আৰু
বা
অথবা
এই
এয়া
এনে
সেই
সেয়া
সি
তেওঁ
তেখেত
মই
আমি
তুমি
তোমাৰ
আপুনি
আপোনাৰ
ই
ইয়াৰ
ইয়াত
তাৰ
তাত
তাক
যে
যদি
যেন
যেতিয়া
তেতিয়া
কিন্তু
তথাপি
কাৰণ
বাবে
কাৰণে
লাগি
পৰা
লৈ
লগতে
সৈতে
মধ্যে
ওপৰত
তলত
আগতে
পিছত
হয়
হৈ
হৈছে
হৈছিল
নহয়
নাই
আছে
আছিল
কৰা
কৰি
কৰে
কৰিছে
কৰিছিল
কি
কোন
কিয়
কেতিয়া
কেনেকৈ
কত
সকলো
বহু
বেছি
কম
মাত্ৰ
পুনৰ
আজি
কালি
এতিয়া
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
