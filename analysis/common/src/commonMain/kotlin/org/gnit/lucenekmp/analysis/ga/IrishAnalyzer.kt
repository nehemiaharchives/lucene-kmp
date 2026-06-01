package org.gnit.lucenekmp.analysis.ga

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
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
import org.tartarus.snowball.ext.IrishStemmer

/** [org.gnit.lucenekmp.analysis.Analyzer] for Irish. */
class IrishAnalyzer : StopwordAnalyzerBase {
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
     * [StandardTokenizer] filtered with [IrishLowerCaseFilter], [StopFilter],
     * [SetKeywordMarkerFilter] if a stem exclusion set is provided and [SnowballFilter].
     */
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = StopFilter(source, HYPHENATIONS)
        result = ElisionFilter(result, DEFAULT_ARTICLES)
        result = IrishLowerCaseFilter(result)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) result = SetKeywordMarkerFilter(result, stemExclusionSet)
        result = SnowballFilter(result, IrishStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = ElisionFilter(`in`, DEFAULT_ARTICLES)
        result = IrishLowerCaseFilter(result)
        return result
    }

    companion object {
        /** File containing default Irish stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "irish_stop.txt"

        private val DEFAULT_ARTICLES: CharArraySet =
            CharArraySet.unmodifiableSet(CharArraySet(mutableListOf<Any>("d", "m", "b"), true))

        /**
         * When StandardTokenizer splits t-athair into {t, athair}, we don't want to cause a position
         * increment, otherwise there will be problems with phrase queries versus tAthair (which would not
         * have a gap).
         */
        private val HYPHENATIONS: CharArraySet =
            CharArraySet.unmodifiableSet(CharArraySet(mutableListOf<Any>("h", "n", "t"), true))

        /**
         * Returns an unmodifiable instance of the default stop words set.
         *
         * @return default stop words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
 | From https://snowballstem.org/algorithms/irish/stop.txt
 | This file is distributed under the BSD License.
 | See https://snowballstem.org/license.html
 | Also see https://opensource.org/licenses/bsd-license.html
 |  - Encoding was converted to UTF-8.
 |  - This notice was added.
 |
 | NOTE: To use this file with StopFilterFactory, you must specify format="snowball"

a
ach
ag
agus
an
aon
ar
arna
as
b'
ba
beirt
bhúr
caoga
ceathair
ceathrar
chomh
chtó
chuig
chun
cois
céad
cúig
cúigear
d'
daichead
dar
de
deich
deichniúr
den
dhá
do
don
dtí
dá
dár
dó
faoi
faoin
faoina
faoinár
fara
fiche
gach
gan
go
gur
haon
hocht
i
iad
idir
in
ina
ins
inár
is
le
leis
lena
lenár
m'
mar
mo
mé
na
nach
naoi
naonúr
ná
ní
níor
nó
nócha
ocht
ochtar
os
roimh
sa
seacht
seachtar
seachtó
seasca
seisear
siad
sibh
sinn
sna
sé
sí
tar
thar
thú
triúr
trí
trína
trínár
tríocha
tú
um
ár
é
éis
í
ó
ón
óna
ónár
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
