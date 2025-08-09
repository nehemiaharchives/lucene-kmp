package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer

/**
 * An [Analyzer] that filters [LetterTokenizer] with [LowerCaseFilter]
 *
 * @since 3.1
 */
class SimpleAnalyzer
/** Creates a new [SimpleAnalyzer]  */
    : Analyzer() {
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tokenizer: Tokenizer = LetterTokenizer()
        return TokenStreamComponents(
            tokenizer,
            LowerCaseFilter(tokenizer)
        )
    }

    override fun normalize(
        fieldName: String,
        `in`: TokenStream
    ): TokenStream {
        return LowerCaseFilter(`in`)
    }
}
