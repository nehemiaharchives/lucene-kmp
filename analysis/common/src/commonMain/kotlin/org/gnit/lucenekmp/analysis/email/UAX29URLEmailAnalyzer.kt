package org.gnit.lucenekmp.analysis.email

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.en.EnglishAnalyzer
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.jdkport.Reader

/**
 * Filters [UAX29URLEmailTokenizer] with [org.gnit.lucenekmp.analysis.LowerCaseFilter]
 * and [org.gnit.lucenekmp.analysis.StopFilter], using a list of English stop words.
 *
 * @since 3.6.0
 */
class UAX29URLEmailAnalyzer : StopwordAnalyzerBase {
    private var maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH

    /**
     * Builds an analyzer with the given stop words.
     *
     * @param stopWords stop words
     */
    constructor(stopWords: CharArraySet) : super(stopWords)

    /** Builds an analyzer with the default stop words ([STOP_WORDS_SET]). */
    constructor() : this(STOP_WORDS_SET)

    /**
     * Builds an analyzer with the stop words from the given reader.
     *
     * @see WordlistLoader#getWordSet
     * @param stopwords Reader to read stop words from
     */
    @Throws(IOException::class)
    constructor(stopwords: Reader) : this(loadStopwordSet(stopwords))

    /**
     * Set the max allowed token length. Tokens larger than this will be chopped up at this token
     * length and emitted as multiple tokens. If you need to skip such large tokens, you could
     * increase this max length, and then use `LengthFilter` to remove long tokens. The default
     * is [UAX29URLEmailAnalyzer.DEFAULT_MAX_TOKEN_LENGTH].
     */
    fun setMaxTokenLength(length: Int) {
        maxTokenLength = length
    }

    /**
     * @see setMaxTokenLength
     */
    fun getMaxTokenLength(): Int {
        return maxTokenLength
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val src = UAX29URLEmailTokenizer()
        src.setMaxTokenLength(maxTokenLength)
        var tok: TokenStream = LowerCaseFilter(src)
        tok = StopFilter(tok, stopwords)
        return TokenStreamComponents(
            { r ->
                src.setMaxTokenLength(this.maxTokenLength)
                src.setReader(r)
            },
            tok
        )
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** Default maximum allowed token length */
        const val DEFAULT_MAX_TOKEN_LENGTH: Int = StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH

        /**
         * An unmodifiable set containing some common English words that are usually not useful for
         * searching.
         */
        val STOP_WORDS_SET: CharArraySet = EnglishAnalyzer.ENGLISH_STOP_WORDS_SET
    }
}
