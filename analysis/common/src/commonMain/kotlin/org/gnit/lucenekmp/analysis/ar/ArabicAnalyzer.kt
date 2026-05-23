package org.gnit.lucenekmp.analysis.ar

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
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

/**
 * [Analyzer] for Arabic.
 *
 * This analyzer implements light-stemming as specified by: * Light Stemming for Arabic Information
 * Retrieval * http://www.mtholyoke.edu/~lballest/Pubs/arab_stem05.pdf
 *
 * The analysis package contains three primary components:
 * - [ArabicNormalizationFilter]: Arabic orthographic normalization.
 * - [ArabicStemFilter]: Arabic light stemming
 * - Arabic stop words file: a set of default Arabic stop words.
 */
class ArabicAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the given stop word and stem exclusion set. */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /** Builds an analyzer with the default stop words: [DEFAULT_STOPWORD_FILE]. */
    constructor() : this(DefaultSetHolder.DEFAULT_STOP_SET)

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = DecimalDigitFilter(result)
        // the order here is important: the stopword list is not normalized!
        result = StopFilter(result, stopwords)
        // TODO maybe we should make ArabicNormalization filter also KeywordAttribute aware?!
        result = ArabicNormalizationFilter(result)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        return TokenStreamComponents(source, ArabicStemFilter(result))
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = ArabicNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Arabic stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        /** Returns an unmodifiable instance of the default stop-words set. */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# This file was created by Jacques Savoy and is distributed under the BSD license.
# See http://members.unine.ch/jacques.savoy/clef/index.html.
# Also see http://www.opensource.org/licenses/bsd-license.html
# Cleaned on October 11, 2009 (not normalized, so use before normalization)
# This means that when modifying this list, you might need to add some 
# redundant entries, for example containing forms with both أ and ا
من
ومن
منها
منه
في
وفي
فيها
فيه
و
ف
ثم
او
أو
ب
بها
به
ا
أ
اى
اي
أي
أى
لا
ولا
الا
ألا
إلا
لكن
ما
وما
كما
فما
عن
مع
اذا
إذا
ان
أن
إن
انها
أنها
إنها
انه
أنه
إنه
بان
بأن
فان
فأن
وان
وأن
وإن
التى
التي
الذى
الذي
الذين
الى
الي
إلى
إلي
على
عليها
عليه
اما
أما
إما
ايضا
أيضا
كل
وكل
لم
ولم
لن
ولن
هى
هي
هو
وهى
وهي
وهو
فهى
فهي
فهو
انت
أنت
لك
لها
له
هذه
هذا
تلك
ذلك
هناك
كانت
كان
يكون
تكون
وكانت
وكان
غير
بعض
قد
نحو
بين
بينما
منذ
ضمن
حيث
الان
الآن
خلال
بعد
قبل
حتى
عند
عندما
لدى
جميع
"""

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

