package org.gnit.lucenekmp.analysis.hu

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

/**
 * [org.gnit.lucenekmp.analysis.Analyzer] for Hungarian.
 *
 * @since 3.1
 */
class HungarianAnalyzer : StopwordAnalyzerBase {
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
     * @return A [org.gnit.lucenekmp.analysis.Analyzer.TokenStreamComponents] built from a
     * [StandardTokenizer] filtered with [LowerCaseFilter], [StopFilter],
     * [SetKeywordMarkerFilter] if a stem exclusion set is provided and [HungarianLightStemFilter].
     */
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = HungarianLightStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Hungarian stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "hungarian_stop.txt"

        /**
         * Returns an unmodifiable instance of the default stop words set.
         *
         * @return default stop words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
 | From https://snowballstem.org/algorithms/hungarian/stop.txt
 | This file is distributed under the BSD License.
 | See https://snowballstem.org/license.html
 | Also see https://opensource.org/licenses/bsd-license.html
 |  - Encoding was converted to UTF-8.
 |  - This notice was added.
 |
 | NOTE: To use this file with StopFilterFactory, you must specify format="snowball"

| Hungarian stop word list
| prepared by Anna Tordai

a
ahogy
ahol
aki
akik
akkor
alatt
által
általában
amely
amelyek
amelyekben
amelyeket
amelyet
amelynek
ami
amit
amolyan
amíg
amikor
át
abban
ahhoz
annak
arra
arról
az
azok
azon
azt
azzal
azért
aztán
azután
azonban
bár
be
belül
benne
cikk
cikkek
cikkeket
csak
de
e
eddig
egész
egy
egyes
egyetlen
egyéb
egyik
egyre
ekkor
el
elég
ellen
elő
először
előtt
első
én
éppen
ebben
ehhez
emilyen
ennek
erre
ez
ezt
ezek
ezen
ezzel
ezért
és
fel
felé
hanem
hiszen
hogy
hogyan
igen
így
illetve
ill.
ill
ilyen
ilyenkor
ison
ismét
itt
jó
jól
jobban
kell
kellett
keresztül
keressünk
ki
kívül
között
közül
legalább
lehet
lehetett
legyen
lenne
lenni
lesz
lett
maga
magát
majd
majd
már
más
másik
meg
még
mellett
mert
mely
melyek
mi
mit
míg
miért
milyen
mikor
minden
mindent
mindenki
mindig
mint
mintha
mivel
most
nagy
nagyobb
nagyon
ne
néha
nekem
neki
nem
néhány
nélkül
nincs
olyan
ott
össze
ő
ők
őket
pedig
persze
rá
s
saját
sem
semmi
sok
sokat
sokkal
számára
szemben
szerint
szinte
talán
tehát
teljes
tovább
továbbá
több
úgy
ugyanis
új
újabb
újra
után
utána
utolsó
vagy
vagyis
valaki
valami
valamint
való
vagyok
van
vannak
volt
voltam
voltak
voltunk
vissza
vele
viszont
volna
"""

        /**
         * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class accesses the
         * static final set the first time.;
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
