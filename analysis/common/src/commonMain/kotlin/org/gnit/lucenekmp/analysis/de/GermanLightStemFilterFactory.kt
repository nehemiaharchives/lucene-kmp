package org.gnit.lucenekmp.analysis.de

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [GermanLightStemFilter]. */
class GermanLightStemFilterFactory : TokenFilterFactory {
    /** Creates a new GermanLightStemFilterFactory */
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
        return GermanLightStemFilter(input)
    }

    companion object {
        const val NAME: String = "germanLightStem"
    }
}
