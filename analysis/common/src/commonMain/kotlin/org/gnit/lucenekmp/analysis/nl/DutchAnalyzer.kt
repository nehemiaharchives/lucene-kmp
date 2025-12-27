package org.gnit.lucenekmp.analysis.nl

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArrayMap
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.miscellaneous.StemmerOverrideFilter
import org.gnit.lucenekmp.analysis.snowball.SnowballFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.tartarus.snowball.ext.DutchStemmer

/** Analyzer for Dutch language. */
class DutchAnalyzer : Analyzer {
    /** Contains the stopwords used with the StopFilter. */
    private val stoptable: CharArraySet

    /** Contains words that should be indexed but not stemmed. */
    private val excltable: CharArraySet

    private val stemdict: StemmerOverrideFilter.StemmerOverrideMap?

    /**
     * Builds an analyzer with the default stop words and a few default
     * entries for the stem exclusion table.
     */
    constructor() : this(
        DefaultSetHolder.DEFAULT_STOP_SET,
        CharArraySet.EMPTY_SET,
        DefaultSetHolder.DEFAULT_STEM_DICT
    )

    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET, DefaultSetHolder.DEFAULT_STEM_DICT)

    constructor(stopwords: CharArraySet, stemExclusionTable: CharArraySet) :
        this(stopwords, stemExclusionTable, DefaultSetHolder.DEFAULT_STEM_DICT)

    constructor(
        stopwords: CharArraySet,
        stemExclusionTable: CharArraySet,
        stemOverrideDict: CharArrayMap<String>
    ) {
        stoptable = CharArraySet.unmodifiableSet(CharArraySet.copy(stopwords))
        excltable = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionTable))
        stemdict = if (stemOverrideDict.isEmpty()) {
            null
        } else {
            val builder = StemmerOverrideFilter.Builder(false)
            val iter = stemOverrideDict.entrySet().iterator() as CharArrayMap<String>.EntryIterator
            val spare = CharsRefBuilder()
            while (iter.hasNext()) {
                val nextKey = iter.nextKey() ?: continue
                spare.copyChars(nextKey, 0, nextKey.size)
                builder.add(spare.get(), iter.currentValue()!!)
            }
            try {
                builder.build()
            } catch (ex: IOException) {
                throw RuntimeException("can not build stem dict", ex)
            }
        }
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stoptable)
        if (!excltable.isEmpty()) {
            result = SetKeywordMarkerFilter(result, excltable)
        }
        if (stemdict != null) {
            result = StemmerOverrideFilter(result, stemdict)
        }
        result = SnowballFilter(result, DutchStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Dutch stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "dutch_stop.txt"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
 | From https://snowballstem.org/algorithms/dutch/stop.txt
 | This file is distributed under the BSD License.
 | See https://snowballstem.org/license.html
 | Also see https://opensource.org/licenses/bsd-license.html
 |  - Encoding was converted to UTF-8.
 |  - This notice was added.
 |
 | NOTE: To use this file with StopFilterFactory, you must specify format="snowball"


 | A Dutch stop word list. Comments begin with vertical bar. Each stop
 | word is at the start of a line.

 | This is a ranked list (commonest to rarest) of stopwords derived from
 | a large sample of Dutch text.

 | Dutch stop words frequently exhibit homonym clashes. These are indicated
 | clearly below.

de             |  the
en             |  and
van            |  of, from
ik             |  I, the ego
te             |  (1) chez, at etc, (2) to, (3) too
dat            |  that, which
die            |  that, those, who, which
in             |  in, inside
een            |  a, an, one
hij            |  he
het            |  the, it
niet           |  not, nothing, naught
zijn           |  (1) to be, being, (2) his, one's, its
is             |  is
was            |  (1) was, past tense of all persons sing. of 'zijn' (to be) (2) wax, (3) the washing, (4) rise of river
op             |  on, upon, at, in, up, used up
aan            |  on, upon, to (as dative)
met            |  with, by
als            |  like, such as, when
voor           |  (1) before, in front of, (2) furrow
had            |  had, past tense all persons sing. of 'hebben' (have)
er             |  there
maar           |  but, only
om             |  round, about, for etc
hem            |  him
dan            |  then
zou            |  should/would, past tense all persons sing. of 'zullen'
of             |  or, whether, if
wat            |  what, something, anything
mijn           |  possessive and noun 'mine'
men            |  people, 'one'
dit            |  this
zo             |  so, thus, in this way
door           |  through by
over           |  over, across
ze             |  she, her, they, them
zich           |  oneself
bij            |  (1) a bee, (2) by, near, at
ook            |  also, too
tot            |  till, until
je             |  you
mij            |  me
uit            |  out of, from
der            |  Old Dutch form of 'van der' still found in surnames
daar           |  (1) there, (2) because
haar           |  (1) her, their, them, (2) hair
naar           |  (1) unpleasant, unwell etc, (2) towards, (3) as
heb            |  present first person sing. of 'to have'
hoe            |  how, why
heeft          |  present third person sing. of 'to have'
hebben         |  'to have' and various parts thereof
deze           |  this
u              |  you
want           |  (1) for, (2) mitten, (3) rigging
nog            |  yet, still
zal            |  'shall', first and third person sing. of verb 'zullen' (will)
me             |  me
zij            |  she, they
nu             |  now
ge             |  'thou', still used in Belgium and south Netherlands
geen           |  none
omdat          |  because
iets           |  something, somewhat
worden         |  to become, grow, get
toch           |  yet, still
al             |  all, every, each
waren          |  (1) 'were' (2) to wander, (3) wares, (3)
veel           |  much, many
meer           |  (1) more, (2) lake
doen           |  to do, to make
toen           |  then, when
moet           |  noun 'spot/mote' and present form of 'to must'
ben            |  (1) am, (2) 'are' in interrogative second person singular of 'to be'
zonder         |  without
kan            |  noun 'can' and present form of 'to be able'
hun            |  their, them
dus            |  so, consequently
alles          |  all, everything, anything
onder          |  under, beneath
ja             |  yes, of course
eens           |  once, one day
hier           |  here
wie            |  who
werd           |  imperfect third person sing. of 'become'
altijd         |  always
doch           |  yet, but etc
wordt          |  present third person sing. of 'become'
wezen          |  (1) to be, (2) 'been' as in 'been fishing', (3) orphans
kunnen         |  to be able
ons            |  us/our
zelf           |  self
tegen          |  against, towards, at
na             |  after, near
reeds          |  already
wil            |  (1) present tense of 'want', (2) 'will', noun, (3) fender
kon            |  could; past tense of 'to be able'
niets          |  nothing
uw             |  your
iemand         |  somebody
geweest        |  been; past participle of 'be'
andere         |  other
"""

        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet
            val DEFAULT_STEM_DICT: CharArrayMap<String>

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getSnowballWordSet(StringReader(DEFAULT_STOPWORD_DATA))
                } catch (ex: IOException) {
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }

                DEFAULT_STEM_DICT = CharArrayMap(4, false)
                DEFAULT_STEM_DICT.put("fiets", "fiets")
                DEFAULT_STEM_DICT.put("bromfiets", "bromfiets")
                DEFAULT_STEM_DICT.put("ei", "eier")
                DEFAULT_STEM_DICT.put("kind", "kinder")
            }
        }
    }
}
