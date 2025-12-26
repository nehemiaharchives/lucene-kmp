package org.gnit.lucenekmp.analysis.fr

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

/**
 * Analyzer for French language.
 *
 * Supports an external list of stopwords (words that will not be indexed at all) and an external
 * list of exclusions (word that will not be stemmed, but indexed). A default set of stopwords is
 * used unless an alternative list is specified, but the exclusion list is empty by default.
 */
class FrenchAnalyzer : StopwordAnalyzerBase {
    /** Contains words that should be indexed but not stemmed. */
    private val exclTable: CharArraySet

    /** Builds an analyzer with the default stop words. */
    constructor() : this(DefaultSetHolder.DEFAULT_STOP_SET)

    /**
     * Builds an analyzer with the given stop words.
     *
     * @param stopwords a stopword set
     */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /**
     * Builds an analyzer with the given stop words.
     *
     * @param stopwords a stopword set
     * @param stemExclusionSet a stemming exclusion set
     */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.exclTable = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = ElisionFilter(source, DEFAULT_ARTICLES)
        result = LowerCaseFilter(result)
        result = StopFilter(result, stopwords)
        if (!exclTable.isEmpty()) {
            result = SetKeywordMarkerFilter(result, exclTable)
        }
        result = FrenchLightStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = ElisionFilter(`in`, DEFAULT_ARTICLES)
        result = LowerCaseFilter(result)
        return result
    }

    companion object {
        /** File containing default French stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "french_stop.txt"

        /** Default set of articles for ElisionFilter. */
        val DEFAULT_ARTICLES: CharArraySet = CharArraySet.unmodifiableSet(
            CharArraySet(
                mutableListOf<Any>(
                    "l",
                    "m",
                    "t",
                    "qu",
                    "n",
                    "s",
                    "j",
                    "d",
                    "c",
                    "jusqu",
                    "quoiqu",
                    "lorsqu",
                    "puisqu"
                ),
                true
            )
        )

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
 | From https://snowballstem.org/algorithms/french/stop.txt
 | This file is distributed under the BSD License.
 | See https://snowballstem.org/license.html
 | Also see https://opensource.org/licenses/bsd-license.html
 |  - Encoding was converted to UTF-8.
 |  - This notice was added.
 |
 | NOTE: To use this file with StopFilterFactory, you must specify format="snowball"

 | A French stop word list. Comments begin with vertical bar. Each stop
 | word is at the start of a line.

au             |  a + le
aux            |  a + les
avec           |  with
ce             |  this
ces            |  these
dans           |  with
de             |  of
des            |  de + les
du             |  de + le
elle           |  she
en             |  `of them' etc
et             |  and
eux            |  them
il             |  he
je             |  I
la             |  the
le             |  the
leur           |  their
lui            |  him
ma             |  my (fem)
mais           |  but
me             |  me
même           |  same; as in moi-même (myself) etc
mes            |  me (pl)
moi            |  me
mon            |  my (masc)
ne             |  not
nos            |  our (pl)
notre          |  our
nous           |  we
on             |  one
ou             |  where
par            |  by
pas            |  not
pour           |  for
qu             |  que before vowel
que            |  that
qui            |  who
sa             |  his, her (fem)
se             |  oneself
ses            |  his (pl)
 | son            |  his, her (masc). Omitted because it is homonym of "sound"
sur            |  on
ta             |  thy (fem)
te             |  thee
tes            |  thy (pl)
toi            |  thee
ton            |  thy (masc)
tu             |  thou
un             |  a
une            |  a
vos            |  your (pl)
votre          |  your
vous           |  you

               |  single letter forms

c              |  c'
d              |  d'
j              |  j'
l              |  l'
à              |  to, at
m              |  m'
n              |  n'
s              |  s'
t              |  t'
y              |  there

               | forms of être (not including the infinitive):
 | été - Omitted because it is homonym of "summer"
étée
étées
 | étés - Omitted because it is homonym of "summers"
étant
suis
es
 | est - Omitted because it is homonym of "east"
 | sommes - Omitted because it is homonym of "sums"
êtes
sont
serai
seras
sera
serons
serez
seront
serais
serait
serions
seriez
seraient
étais
était
étions
étiez
étaient
fus
fut
fûmes
fûtes
furent
sois
soit
soyons
soyez
soient
fusse
fusses
 | fût - Omitted because it is homonym of "tap", like in "beer on tap"
fussions
fussiez
fussent

               | forms of avoir (not including the infinitive):
ayant
eu
eue
eues
eus
ai
 | as - Omitted because it is homonym of "ace"
avons
avez
ont
aurai
 | auras - Omitted because it is also the name of a kind of wind
 | aura - Omitted because it is also the name of a kind of wind and homonym of "aura"
aurons
aurez
auront
aurais
aurait
aurions
auriez
auraient
avais
avait
 | avions - Omitted because it is homonym of "planes"
aviez
avaient
eut
eûmes
eûtes
eurent
aie
aies
ait
ayons
ayez
aient
eusse
eusses
eût
eussions
eussiez
eussent

               | Later additions (from Jean-Christophe Deschamps)
ceci           |  this
cela           |  that (added 11 Apr 2012. Omission reported by Adrien Grand)
celà           |  that (incorrect, though common)
cet            |  this
cette          |  this
ici            |  here
ils            |  they
les            |  the (pl)
leurs          |  their (pl)
quel           |  which
quels          |  which
quelle         |  which
quelles        |  which
sans           |  without
soi            |  oneself
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
