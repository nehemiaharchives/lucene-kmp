package org.gnit.lucenekmp.analysis.su

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

/** Analyzer for Sundanese. */
class SundaneseAnalyzer : StopwordAnalyzerBase {
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
        result = SundaneseNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = SundaneseStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = SundaneseNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Sundanese stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Source: bimarakajati/Javanese-and-Sundanese-Stopwords local clone: ../Javanese-and-Sundanese-Stopwords/local_languages_stopwords.csv
abdi
acan
aing
anu
antara
aranjeunna
atawa
aya
ayana
ayeuna
baheula
bakal
bari
cekap
cisa
dina
di
dua
duanana
éta
geus
hadé
handap
hartina
hartosna
hayang
hayu
henteu
hiji
hoyong
hungkul
ieu
iraha
jadi
janten
jeung
jumlah
ka
kadua
kami
kana
kaayaan
kahayang
kali
kieu
kinilah
kitu
komo
ku
kumaha
kukituna
kuduna
kedah
kudu
kuring
lamun
leutik
leres
loba
mampuh
margi
anjeunna
manehna
maranéhna
masalah
masing-masing
mastikeun
métode
metode
mimiti
mimitina
na
naha
naon
ngajadikeun
ngadamel
ngalakukeun
ngomong
ngarasa
ngeunaan
ngingetkeun
ngingetan
ngomong
ngajelaskeun
ngajawabna
ngan
ogé
oké
opat
paling
pasihan
penting
pisan
pikeun
punten
rada
rasa
rék
réngsé
sabab
sababaraha
sakali
sakumaha
salaku
sami
sanajan
sanes
sangkan
sarta
saterusna
sia
siga
sigana
teh
teu
teras
tibatan
tina
tujuanna
tuh
tungtung
tungtungna
upami
urang
wangsit
waktosna
anjeunna
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
