package org.gnit.lucenekmp.analysis.shingle

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [ShingleFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_shingle" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.ShingleFilterFactory" minShingleSize="2" maxShingleSize="2"
 *             outputUnigrams="true" outputUnigramsIfNoShingles="false" tokenSeparator=" " fillerToken="_"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1
 */
class ShingleFilterFactory(args: MutableMap<String, String>) : TokenFilterFactory(args) {
    companion object {
        /** SPI name */
        const val NAME = "shingle"
    }

    private val minShingleSize: Int = getInt(args, "minShingleSize", ShingleFilter.DEFAULT_MIN_SHINGLE_SIZE)
    private val maxShingleSize: Int = getInt(args, "maxShingleSize", ShingleFilter.DEFAULT_MAX_SHINGLE_SIZE)
    private val outputUnigrams: Boolean = getBoolean(args, "outputUnigrams", true)
    private val outputUnigramsIfNoShingles: Boolean = getBoolean(args, "outputUnigramsIfNoShingles", false)
    private val tokenSeparator: String = get(args, "tokenSeparator", ShingleFilter.DEFAULT_TOKEN_SEPARATOR)
    private val fillerToken: String = get(args, "fillerToken", ShingleFilter.DEFAULT_FILLER_TOKEN)

    /** Creates a new ShingleFilterFactory */
    init {
        require(maxShingleSize >= 2) {
            "Invalid maxShingleSize ($maxShingleSize) - must be at least 2"
        }
        require(minShingleSize >= 2) {
            "Invalid minShingleSize ($minShingleSize) - must be at least 2"
        }
        require(minShingleSize <= maxShingleSize) {
            "Invalid minShingleSize ($minShingleSize) - must be no greater than maxShingleSize ($maxShingleSize)"
        }
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() : this(mutableMapOf()) {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): ShingleFilter {
        val r = ShingleFilter(input, minShingleSize, maxShingleSize)
        r.setOutputUnigrams(outputUnigrams)
        r.setOutputUnigramsIfNoShingles(outputUnigramsIfNoShingles)
        r.setTokenSeparator(tokenSeparator)
        r.setFillerToken(fillerToken)
        return r
    }
}
