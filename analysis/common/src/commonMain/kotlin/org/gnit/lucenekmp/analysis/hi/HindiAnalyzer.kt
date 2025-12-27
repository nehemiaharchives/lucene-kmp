package org.gnit.lucenekmp.analysis.hi

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

/** Analyzer for Hindi. */
class HindiAnalyzer : StopwordAnalyzerBase {
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
        result = HindiNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = HindiStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        result = HindiNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Hindi stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Also see http://www.opensource.org/licenses/bsd-license.html
# See http://members.unine.ch/jacques.savoy/clef/index.html.
# This file was created by Jacques Savoy and is distributed under the BSD license.
# Note: by default this file also contains forms normalized by HindiNormalizer
# for spelling variation (see section below), such that it can be used whether or
# not you enable that feature. When adding additional entries to this list,
# please add the normalized form as well.
अंदर
अत
अपना
अपनी
अपने
अभी
आदि
आप
इत्यादि
इन
इनका
इन्हीं
इन्हें
इन्हों
इस
इसका
इसकी
इसके
इसमें
इसी
इसे
उन
उनका
उनकी
उनके
उनको
उन्हीं
उन्हें
उन्हों
उस
उसके
उसी
उसे
एक
एवं
एस
ऐसे
और
कई
कर
करता
करते
करना
करने
करें
कहते
कहा
का
काफ़ी
कि
कितना
किन्हें
किन्हों
किया
किर
किस
किसी
किसे
की
कुछ
कुल
के
को
कोई
कौन
कौनसा
गया
घर
जब
जहाँ
जा
जितना
जिन
जिन्हें
जिन्हों
जिस
जिसे
जीधर
जैसा
जैसे
जो
तक
तब
तरह
तिन
तिन्हें
तिन्हों
तिस
तिसे
तो
था
थी
थे
दबारा
दिया
दुसरा
दूसरे
दो
द्वारा
न
नहीं
ना
निहायत
नीचे
ने
पर
पहले
पूरा
पे
फिर
बनी
बही
बहुत
बाद
बाला
बिलकुल
भी
भीतर
मगर
मानो
मे
में
यदि
यह
यहाँ
यही
या
यिह
ये
रखें
रहा
रहे
ऱ्वासा
लिए
लिये
लेकिन
व
वर्ग
वह
वहाँ
वहीं
वाले
वुह
वे
वग़ैरह
संग
सकता
सकते
सबसे
सभी
साथ
साबुत
साभ
सारा
से
सो
ही
हुआ
हुई
हुए
है
हैं
हो
होता
होती
होते
होना
होने
# additional normalized forms of the above
अपनि
जेसे
होति
सभि
तिंहों
इंहों
दवारा
इसि
किंहें
थि
उंहों
ओर
जिंहें
वहिं
अभि
बनि
हि
उंहिं
उंहें
हें
वगेरह
एसे
रवासा
कोन
निचे
काफि
उसि
पुरा
भितर
हे
बहि
वहां
कोइ
यहां
जिंहों
तिंहें
किसि
कइ
यहि
इंहिं
जिधर
इंहें
अदि
इतयादि
हुइ
कोनसा
इसकि
दुसरे
जहां
अप
किंहों
उनकि
भि
वरग
हुअ
जेसा
नहिं
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
