package org.gnit.lucenekmp.analysis.gu

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

/** Analyzer for Gujarati. */
class GujaratiAnalyzer : StopwordAnalyzerBase {
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
        result = GujaratiNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = GujaratiStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = IndicNormalizationFilter(result)
        result = GujaratiNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Gujarati stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Source: stopwords-iso (gu) local clone: stopwords-iso/stopwords-iso.json
અંગે
અંદર
અથવા
અને
અમને
અમારું
અમે
અહીં
આ
આગળ
આથી
આનું
આને
આપણને
આપણું
આપણે
આપી
આર
આવી
આવે
ઉપર
ઉભા
ઊંચે
ઊભું
એ
એક
એન
એના
એનાં
એની
એનું
એને
એનો
એમ
એવા
એવાં
એવી
એવું
એવો
ઓછું
કંઈક
કઈ
કયું
કયો
કરતાં
કરવું
કરી
કરીએ
કરું
કરે
કરેલું
કર્યા
કર્યાં
કર્યું
કર્યો
કાંઈ
કે
કેટલું
કેમ
કેવી
કેવું
કોઈ
કોઈક
કોણ
કોણે
કોને
ક્યાં
ક્યારે
ખૂબ
ગઈ
ગયા
ગયાં
ગયું
ગયો
ઘણું
છ
છતાં
છીએ
છું
છે
છેક
છો
જ
જાય
જી
જે
જેટલું
જેને
જેમ
જેવી
જેવું
જેવો
જો
જોઈએ
જ્યાં
જ્યારે
ઝાઝું
તને
તમને
તમારું
તમે
તા
તારાથી
તારામાં
તારું
તું
તે
તેં
તેઓ
તેણે
તેથી
તેના
તેની
તેનું
તેને
તેમ
તેમનું
તેમને
તેવી
તેવું
તો
ત્યાં
ત્યારે
થઇ
થઈ
થઈએ
થતા
થતાં
થતી
થતું
થતો
થયા
થયાં
થયું
થયેલું
થયો
થવું
થાઉં
થાઓ
થાય
થી
થોડું
દરેક
ન
નં
નં.
નથી
નહિ
નહી
નહીં
ના
ની
નીચે
નું
ને
નો
પછી
પણ
પર
પરંતુ
પહેલાં
પાછળ
પાસે
પોતાનું
પ્રત્યેક
ફક્ત
ફરી
ફરીથી
બંને
બધા
બધું
બની
બહાર
બહુ
બાદ
બે
મને
મા
માં
માટે
માત્ર
મારું
મી
મૂકવું
મૂકી
મૂક્યા
મૂક્યાં
મૂક્યું
મેં
રહી
રહે
રહેવું
રહ્યા
રહ્યાં
રહ્યો
રીતે
રૂ.
રૂા
લેતા
લેતું
લેવા
વગેરે
વધુ
શકે
શા
શું
સરખું
સામે
સુધી
હતા
હતાં
હતી
હતું
હવે
હશે
હશો
હા
હું
હો
હોઈ
હોઈશ
હોઈશું
હોય
હોવા
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
