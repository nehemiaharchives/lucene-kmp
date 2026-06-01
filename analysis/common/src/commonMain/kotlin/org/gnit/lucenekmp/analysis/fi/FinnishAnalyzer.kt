package org.gnit.lucenekmp.analysis.fi

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
import org.tartarus.snowball.ext.FinnishStemmer

/** [org.gnit.lucenekmp.analysis.Analyzer] for Finnish. */
class FinnishAnalyzer : StopwordAnalyzerBase {
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

    /**
     * Creates a [org.gnit.lucenekmp.analysis.Analyzer.TokenStreamComponents] which tokenizes all
     * the text in the provided [org.gnit.lucenekmp.jdkport.Reader].
     *
     * @return A [org.gnit.lucenekmp.analysis.Analyzer.TokenStreamComponents] built from an
     * [StandardTokenizer] filtered with [LowerCaseFilter], [StopFilter], [SetKeywordMarkerFilter]
     * if a stem exclusion set is provided and [SnowballFilter].
     */
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) result = SetKeywordMarkerFilter(result, stemExclusionSet)
        result = SnowballFilter(result, FinnishStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Italian stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "finnish_stop.txt"

        /**
         * Returns an unmodifiable instance of the default stop words set.
         *
         * @return default stop words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
 | From https://snowballstem.org/algorithms/finnish/stop.txt
 | This file is distributed under the BSD License.
 | See https://snowballstem.org/license.html
 | Also see https://opensource.org/licenses/bsd-license.html
 |  - Encoding was converted to UTF-8.
 |  - This notice was added.
 |
 | NOTE: To use this file with StopFilterFactory, you must specify format="snowball"

| forms of BE

olla
olen
olet
on
olemme
olette
ovat
ole        | negative form

oli
olisi
olisit
olisin
olisimme
olisitte
olisivat
olit
olin
olimme
olitte
olivat
ollut
olleet

en         | negation
et
ei
emme
ette
eivät

|Nom   Gen    Acc    Part   Iness   Elat    Illat  Adess   Ablat   Allat   Ess    Trans
minä   minun  minut  minua  minussa minusta minuun minulla minulta minulle               | I
sinä   sinun  sinut  sinua  sinussa sinusta sinuun sinulla sinulta sinulle               | you
hän    hänen  hänet  häntä  hänessä hänestä häneen hänellä häneltä hänelle               | he she
me     meidän meidät meitä  meissä  meistä  meihin meillä  meiltä  meille                | we
te     teidän teidät teitä  teissä  teistä  teihin teillä  teiltä  teille                | you
he     heidän heidät heitä  heissä  heistä  heihin heillä  heiltä  heille                | they

tämä   tämän         tätä   tässä   tästä   tähän  tällä   tältä   tälle   tänä   täksi  | this
tuo    tuon          tuota  tuossa  tuosta  tuohon tuolla  tuolta  tuolle  tuona  tuoksi | that
se     sen           sitä   siinä   siitä   siihen sillä   siltä   sille   sinä   siksi  | it
nämä   näiden        näitä  näissä  näistä  näihin näillä  näiltä  näille  näinä  näiksi | these
nuo    noiden        noita  noissa  noista  noihin noilla  noilta  noille  noina  noiksi | those
ne     niiden        niitä  niissä  niistä  niihin niillä  niiltä  niille  niinä  niiksi | they

kuka   kenen kenet   ketä   kenessä kenestä keneen kenellä keneltä kenelle kenenä keneksi| who
ketkä  keiden ketkä  keitä  keissä  keistä  keihin keillä  keiltä  keille  keinä  keiksi | (pl)
mikä   minkä minkä   mitä   missä   mistä   mihin  millä   miltä   mille   minä   miksi  | which what
mitkä                                                                                    | (pl)

joka   jonka         jota   jossa   josta   johon  jolla   jolta   jolle   jona   joksi  | who which
jotka  joiden        joita  joissa  joista  joihin joilla  joilta  joille  joina  joiksi | (pl)

| conjunctions

että   | that
ja     | and
jos    | if
koska  | because
kuin   | than
mutta  | but
niin   | so
sekä   | and
sillä  | for
tai    | or
vaan   | but
vai    | or
vaikka | although


| prepositions

kanssa  | with
mukaan  | according to
noin    | about
poikki  | across
yli     | over, across

| other

kun    | when
nyt    | now
itse   | self

"""

        /**
         * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class accesses the
         * static final set the first time.
         */
        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getSnowballWordSet(StringReader(DEFAULT_STOPWORD_DATA))
                } catch (ex: IOException) {
                    // default set should always be present as it is part of the
                    // distribution (JAR)
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}
