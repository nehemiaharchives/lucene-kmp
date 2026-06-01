package org.gnit.lucenekmp.analysis.ngram

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * Tokenizes the input from an edge into n-grams of given size(s).
 *
 * <p>This [Tokenizer] create n-grams from the beginning edge of a input token.
 *
 * <p><a id="match_version"></a>As of Lucene 4.4, this class supports [isTokenChar]
 * pre-tokenization and correctly handles supplementary characters.
 */
class EdgeNGramTokenizer : NGramTokenizer {
    companion object {
        const val DEFAULT_MAX_GRAM_SIZE = 1
        const val DEFAULT_MIN_GRAM_SIZE = 1
    }

    constructor(minGram: Int, maxGram: Int) : super(minGram, maxGram, true)

    constructor(factory: AttributeFactory, minGram: Int, maxGram: Int) : super(factory, minGram, maxGram, true)
}
