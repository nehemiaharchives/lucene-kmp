package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.AnalyzerWrapper

/**
 * This Analyzer limits the number of tokens while indexing. It is a replacement for the maximum
 * field length setting inside [org.gnit.lucenekmp.index.IndexWriter].
 *
 * @see LimitTokenCountFilter
 * @since 3.1
 */
class LimitTokenCountAnalyzer : AnalyzerWrapper {
    private val delegate: Analyzer
    private val maxTokenCount: Int
    private val consumeAllTokens: Boolean

    /**
     * Build an analyzer that limits the maximum number of tokens per field. This analyzer will not
     * consume any tokens beyond the maxTokenCount limit
     *
     * @see LimitTokenCountAnalyzer
     */
    constructor(delegate: Analyzer, maxTokenCount: Int) : this(delegate, maxTokenCount, false)

    /**
     * Build an analyzer that limits the maximum number of tokens per field.
     *
     * @param delegate the analyzer to wrap
     * @param maxTokenCount max number of tokens to produce
     * @param consumeAllTokens whether all tokens from the delegate should be consumed even if
     * maxTokenCount is reached.
     */
    constructor(delegate: Analyzer, maxTokenCount: Int, consumeAllTokens: Boolean) :
        super(delegate.reuseStrategy) {
        this.delegate = delegate
        this.maxTokenCount = maxTokenCount
        this.consumeAllTokens = consumeAllTokens
    }

    override fun getWrappedAnalyzer(fieldName: String): Analyzer {
        return delegate
    }

    override fun wrapComponents(fieldName: String, components: TokenStreamComponents): TokenStreamComponents {
        return TokenStreamComponents(
            components.getSource(),
            LimitTokenCountFilter(components.tokenStream, maxTokenCount, consumeAllTokens)
        )
    }

    override fun toString(): String {
        return "LimitTokenCountAnalyzer($delegate, maxTokenCount=$maxTokenCount, consumeAllTokens=$consumeAllTokens)"
    }
}
