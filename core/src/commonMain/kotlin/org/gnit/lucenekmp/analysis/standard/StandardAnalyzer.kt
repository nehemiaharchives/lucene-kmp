package org.gnit.lucenekmp.analysis.standard

import org.gnit.lucenekmp.analysis.Analyzer.TokenStreamComponents
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.Reader

/**
 * Filters [StandardTokenizer] with [LowerCaseFilter] and [StopFilter], using a
 * configurable list of stop words.
 *
 * @since 3.1
 */
class StandardAnalyzer
/**
 * Builds an analyzer with the given stop words.
 *
 * @param stopWords stop words
 */
    (stopWords: CharArraySet) : StopwordAnalyzerBase(stopWords) {
    /**
     * Returns the current maximum token length
     *
     * @see .setMaxTokenLength
     */
    /**
     * Set the max allowed token length. Tokens larger than this will be chopped up at this token
     * length and emitted as multiple tokens. If you need to skip such large tokens, you could
     * increase this max length, and then use `LengthFilter` to remove long tokens. The default
     * is [StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH].
     */
    var maxTokenLength: Int = DEFAULT_MAX_TOKEN_LENGTH

    /** Builds an analyzer with no stop words.  */
    constructor() : this(CharArraySet.EMPTY_SET)

    /**
     * Builds an analyzer with the stop words from the given reader.
     *
     * @see WordlistLoader.getWordSet
     * @param stopwords Reader to read stop words from
     */
    constructor(stopwords: Reader) : this(loadStopwordSet(stopwords))

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val src: StandardTokenizer = StandardTokenizer()
        src.setMaxTokenLength(maxTokenLength)
        var tok: TokenStream = LowerCaseFilter(src)
        tok = StopFilter(tok, stopwords)
        return TokenStreamComponents(
            { r ->
                src.setMaxTokenLength(this@StandardAnalyzer.maxTokenLength)
                src.setReader(r)
            },
            tok
        )
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }

    companion object {
        /** Default maximum allowed token length  */
        const val DEFAULT_MAX_TOKEN_LENGTH: Int = 255
    }
}
