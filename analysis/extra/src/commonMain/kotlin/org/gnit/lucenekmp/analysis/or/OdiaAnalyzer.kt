package org.gnit.lucenekmp.analysis.or

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

/** Analyzer for Odia. */
class OdiaAnalyzer : StopwordAnalyzerBase {
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
        result = OdiaNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = OdiaStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        result = OdiaNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Odia stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Source: openodia local clone: ../openodia/openodia/common/constants.py STOPWORDS
।
ଦେଇଛନ୍ତି
ଲେଖାଏଁ
ଜଣେ
ଏହା
ତେଣୁ
ମିଳିଥାଏ
ପାଇଁ
ନେଉଛନ୍ତି
ଯୋଗୁଁ
ଏପର୍ଯ୍ୟନ୍ତ
ଏଭଳି
କରୁଛି
ଓ
ଯାଏଁ
ହୋଇଛନ୍ତି
କି
କରାଯିବା
ପରେ
ଏହି
ଏବଂ
ଜଣ
ଥିବା
ହୋଇଥିଲା
ତେବେ
ଆଜି
ଜଣଙ୍କ
ଏଥି
ଗତ
ହୋଇଥିଲେ
ହେଉଥିବା
ଯୋଗେ
ବୋଲି
ଜଣାପଡ଼ିଛି
ଦ୍ବାରା
କରି
ଯାଇ
ଏନେଇ
ଚାଲୁ
ରହିଛି
ତାରିଖ
ମିଳିଛି
ବର୍ଷୀୟ
ଦିନତଳେ
ସହ
ଆସିଛନ୍ତି
ମଧ୍ୟ
କେଉଁ
ହୋଇଯାଇଛି
ନେଇଯାଇଛି
କାମ
କରିଛି
ହେବା
ଏବେ
ହୋଇଛି
ରଖି
ନିଆଯାଇଥିବା
ପର୍ଯ୍ୟନ୍ତ
କରିଛନ୍ତି
ଉପରେ
ଦେଲେ
ଥର
ଆଉ
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
