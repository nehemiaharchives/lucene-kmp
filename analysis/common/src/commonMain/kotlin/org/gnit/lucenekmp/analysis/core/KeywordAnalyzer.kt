package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.Analyzer

/**
 * "Tokenizes" the entire stream as a single token. This is useful for data like zip codes, ids, and
 * some product names.
 *
 * @since 3.1
 */
class KeywordAnalyzer : Analyzer() {
    override fun createComponents(fieldName: String): TokenStreamComponents {
        return TokenStreamComponents(KeywordTokenizer())
    }
}

