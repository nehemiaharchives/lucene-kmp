package org.gnit.lucenekmp.analysis.br

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [BrazilianStemFilter]. */
class BrazilianStemFilterFactory : TokenFilterFactory {
    /** Creates a new BrazilianStemFilterFactory */
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
        return BrazilianStemFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "brazilianStem"
    }
}
