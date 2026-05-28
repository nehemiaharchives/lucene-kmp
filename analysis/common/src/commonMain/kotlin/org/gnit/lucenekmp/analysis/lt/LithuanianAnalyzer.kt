package org.gnit.lucenekmp.analysis.lt

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.snowball.SnowballFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.tartarus.snowball.ext.LithuanianStemmer

/**
 * [org.gnit.lucenekmp.analysis.Analyzer] for Lithuanian.
 *
 * @since 5.3.0
 */
class LithuanianAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the default stop words: [DEFAULT_STOPWORD_FILE]. */
    constructor() : this(DefaultSetHolder.DEFAULT_STOP_SET)

    /**
     * Builds an analyzer with the given stop words.
     *
     * @param stopwords a stopword set
     */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /**
     * Builds an analyzer with the given stop words. If a non-empty stem exclusion set is provided
     * this analyzer will add a [SetKeywordMarkerFilter] before stemming.
     *
     * @param stopwords a stopword set
     * @param stemExclusionSet a set of terms not to be stemmed
     */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = SnowballFilter(result, LithuanianStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Lithuanian stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        /**
         * Returns an unmodifiable instance of the default stop words set.
         *
         * @return default stop words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Lithuanian stopwords list
ant
apie
ar
arba
aš
be
bei
bet
bus
būti
būtų
buvo
dėl
gali
į
iki
ir
iš
ja
ją
jai
jais
jam
jame
jas
jei
ji
jį
jie
jiedu
jiedvi
jiedviem
jiedviese
jiems
jis
jo
jodviem
jog
joje
jomis
joms
jos
jose
jų
judu
judvi
judviejų
jųdviejų
judviem
judviese
jumis
jums
jumyse
juo
juodu
juodviese
juos
juose
jus
jūs
jūsų
ką
kad
kai
kaip
kas
kiek
kol
kur
kurie
kuris
man
mane
manęs
manimi
mano
manyje
mes
metu
mudu
mudvi
mudviejų
mudviem
mudviese
mumis
mums
mumyse
mus
mūsų
nei
nes
net
nors
nuo
o
pat
per
po
prie
prieš
sau
save
savęs
savimi
savo
savyje
su
tačiau
tada
tai
taip
tas
tau
tave
tavęs
tavimi
tavyje
ten
to
todėl
tu
tuo
už
visi
yra
"""

        /**
         * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class accesses the
         * static final set the first time.
         */
        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), "#")
                } catch (ex: IOException) {
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}
