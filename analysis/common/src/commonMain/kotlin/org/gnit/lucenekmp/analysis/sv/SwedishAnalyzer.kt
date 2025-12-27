package org.gnit.lucenekmp.analysis.sv

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
import org.tartarus.snowball.ext.SwedishStemmer

/** Analyzer for Swedish. */
class SwedishAnalyzer : StopwordAnalyzerBase {
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
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = SnowballFilter(result, SwedishStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Swedish stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "swedish_stop.txt"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
 | From https://snowballstem.org/algorithms/swedish/stop.txt
 | This file is distributed under the BSD License.
 | See https://snowballstem.org/license.html
 | Also see https://opensource.org/licenses/bsd-license.html
 |  - Encoding was converted to UTF-8.
 |  - This notice was added.
 |
 | NOTE: To use this file with StopFilterFactory, you must specify format=\"snowball\"

 | A Swedish stop word list. Comments begin with vertical bar. Each stop
 | word is at the start of a line.

 | This is a ranked list (commonest to rarest) of stopwords derived from
 | a large text sample.

 | Swedish stop words occasionally exhibit homonym clashes. For example
 |  så = so, but also seed. These are indicated clearly below.

och            | and
det            | it, this/that
att            | to (with infinitive)
i              | in, at
en             | a
jag            | I
hon            | she
som            | who, that
han            | he
på             | on
den            | it, this/that
med            | with
var            | where, each
sig            | him(self) etc
för            | for
så             | so (also: seed)
till           | to
är             | is
men            | but
ett            | a
om             | if; around, about
hade           | had
de             | they, these/those
av             | of
icke           | not, no
mig            | me
du             | you
henne          | her
då             | then, when
sin            | his
nu             | now
har            | have
inte           | inte någon = no one
hans           | his
honom          | him
skulle         | 'sake'
hennes         | her
där            | there
min            | my
man            | one (pronoun)
ej             | nor
vid            | at, by, on (also: vast)
kunde          | could
något          | some etc
från           | from, off
ut             | out
när            | when
efter          | after, behind
upp            | up
vi             | we
dem            | them
vara           | be
vad            | what
över           | over
än             | than
dig            | you
kan            | can
sina           | his
här            | here
ha             | have
mot            | towards
alla           | all
under          | under (also: wonder)
någon          | some etc
eller          | or (else)
allt           | all
mycket         | much
sedan          | since
ju             | why
denna          | this/that
själv          | myself, yourself etc
detta          | this/that
åt             | to
utan           | without
varit          | was
hur            | how
ingen          | no
mitt           | my
ni             | you
bli            | to be, become
blev           | from bli
oss            | us
din            | thy
dessa          | these/those
några          | some etc
deras          | their
blir           | from bli
mina           | my
samma          | (the) same
vilken         | who, that
er             | you, your
sådan          | such a
vår            | our
blivit         | from bli
dess           | its
inom           | within
mellan         | between
sådant         | such a
varför         | why
varje          | each
vilka          | who, that
ditt           | thy
vem            | who
vilket         | who, that
sitt           | his
sådana         | such a
vart           | each
dina           | thy
vars           | whose
vårt           | our
våra           | our
ert            | your
era            | your
vilkas         | whose
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
