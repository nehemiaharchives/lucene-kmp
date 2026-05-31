package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.Analyzer

/**
 * An Analyzer that uses [UnicodeWhitespaceTokenizer].
 *
 * @since 5.4.0
 */
class UnicodeWhitespaceAnalyzer : Analyzer() {
    /** Creates a new [UnicodeWhitespaceAnalyzer] */

    override fun createComponents(fieldName: String): TokenStreamComponents {
        return TokenStreamComponents(UnicodeWhitespaceTokenizer())
    }
}

