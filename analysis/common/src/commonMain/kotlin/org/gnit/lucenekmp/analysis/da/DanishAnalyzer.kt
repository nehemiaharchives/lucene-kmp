package org.gnit.lucenekmp.analysis.da

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
import org.tartarus.snowball.ext.DanishStemmer

/**
 * [org.gnit.lucenekmp.analysis.Analyzer] for Danish.
 *
 * @since 3.1
 */
class DanishAnalyzer : StopwordAnalyzerBase {
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
     * [StandardTokenizer] filtered with [LowerCaseFilter], [StopFilter], [SetKeywordMarkerFilter]
     * if a stem exclusion set is provided and [SnowballFilter].
     */
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = SnowballFilter(result, DanishStemmer())
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** File containing default Danish stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "danish_stop.txt"

        /**
         * Returns an unmodifiable instance of the default stop words set.
         *
         * @return default stop words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
 | From https://snowballstem.org/algorithms/danish/stop.txt
 | This file is distributed under the BSD License.
 | See https://snowballstem.org/license.html
 | Also see https://opensource.org/licenses/bsd-license.html
 |  - Encoding was converted to UTF-8.
 |  - This notice was added.
 |
 | NOTE: To use this file with StopFilterFactory, you must specify format="snowball"

 | A Danish stop word list. Comments begin with vertical bar. Each stop
 | word is at the start of a line.

 | This is a ranked list (commonest to rarest) of stopwords derived from
 | a large text sample.

og           | and
i            | in
jeg          | I
det          | that (dem. pronoun)/it (pers. pronoun)
at           | that (in front of a sentence)/to (with infinitive)
en           | a/an
den          | it (pers. pronoun)/that (dem. pronoun)
til          | to/at/for/until/against/by/of/into, more
er           | present tense of "to be"
som          | who, as
på           | on/upon/in/on/at/to/after/of/with/for, on
de           | they
med          | with/by/in, along
han          | he
af           | of/by/from/off/for/in/with/on, off
for          | at/for/to/from/by/of/ago, in front/before, because
ikke         | not
der          | who/which, there/those
var          | past tense of "to be"
mig          | me/myself
sig          | oneself/himself/herself/itself/themselves
men          | but
et           | a/an/one, one (number), someone/somebody/one
har          | present tense of "to have"
om           | round/about/for/in/a, about/around/down, if
vi           | we
min          | my
havde        | past tense of "to have"
ham          | him
hun          | she
nu           | now
over         | over/above/across/by/beyond/past/on/about, over/past
da           | then, when/as/since
fra          | from/off/since, off, since
du           | you
ud           | out
sin          | his/her/its/one's
dem          | them
os           | us/ourselves
op           | up
man          | you/one
hans         | his
hvor         | where
eller        | or
hvad         | what
skal         | must/shall etc.
selv         | myself/yourself/herself/ourselves etc., even
her          | here
alle         | all/everyone/everybody etc.
vil          | will (verb)
blev         | past tense of "to stay/to remain/to get/to become"
kunne        | could
ind          | in
når          | when
være         | present tense of "to be"
dog          | however/yet/after all
noget        | something
ville        | would
jo           | you know/you see (adv), yes
deres        | their/theirs
efter        | after/behind/according to/for/by/from, later/afterwards
ned          | down
skulle       | should
denne        | this
end          | than
dette        | this
mit          | my/mine
også         | also
under        | under/beneath/below/during, below/underneath
have         | have
dig          | you
anden        | other
hende        | her
mine         | my
alt          | everything
meget        | much/very, plenty of
sit          | his, her, its, one's
sine         | his, her, its, one's
vor          | our
mod          | against
disse        | these
hvis         | if
din          | your/yours
nogle        | some
hos          | by/at
blive        | be/become
mange        | many
ad           | by/through
bliver       | present tense of "to be/to become"
hendes       | her/hers
været        | be
thi          | for (conj)
jer          | you
sådan        | such, like this/like that
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
