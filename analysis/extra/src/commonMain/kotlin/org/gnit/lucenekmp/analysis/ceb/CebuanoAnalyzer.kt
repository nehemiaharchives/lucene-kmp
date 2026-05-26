package org.gnit.lucenekmp.analysis.ceb

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/** Analyzer for Cebuano. */
class CebuanoAnalyzer : StopwordAnalyzerBase {
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
        result = CebuanoNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = CebuanoStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = CebuanoNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Cebuano stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Sources: Universal Dependencies Cebuano notes, common Cebuano word lists, and user-provided terms.
ako
akong
alag
alang
ambot
ang
ania
ano
apan
aron
asa
ayaw
ba
bag-o
bahin
bisan
bitaw
di
diha
dili
dinhi
dinha
dito
dunay
gikan
gihapon
gud
gyud
ha
hain
hangtod
ikaw
ila
ilang
imo
imong
ingon
intawon
inyong
isa
iya
iyang
ka
kada
kadtong
kaha
kami
kamo
kanako
kanang
kanato
kanila
kaniya
kaniadto
kanimo
kaninyo
karon
katong
kay
kini
kita
ko
kun
kung
kuno
lagi
lang
mao
man
mas
matag
may
mi
mga
mo
na
nag
nga
ngano
ni
nia
nila
nilang
nimo
ninyo
niya
niyang
nuon
o
og
pa
pag
panahon
para
pay
ra
rehiyon
sa
samtang
si
sila
siya
ta
tanan
tungod
ug
unsa
usa
wala
walay
# User supplied domain/document words.
departamento
pransiya
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
