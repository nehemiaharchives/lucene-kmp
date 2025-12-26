package org.gnit.lucenekmp.analysis.es

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [SpanishLightStemFilter]. */
class SpanishLightStemFilterFactory : TokenFilterFactory {
    /** Creates a new SpanishLightStemFilterFactory */
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
        return SpanishLightStemFilter(input)
    }

    companion object {
        const val NAME: String = "spanishLightStem"
    }
}
