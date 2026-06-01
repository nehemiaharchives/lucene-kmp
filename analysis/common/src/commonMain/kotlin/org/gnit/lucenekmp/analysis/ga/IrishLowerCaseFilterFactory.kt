package org.gnit.lucenekmp.analysis.ga

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [IrishLowerCaseFilter].
 *
 * @since 3.6.0
 * @lucene.spi {@value #NAME}
 */
class IrishLowerCaseFilterFactory(args: MutableMap<String, String>) : TokenFilterFactory(args) {
    init {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() : this(mutableMapOf()) {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return IrishLowerCaseFilter(input)
    }

    // this will 'mostly work', except for special cases, just like most other filters
    override fun normalize(input: TokenStream): TokenStream {
        return create(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "irishLowercase"
    }
}
