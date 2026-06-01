package org.gnit.lucenekmp.analysis.shingle

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [FixedShingleFilter]
 *
 * <p>Parameters are:
 *
 * <ul>
 *   <li>shingleSize - how many tokens should be combined into each shingle (default: 2)
 *   <li>tokenSeparator - how tokens should be joined together in the shingle (default: space)
 *   <li>fillerToken - what should be added in place of stop words (default: _ )
 * </ul>
 *
 * @since 7.4.0
 */
class FixedShingleFilterFactory(args: MutableMap<String, String>) : TokenFilterFactory(args) {
    companion object {
        /** SPI name */
        const val NAME = "fixedShingle"
    }

    private val shingleSize: Int = getInt(args, "shingleSize", 2)
    private val tokenSeparator: String = get(args, "tokenSeparator", " ")
    private val fillerToken: String = get(args, "fillerToken", "_")

    init {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() : this(mutableMapOf()) {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return FixedShingleFilter(input, shingleSize, tokenSeparator, fillerToken)
    }
}
