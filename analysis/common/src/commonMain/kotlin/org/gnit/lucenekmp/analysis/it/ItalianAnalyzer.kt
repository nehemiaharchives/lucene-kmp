package org.gnit.lucenekmp.analysis.it

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
import org.gnit.lucenekmp.analysis.util.ElisionFilter
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/** Analyzer for Italian. */
class ItalianAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the default stop words. */
    constructor() : this(DefaultSetHolder.DEFAULT_STOP_SET)

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /**
     * Builds an analyzer with the given stop words. If a non-empty stem exclusion set is provided
     * this analyzer will add a SetKeywordMarkerFilter before stemming.
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
        result = ItalianLightStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = ElisionFilter(`in`, DEFAULT_ARTICLES)
        result = LowerCaseFilter(result)
        return result
    }

    companion object {
        /** File containing default Italian stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "italian_stop.txt"

        /** Default set of articles for ElisionFilter. */
        val DEFAULT_ARTICLES: CharArraySet = CharArraySet.unmodifiableSet(
            CharArraySet(
                mutableListOf<Any>(
                    "c",
                    "l",
                    "all",
                    "dall",
                    "dell",
                    "nell",
                    "sull",
                    "coll",
                    "pell",
                    "gl",
                    "agl",
                    "dagl",
                    "degl",
                    "negl",
                    "sugl",
                    "un",
                    "m",
                    "t",
                    "s",
                    "v",
                    "d"
                ),
                true
            )
        )

        /**
         * Returns an unmodifiable instance of the default stop words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
 | From https://snowballstem.org/algorithms/italian/stop.txt
 | This file is distributed under the BSD License.
 | See https://snowballstem.org/license.html
 | Also see https://opensource.org/licenses/bsd-license.html
 |  - Encoding was converted to UTF-8.
 |  - This notice was added.
 |
 | NOTE: To use this file with StopFilterFactory, you must specify format=\"snowball\"

 | An Italian stop word list. Comments begin with vertical bar. Each stop
 | word is at the start of a line.

ad             |  a (to) before vowel
al             |  a + il
allo           |  a + lo
ai             |  a + i
agli           |  a + gli
all            |  a + l'
agl            |  a + gl'
alla           |  a + la
alle           |  a + le
con            |  with
col            |  con + il
coi            |  con + i (forms collo, cogli etc are now very rare)
da             |  from
dal            |  da + il
dallo          |  da + lo
dai            |  da + i
dagli          |  da + gli
dall           |  da + l'
dagl           |  da + gll'
dalla          |  da + la
dalle          |  da + le
di             |  of
del            |  di + il
dello          |  di + lo
dei            |  di + i
degli          |  di + gli
dell           |  di + l'
degl           |  di + gl'
della          |  di + la
delle          |  di + le
in             |  in
nel            |  in + el
nello          |  in + lo
nei            |  in + i
negli          |  in + gli
nell           |  in + l'
negl           |  in + gl'
nella          |  in + la
nelle          |  in + le
su             |  on
sul            |  su + il
sullo          |  su + lo
sui            |  su + i
sugli          |  su + gli
sull           |  su + l'
sugl           |  su + gl'
sulla          |  su + la
sulle          |  su + le
per            |  through, by
tra            |  among
contro         |  against
io             |  I
tu             |  thou
lui            |  he
lei            |  she
noi            |  we
voi            |  you
loro           |  they
mio            |  my
mia            |
miei           |
mie            |
tuo            |
tua            |
tuoi           |  thy
tue            |
suo            |
sua            |
suoi           |  his, her
sue            |
nostro         |  our
nostra         |
nostri         |
nostre         |
vostro         |  your
vostra         |
vostri         |
vostre         |
mi             |  me
ti             |  thee
ci             |  us, there
vi             |  you, there
lo             |  him, the
la             |  her, the
li             |  them
le             |  them, the
gli            |  to him, the
ne             |  from there etc
il             |  the
un             |  a
uno            |  a
una            |  a
ma             |  but
ed             |  and
se             |  if
perché         |  why, because
anche          |  also
come           |  how
dov            |  where (as dov')
dove           |  where
che            |  who, that
chi            |  who
cui            |  whom
non            |  not
più            |  more
quale          |  who, that
quanto         |  how much
quanti         |
quanta         |
quante         |
quello         |  that
quelli         |
quella         |
quelle         |
questo         |  this
questi         |
questa         |
queste         |
si             |  yes
tutto          |  all
tutti          |  all

               |  single letter forms:

a              |  at
c              |  as c' for ce or ci
e              |  and
i              |  the
l              |  as l'
o              |  or

               | forms of avere, to have (not including the infinitive):

ho
hai
ha
abbiamo
avete
hanno
abbia
abbiate
abbiano
avrò
avrai
avrà
avremo
avrete
avranno
avrei
avresti
avrebbe
avremmo
avreste
avrebbero
avevo
avevi
aveva
avevamo
avevate
avevano
ebbi
avesti
ebbe
avemmo
aveste
ebbero
avessi
avesse
avessimo
avessero
avendo
avuto
avuta
avuti
avute

               | forms of essere, to be (not including the infinitive):
sono
sei
è
siamo
siete
sia
siate
siano
sarò
sarai
sarà
saremo
sarete
saranno
sarei
saresti
sarebbe
saremmo
sareste
sarebbero
ero
eri
era
eravamo
eravate
erano
fui
fosti
fu
fummo
foste
furono
fossi
fosse
fossimo
fossero
essendo

               | forms of fare, to do (not including the infinitive, fa, fat-):
faccio
fai
facciamo
fanno
faccia
facciate
facciano
farò
farai
farà
faremo
farete
faranno
farei
faresti
farebbe
faremmo
fareste
farebbero
facevo
facevi
faceva
facevamo
facevate
facevano
feci
facesti
fece
facemmo
faceste
fecero
facessi
facesse
facessimo
facessero
facendo

               | forms of stare, to be (not including the infinitive):
sto
stai
sta
stiamo
stanno
stia
stiate
stiano
starò
starai
starà
staremo
starete
staranno
starei
staresti
starebbe
staremmo
stareste
starebbero
stavo
stavi
stava
stavamo
stavate
stavano
stetti
stesti
stette
stemmo
steste
stettero
stessi
stesse
stessimo
stessero
stando
"""

        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getSnowballWordSet(StringReader(DEFAULT_STOPWORD_DATA))
                } catch (ex: IOException) {
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}
