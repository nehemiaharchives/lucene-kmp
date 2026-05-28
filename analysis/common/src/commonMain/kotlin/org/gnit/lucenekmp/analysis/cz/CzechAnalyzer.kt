package org.gnit.lucenekmp.analysis.cz

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/** Analyzer for Czech. */
class CzechAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionTable: CharArraySet

    /** Builds an analyzer with the given stop words and a set of words to exclude from [CzechStemFilter]. */
    constructor(stopwords: CharArraySet, stemExclusionTable: CharArraySet) : super(stopwords) {
        this.stemExclusionTable = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionTable))
    }

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /** Builds an analyzer with the default stop words: [DEFAULT_STOPWORD_FILE]. */
    constructor() : this(DefaultSetHolder.DEFAULT_SET)

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionTable.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionTable)
        }
        result = CzechStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Czech stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /** Returns a set of default Czech stopwords. */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
a
s
k
o
i
u
v
z
dnes
cz
tímto
budeš
budem
byli
jseš
můj
svým
ta
tomto
tohle
tuto
tyto
jej
zda
proč
máte
tata
kam
tohoto
kdo
kteří
mi
nám
tom
tomuto
mít
nic
proto
kterou
byla
toho
protože
asi
ho
naši
napište
re
což
tím
takže
svých
její
svými
jste
aj
tu
tedy
teto
bylo
kde
ke
pravé
ji
nad
nejsou
či
pod
téma
mezi
přes
ty
pak
vám
ani
když
však
neg
jsem
tento
článku
články
aby
jsme
před
pta
jejich
byl
ještě
až
bez
také
pouze
první
vaše
která
nás
nový
tipy
pokud
může
strana
jeho
své
jiné
zprávy
nové
není
vás
jen
podle
zde
už
být
více
bude
již
než
který
by
které
co
nebo
ten
tak
má
při
od
po
jsou
jak
další
ale
si
se
ve
to
jako
za
zpět
ze
do
pro
je
na
atd
atp
jakmile
přičemž
já
on
ona
ono
oni
ony
my
vy
jí
ji
mě
mne
jemu
tomu
těm
těmu
němu
němuž
jehož
jíž
jelikož
jež
jakož
načež
"""

        private object DefaultSetHolder {
            val DEFAULT_SET: CharArraySet

            init {
                try {
                    DEFAULT_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), STOPWORDS_COMMENT)
                } catch (ex: IOException) {
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}
