package org.gnit.lucenekmp.analysis.core

import okio.IOException
import okio.Path
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.LowerCaseFilter as CoreLowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter as CoreStopFilter
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.util.IOUtils

/**
 * Filters [LetterTokenizer] with [CoreLowerCaseFilter] and [CoreStopFilter].
 *
 * @since 3.1
 */
class StopAnalyzer : StopwordAnalyzerBase {
    /**
     * Builds an analyzer with the stop words from the given set.
     *
     * @param stopWords Set of stop words
     */
    constructor(stopWords: CharArraySet) : super(stopWords)

    /**
     * Builds an analyzer with the stop words from the given path.
     *
     * @see WordlistLoader.getWordSet
     * @param stopwordsFile File to load stop words from
     */
    @Throws(IOException::class)
    constructor(stopwordsFile: Path) : this(loadStopwordSetFromPath(stopwordsFile))

    /**
     * Builds an analyzer with the stop words from the given reader.
     *
     * @see WordlistLoader.getWordSet
     * @param stopwords Reader to load stop words from
     */
    @Throws(IOException::class)
    constructor(stopwords: Reader) : this(loadStopwordSetFromReader(stopwords))

    /**
     * Creates [org.gnit.lucenekmp.analysis.Analyzer.TokenStreamComponents] used to tokenize all
     * the text in the provided [Reader].
     *
     * @return [org.gnit.lucenekmp.analysis.Analyzer.TokenStreamComponents] built from a [LetterTokenizer] filtered with [CoreStopFilter]
     */
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = LetterTokenizer()
        return TokenStreamComponents(source, CoreStopFilter(CoreLowerCaseFilter(source), stopwords))
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return CoreLowerCaseFilter(`in`)
    }

    companion object {
        @Throws(IOException::class)
        private fun loadStopwordSetFromPath(stopwords: Path): CharArraySet {
            Files.newBufferedReader(stopwords, StandardCharsets.UTF_8).use { reader ->
                return WordlistLoader.getWordSet(reader)
            }
        }

        @Throws(IOException::class)
        private fun loadStopwordSetFromReader(stopwords: Reader): CharArraySet {
            try {
                return WordlistLoader.getWordSet(stopwords)
            } finally {
                IOUtils.close(stopwords)
            }
        }
    }
}
