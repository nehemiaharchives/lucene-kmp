package org.gnit.lucenekmp.analysis.bn

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

/** Analyzer for Bengali. */
class BengaliAnalyzer : StopwordAnalyzerBase {
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
        result = BengaliNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = BengaliStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        result = BengaliNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Bengali stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /** Returns an unmodifiable instance of the default stop-words set. */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# See http://members.unine.ch/jacques.savoy/clef/index.html.
# This file was created by Jacques Savoy and is distributed under the BSD license
এই
ও
থেকে
করে
এ
না
ওই
এক্
নিয়ে
করা
বলেন
সঙ্গে
যে
এব
তা
আর
কোনো
বলে
সেই
দিন
হয়
কি
দু
পরে
সব
দেওয়া
মধ্যে
এর
সি
শুরু
কাজ
কিছু
কাছে
সে
তবে
বা
বন
আগে
জ্নজন
পি
পর
তো
ছিল
এখন
আমরা
প্রায়
দুই
আমাদের
তাই
অন্য
গিয়ে
প্রযন্ত
মনে
নতুন
মতো
কেখা
প্রথম
আজ
টি
ধামার
অনেক
বিভিন্ন
র
হাজার
জানা
নয়
অবশ্য
বেশি
এস
করে
কে
হতে
বি
কয়েক
সহ
বেশ
এমন
এমনি
কেন
কেউ
নেওয়া
চেষ্টা
লক্ষ
বলা
কারণ
আছে
শুধু
তখন
যা
এসে
চার
ছিল
যদি
আবার
কোটি
উত্তর
সামনে
উপর
বক্তব্য
এত
প্রাথমিক
উপরে
আছে
প্রতি
কাজে
যখন
খুব
বহু
গেল
পেয়্র্
চালু
ই
নাগাদ
থাকা
পাচ
যাওয়া
রকম
সাধারণ
কমনে
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
