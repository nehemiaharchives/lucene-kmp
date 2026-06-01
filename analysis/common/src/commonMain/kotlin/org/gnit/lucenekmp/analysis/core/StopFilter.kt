package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Removes stop words from a token stream.
 *
 * <p>This class moved to Lucene Core, but a reference in the {@code analysis/common} module is
 * preserved for documentation purposes and consistency with filter factory.
 *
 * @see org.apache.lucene.analysis.StopFilter
 * @see StopFilterFactory
 */
class StopFilter
/**
 * Constructs a filter which removes words from the input TokenStream that are named in the Set.
 *
 * @param in Input stream
 * @param stopWords A [CharArraySet] representing the stopwords.
 * @see .makeStopSet
 */
    (`in`: TokenStream, stopWords: CharArraySet) : org.gnit.lucenekmp.analysis.StopFilter(`in`, stopWords)
