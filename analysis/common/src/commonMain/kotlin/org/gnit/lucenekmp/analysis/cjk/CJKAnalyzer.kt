package org.gnit.lucenekmp.analysis.cjk

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/**
 * An [org.gnit.lucenekmp.analysis.Analyzer] that tokenizes text with [StandardTokenizer], normalizes
 * content with [CJKWidthFilter], folds case with [LowerCaseFilter], forms bigrams of CJK with
 * [CJKBigramFilter], and filters stopwords with [StopFilter]
 *
 * @since 3.1
 */
class CJKAnalyzer : StopwordAnalyzerBase {
    /**
     * Builds an analyzer which removes words in [getDefaultStopSet].
     */
    constructor() : this(DefaultSetHolder.DEFAULT_STOP_SET)

    /**
     * Builds an analyzer with the given stop words
     *
     * @param stopwords a stopword set
     */
    constructor(stopwords: CharArraySet) : super(stopwords)

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        // run the widthfilter first before bigramming, it sometimes combines characters.
        var result: TokenStream = CJKWidthFilter(source)
        result = LowerCaseFilter(result)
        result = CJKBigramFilter(result)
        return TokenStreamComponents(source, StopFilter(result, stopwords))
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = CJKWidthFilter(`in`)
        result = LowerCaseFilter(result)
        return result
    }

    private object DefaultSetHolder {
        val DEFAULT_STOP_SET: CharArraySet =
            try {
                WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), "#")
            } catch (ex: IOException) {
                // default set should always be present as it is part of the distribution (JAR)
                throw UncheckedIOException("Unable to load default stopword set", ex)
            }
    }

    companion object {
        /**
         * File containing default CJK stopwords.
         *
         * Currently it contains some common English words that are not usually useful for searching
         * and some double-byte interpunctions.
         */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         *
         * @return an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
a
and
are
as
at
be
but
by
for
if
in
into
is
it
no
not
of
on
or
s
such
t
that
the
their
then
there
these
they
this
to
was
will
with
www
"""
    }
}
