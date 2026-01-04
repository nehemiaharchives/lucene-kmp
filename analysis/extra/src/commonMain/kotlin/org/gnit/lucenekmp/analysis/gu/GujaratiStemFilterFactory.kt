package org.gnit.lucenekmp.analysis.gu

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [GujaratiStemFilter]. */
class GujaratiStemFilterFactory : TokenFilterFactory {
    /** Creates a new GujaratiStemFilterFactory */
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
        return GujaratiStemFilter(input)
    }

    companion object {
        const val NAME: String = "gujaratiStem"
    }
}
