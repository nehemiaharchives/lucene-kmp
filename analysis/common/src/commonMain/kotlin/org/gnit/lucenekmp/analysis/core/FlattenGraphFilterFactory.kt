package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [FlattenGraphFilter]. */
class FlattenGraphFilterFactory : TokenFilterFactory {
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return FlattenGraphFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "flattenGraph"
    }
}

