package org.gnit.lucenekmp.analysis.pt

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [PortugueseLightStemFilter]. */
class PortugueseLightStemFilterFactory : TokenFilterFactory {
    /** Creates a new PortugueseLightStemFilterFactory */
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
        return PortugueseLightStemFilter(input)
    }

    companion object {
        const val NAME: String = "portugueseLightStem"
    }
}
