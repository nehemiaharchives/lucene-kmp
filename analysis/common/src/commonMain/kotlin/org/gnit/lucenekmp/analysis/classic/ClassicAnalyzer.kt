package org.gnit.lucenekmp.analysis.classic

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
 * Filters [ClassicTokenizer] with [ClassicFilter], [LowerCaseFilter] and [StopFilter], using a
 * list of English stop words.
 *
 * <p>ClassicAnalyzer was named StandardAnalyzer in Lucene versions prior to 3.1. As of 3.1,
 * [StandardAnalyzer] implements Unicode text segmentation, as specified by UAX#29.
 *
 * @since 3.1
 */
class ClassicAnalyzer : StopwordAnalyzerBase {
    /** Default maximum allowed token length */
    private var maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH

    /**
     * An unmodifiable set containing some common English words that are usually not useful for
     * searching.
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
    constructor(stopwords: Reader) : this(loadStopwordSet(stopwords))

    /**
     * Set maximum allowed token length. If a token is seen that exceeds this length then it is
     * discarded. This setting only takes effect the next time tokenStream or tokenStream is
     * called.
     */
    fun setMaxTokenLength(length: Int) {
        maxTokenLength = length
    }

    /**
     * @see setMaxTokenLength
     */
    fun getMaxTokenLength(): Int = maxTokenLength

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val src = ClassicTokenizer()
        src.setMaxTokenLength(maxTokenLength)
        var tok: TokenStream = ClassicFilter(src)
        tok = LowerCaseFilter(tok)
        tok = StopFilter(tok, stopwords)
        return TokenStreamComponents(
            { r ->
                src.setMaxTokenLength(this.maxTokenLength)
                src.setReader(r)
            },
            tok
        )
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream = LowerCaseFilter(`in`)

    companion object {
        const val DEFAULT_MAX_TOKEN_LENGTH: Int = 255
        val STOP_WORDS_SET: CharArraySet = EnglishAnalyzer.ENGLISH_STOP_WORDS_SET
    }
}
