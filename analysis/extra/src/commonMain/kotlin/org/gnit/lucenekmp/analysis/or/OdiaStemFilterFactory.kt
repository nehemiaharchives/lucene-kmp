package org.gnit.lucenekmp.analysis.or

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [OdiaStemFilter]. */
class OdiaStemFilterFactory : TokenFilterFactory {
    /** Creates a new OdiaStemFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return OdiaStemFilter(input)
    }

    companion object {
        const val NAME: String = "odiaStem"
    }
}
