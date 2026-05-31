package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.Analyzer

/**
 * An Analyzer that uses [WhitespaceTokenizer].
 *
 * @since 3.1
 */
class WhitespaceAnalyzer(
    private val maxTokenLength: Int = WhitespaceTokenizer.DEFAULT_MAX_WORD_LEN
) : Analyzer() {
    override fun createComponents(fieldName: String): TokenStreamComponents {
        return TokenStreamComponents(WhitespaceTokenizer(maxTokenLength))
    }
}
