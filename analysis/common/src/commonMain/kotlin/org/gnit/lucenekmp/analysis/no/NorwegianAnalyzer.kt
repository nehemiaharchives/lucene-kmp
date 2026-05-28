package org.gnit.lucenekmp.analysis.no

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
import org.tartarus.snowball.ext.NorwegianStemmer

/** Analyzer for Norwegian. */
class NorwegianAnalyzer : StopwordAnalyzerBase {
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
        result = SnowballFilter(result, NorwegianStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Norwegian stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "norwegian_stop.txt"

        /**
         * Returns an unmodifiable instance of the default stop words set.
         *
         * @return default stop words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
 | From https://snowballstem.org/algorithms/norwegian/stop.txt
 | This file is distributed under the BSD License.
 | See https://snowballstem.org/license.html
 | Also see https://opensource.org/licenses/bsd-license.html
 |  - Encoding was converted to UTF-8.
 |  - This notice was added.
 |
 | NOTE: To use this file with StopFilterFactory, you must specify format="snowball"

 | A Norwegian stop word list. Comments begin with vertical bar. Each stop
 | word is at the start of a line.

 | This stop word list is for the dominant bokmål dialect. Words unique
 | to nynorsk are marked *.

 | Revised by Jan Bruusgaard <Jan.Bruusgaard@ssb.no>, Jan 2005

og             | and
i              | in
jeg            | I
det            | it/this/that
at             | to (w. inf.)
en             | a/an
et             | a/an
den            | it/this/that
til            | to
er             | is/am/are
som            | who/which/that
på             | on
de             | they / you(formal)
med            | with
han            | he
av             | of
ikke           | not
ikkje          | not *
der            | there
så             | so
var            | was/were
meg            | me
seg            | you
men            | but
ett            | one
har            | have
om             | about
vi             | we
min            | my
mitt           | my
ha             | have
hadde          | had
hun            | she
nå             | now
over           | over
da             | when/as
ved            | by/know
fra            | from
du             | you
ut             | out
sin            | your
dem            | them
oss            | us
opp            | up
man            | you/one
kan            | can
hans           | his
hvor           | where
eller          | or
hva            | what
skal           | shall/must
selv           | self (reflective)
sjøl           | self (reflective)
her            | here
alle           | all
vil            | will
bli            | become
ble            | became
blei           | became *
blitt          | have become
kunne          | could
inn            | in
når            | when
være           | be
kom            | come
noen           | some
noe            | some
ville          | would
dere           | you
deres          | their/theirs
kun            | only/just
ja             | yes
etter          | after
ned            | down
skulle         | should
denne          | this
for            | for/because
deg            | you
si             | hers/his
sine           | hers/his
sitt           | hers/his
mot            | against
å              | to
meget          | much
hvorfor        | why
dette          | this
disse          | these/those
uten           | without
hvordan        | how
ingen          | none
din            | your
ditt           | your
blir           | become
samme          | same
hvilken        | which
hvilke         | which (plural)
sånn           | such a
inni           | inside/within
mellom         | between
vår            | our
hver           | each
hvem           | who
vors           | us/ours
hvis           | whose
både           | both
bare           | only/just
enn            | than
fordi          | as/because
før            | before
mange          | many
også           | also
slik           | just
vært           | been
båe            | both *
begge          | both
siden          | since
dykk           | your *
dykkar         | yours *
dei            | they *
deira          | them *
deires         | theirs *
deim           | them *
di             | your (fem.) *
då             | as/when *
eg             | I *
ein            | a/an *
eit            | a/an *
eitt           | a/an *
elles          | or *
honom          | he *
hjå            | at *
ho             | she *
hoe            | she *
henne          | her
hennar         | her/hers
hennes         | hers
hoss           | how *
hossen         | how *
ingi           | noone *
inkje          | noone *
korleis        | how *
korso          | how *
kva            | what/which *
kvar           | where *
kvarhelst      | where *
kven           | who/whom *
kvi            | why *
kvifor         | why *
me             | we *
medan          | while *
mi             | my *
mine           | my *
mykje          | much *
no             | now *
nokon          | some (masc./neut.) *
noka           | some (fem.) *
nokor          | some *
noko           | some *
nokre          | some *
sia            | since *
sidan          | since *
so             | so *
somt           | some *
somme          | some *
um             | about*
upp            | up *
vere           | be *
vore           | was *
verte          | become *
vort           | become *
varte          | became *
vart           | became *
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
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}

