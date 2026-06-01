package org.gnit.lucenekmp.analysis.gl

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

/** [org.gnit.lucenekmp.analysis.Analyzer] for Galician. */
class GalicianAnalyzer : StopwordAnalyzerBase {
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
     * if a stem exclusion set is provided and [GalicianStemFilter].
     */
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) result = SetKeywordMarkerFilter(result, stemExclusionSet)
        result = GalicianStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Galician stopwords. */
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
# galican stopwords
a
aínda
alí
aquel
aquela
aquelas
aqueles
aquilo
aquí
ao
aos
as
así
á
ben
cando
che
co
coa
comigo
con
connosco
contigo
convosco
coas
cos
cun
cuns
cunha
cunhas
da
dalgunha
dalgunhas
dalgún
dalgúns
das
de
del
dela
delas
deles
desde
deste
do
dos
dun
duns
dunha
dunhas
e
el
ela
elas
eles
en
era
eran
esa
esas
ese
eses
esta
estar
estaba
está
están
este
estes
estiven
estou
eu
é
facer
foi
foron
fun
había
hai
iso
isto
la
las
lle
lles
lo
los
mais
me
meu
meus
min
miña
miñas
moi
na
nas
neste
nin
no
non
nos
nosa
nosas
noso
nosos
nós
nun
nunha
nuns
nunhas
o
os
ou
ó
ós
para
pero
pode
pois
pola
polas
polo
polos
por
que
se
senón
ser
seu
seus
sexa
sido
sobre
súa
súas
tamén
tan
te
ten
teñen
teño
ter
teu
teus
ti
tido
tiña
tiven
túa
túas
un
unha
unhas
uns
vos
vosa
vosas
voso
vosos
vós
"""

        /**
         * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class accesses the
         * static final set the first time.
         */
        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA))
                } catch (ex: IOException) {
                    // default set should always be present as it is part of the
                    // distribution (JAR)
                    throw RuntimeException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}
