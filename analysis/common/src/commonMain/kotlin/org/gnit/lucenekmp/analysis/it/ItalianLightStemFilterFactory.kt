package org.gnit.lucenekmp.analysis.it

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [ItalianLightStemFilter]. */
class ItalianLightStemFilterFactory : TokenFilterFactory {
    /** Creates a new ItalianLightStemFilterFactory */
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
        return ItalianLightStemFilter(input)
    }

    companion object {
        const val NAME: String = "italianLightStem"
    }
}
