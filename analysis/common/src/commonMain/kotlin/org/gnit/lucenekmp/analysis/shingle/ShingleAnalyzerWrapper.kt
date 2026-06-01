package org.gnit.lucenekmp.analysis.shingle

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.AnalyzerWrapper
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer

/**
 * A ShingleAnalyzerWrapper wraps a [ShingleFilter] around another [Analyzer].
 *
 * <p>A shingle is another name for a token based n-gram.
 */
class ShingleAnalyzerWrapper : AnalyzerWrapper {
    private val delegate: Analyzer
    val maxShingleSize: Int
    val minShingleSize: Int
    val tokenSeparator: String
    val outputUnigrams: Boolean
    val outputUnigramsIfNoShingles: Boolean
    val fillerToken: String

    constructor(defaultAnalyzer: Analyzer) : this(defaultAnalyzer, ShingleFilter.DEFAULT_MAX_SHINGLE_SIZE)

    constructor(defaultAnalyzer: Analyzer, maxShingleSize: Int) :
        this(defaultAnalyzer, ShingleFilter.DEFAULT_MIN_SHINGLE_SIZE, maxShingleSize)

    constructor(defaultAnalyzer: Analyzer, minShingleSize: Int, maxShingleSize: Int) :
        this(
            defaultAnalyzer,
            minShingleSize,
            maxShingleSize,
            ShingleFilter.DEFAULT_TOKEN_SEPARATOR,
            true,
            false,
            ShingleFilter.DEFAULT_FILLER_TOKEN
        )

    /**
     * Creates a new ShingleAnalyzerWrapper
     *
     * @param delegate Analyzer whose TokenStream is to be filtered
     * @param minShingleSize Min shingle (token ngram) size
     * @param maxShingleSize Max shingle size
     * @param tokenSeparator Used to separate input stream tokens in output shingles
     * @param outputUnigrams Whether or not the filter shall pass the original tokens to the output
     *     stream
     * @param outputUnigramsIfNoShingles Overrides the behavior of outputUnigrams==false for those
     *     times when no shingles are available (because there are fewer than minShingleSize tokens in
     *     the input stream)? Note that if outputUnigrams==true, then unigrams are always output,
     *     regardless of whether any shingles are available.
     * @param fillerToken filler token to use when positionIncrement is more than 1
     */
    constructor(
        delegate: Analyzer,
        minShingleSize: Int,
        maxShingleSize: Int,
        tokenSeparator: String?,
        outputUnigrams: Boolean,
        outputUnigramsIfNoShingles: Boolean,
        fillerToken: String
    ) : super(delegate.reuseStrategy) {
        require(maxShingleSize >= 2) { "Max shingle size must be >= 2" }
        require(minShingleSize >= 2) { "Min shingle size must be >= 2" }
        require(minShingleSize <= maxShingleSize) { "Min shingle size must be <= max shingle size" }
        this.delegate = delegate
        this.maxShingleSize = maxShingleSize
        this.minShingleSize = minShingleSize
        this.tokenSeparator = tokenSeparator ?: ""
        this.outputUnigrams = outputUnigrams
        this.outputUnigramsIfNoShingles = outputUnigramsIfNoShingles
        this.fillerToken = fillerToken
    }

    /** Wraps [StandardAnalyzer]. */
    constructor() : this(ShingleFilter.DEFAULT_MIN_SHINGLE_SIZE, ShingleFilter.DEFAULT_MAX_SHINGLE_SIZE)

    /** Wraps [StandardAnalyzer]. */
    constructor(minShingleSize: Int, maxShingleSize: Int) :
        this(StandardAnalyzer(), minShingleSize, maxShingleSize)

    override fun getWrappedAnalyzer(fieldName: String): Analyzer {
        return delegate
    }

    override fun wrapComponents(fieldName: String, components: TokenStreamComponents): TokenStreamComponents {
        val filter = ShingleFilter(components.tokenStream, minShingleSize, maxShingleSize)
        filter.setMinShingleSize(minShingleSize)
        filter.setMaxShingleSize(maxShingleSize)
        filter.setTokenSeparator(tokenSeparator)
        filter.setOutputUnigrams(outputUnigrams)
        filter.setOutputUnigramsIfNoShingles(outputUnigramsIfNoShingles)
        filter.setFillerToken(fillerToken)
        return TokenStreamComponents(components.getSource(), filter)
    }
}
