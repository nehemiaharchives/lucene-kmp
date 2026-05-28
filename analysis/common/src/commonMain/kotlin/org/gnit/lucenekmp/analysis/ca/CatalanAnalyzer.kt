package org.gnit.lucenekmp.analysis.ca

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
import org.gnit.lucenekmp.analysis.util.ElisionFilter
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.tartarus.snowball.ext.CatalanStemmer

/** [org.gnit.lucenekmp.analysis.Analyzer] for Catalan. */
class CatalanAnalyzer : StopwordAnalyzerBase {
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
        var result: TokenStream = ElisionFilter(source, DEFAULT_ARTICLES)
        result = LowerCaseFilter(result)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = SnowballFilter(result, CatalanStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = ElisionFilter(`in`, DEFAULT_ARTICLES)
        result = LowerCaseFilter(result)
        return result
    }

    companion object {
        /** File containing default Catalan stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        val DEFAULT_ARTICLES: CharArraySet =
            CharArraySet.unmodifiableSet(
                CharArraySet(mutableListOf("d", "l", "m", "n", "s", "t"), true)
            )

        /**
         * Returns an unmodifiable instance of the default stop words set.
         *
         * @return default stop words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Catalan stopwords from http://github.com/vcl/cue.language (Apache 2 Licensed)
a
abans
ací
ah
així
això
al
als
aleshores
algun
alguna
algunes
alguns
alhora
allà
allí
allò
altra
altre
altres
amb
ambdós
ambdues
apa
aquell
aquella
aquelles
aquells
aquest
aquesta
aquestes
aquests
aquí
baix
cada
cadascú
cadascuna
cadascunes
cadascuns
com
contra
d'un
d'una
d'unes
d'uns
dalt
de
del
dels
des
després
dins
dintre
donat
doncs
durant
e
eh
el
els
em
en
encara
ens
entre
érem
eren
éreu
es
és
esta
està
estàvem
estaven
estàveu
esteu
et
etc
ets
fins
fora
gairebé
ha
han
has
havia
he
hem
heu
hi 
ho
i
igual
iguals
ja
l'hi
la
les
li
li'n
llavors
m'he
ma
mal
malgrat
mateix
mateixa
mateixes
mateixos
me
mentre
més
meu
meus
meva
meves
molt
molta
moltes
molts
mon
mons
n'he
n'hi
ne
ni
no
nogensmenys
només
nosaltres
nostra
nostre
nostres
o
oh
oi
on
pas
pel
pels
per
però
perquè
poc 
poca
pocs
poques
potser
propi
qual
quals
quan
quant 
que
què
quelcom
qui
quin
quina
quines
quins
s'ha
s'han
sa
semblant
semblants
ses
seu 
seus
seva
seva
seves
si
sobre
sobretot
sóc
solament
sols
son 
són
sons 
sota
sou
t'ha
t'han
t'he
ta
tal
també
tampoc
tan
tant
tanta
tantes
teu
teus
teva
teves
ton
tons
tot
tota
totes
tots
un
una
unes
uns
us
va
vaig
vam
van
vas
veu
vosaltres
vostra
vostre
vostres
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
