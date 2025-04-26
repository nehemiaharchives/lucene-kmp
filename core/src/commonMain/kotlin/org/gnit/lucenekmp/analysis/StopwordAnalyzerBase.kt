package org.gnit.lucenekmp.analysis


import org.gnit.lucenekmp.util.IOUtils
import kotlinx.io.IOException
import kotlinx.io.files.Path
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StandardCharsets

/**
 * Base class f2or Analyzers that need to make use of stopword sets.
 *
 * @since 3.1
 */
abstract class StopwordAnalyzerBase protected constructor(stopwords: CharArraySet? = null) : Analyzer() {
    /**
     * Returns the analyzer's stopword set or an empty set if the analyzer has no stopwords
     *
     * @return the analyzer's stopword set or an empty set if the analyzer has no stopwords
     */
    // analyzers should use char array set for stopwords!
    /** An immutable stopword set  */
    val stopwords: CharArraySet = if (stopwords == null)
        CharArraySet.EMPTY_SET
    else
        CharArraySet.unmodifiableSet(CharArraySet.copy(stopwords))

    /**
     * Creates a new instance initialized with the given stopword set
     *
     * @param stopwords the analyzer's stopword set
     */

    companion object {
        /**
         * Creates a CharArraySet from a path.
         *
         * @param stopwords the stopwords file to load
         * @return a CharArraySet containing the distinct stopwords from the given file
         * @throws IOException if loading the stopwords throws an [IOException]
         */
        @Throws(IOException::class)
        protected fun loadStopwordSet(stopwords: Path): CharArraySet {
            Files.newBufferedReader(stopwords, StandardCharsets.UTF_8).use { reader ->
                return WordlistLoader.getWordSet(reader)
            }
        }

        /**
         * Creates a CharArraySet from a file.
         *
         * @param stopwords the stopwords reader to load
         * @return a CharArraySet containing the distinct stopwords from the given reader
         * @throws IOException if loading the stopwords throws an [IOException]
         */
        @Throws(IOException::class)
        fun loadStopwordSet(stopwords: Reader): CharArraySet {
            try {
                return WordlistLoader.getWordSet(stopwords)
            } finally {
                IOUtils.close(stopwords)
            }
        }
    }
}
