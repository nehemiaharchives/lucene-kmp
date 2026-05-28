package org.gnit.lucenekmp.analysis.ckb

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/** [org.gnit.lucenekmp.analysis.Analyzer] for Sorani Kurdish. */
class SoraniAnalyzer : StopwordAnalyzerBase {
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
        var result: TokenStream = SoraniNormalizationFilter(source)
        result = LowerCaseFilter(result)
        result = DecimalDigitFilter(result)
        result = StopFilter(result, stopwords)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = SoraniStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = SoraniNormalizationFilter(`in`)
        result = LowerCaseFilter(result)
        result = DecimalDigitFilter(result)
        return result
    }

    companion object {
        /** File containing default Kurdish stopwords. */
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
# set of kurdish stopwords
# note these have been normalized with our scheme (e represented with U+06D5, etc)
# constructed from:
# * Fig 5 of "Building A Test Collection For Sorani Kurdish" (Esmaili et al)
# * "Sorani Kurdish: A Reference Grammar with selected readings" (Thackston)
# * Corpus-based analysis of 77M word Sorani collection: wikipedia, news, blogs, etc

# and
و
# which
کە
# of
ی
# made/did
کرد
# that/which
ئەوەی
# on/head
سەر
# two
دوو
# also
هەروەها
# from/that
لەو
# makes/does
دەکات
# some
چەند
# every
هەر

# demonstratives
# that
ئەو
# this
ئەم

# personal pronouns
# I
من
# we
ئێمە
# you
تۆ
# you
ئێوە
# he/she/it
ئەو
# they
ئەوان

# prepositions
# to/with/by
بە
پێ
# without
بەبێ
# along with/while/during
بەدەم
# in the opinion of
بەلای
# according to
بەپێی
# before
بەرلە
# in the direction of
بەرەوی
# in front of/toward
بەرەوە
# before/in the face of
بەردەم
# without
بێ
# except for
بێجگە
# for
بۆ
# on/in
دە
تێ
# with
دەگەڵ
# after
دوای
# except for/aside from
جگە
# in/from
لە
لێ
# in front of/before/because of
لەبەر
# between/among
لەبەینی
# concerning/about
لەبابەت
# concerning
لەبارەی
# instead of
لەباتی
# beside
لەبن
# instead of
لەبرێتی
# behind
لەدەم
# with/together with
لەگەڵ
# by
لەلایەن
# within
لەناو
# between/among
لەنێو
# for the sake of
لەپێناوی
# with respect to
لەرەوی
# by means of/for
لەرێ
# for the sake of
لەرێگا
# on/on top of/according to
لەسەر
# under
لەژێر
# between/among
ناو
# between/among
نێوان
# after
پاش
# before
پێش
# like
وەک
"""

        /**
         * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class accesses the
         * static final set the first time.
         */
        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), "#")
                } catch (ex: IOException) {
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}
