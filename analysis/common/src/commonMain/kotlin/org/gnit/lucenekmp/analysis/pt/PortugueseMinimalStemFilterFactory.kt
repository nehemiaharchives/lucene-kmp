package org.gnit.lucenekmp.analysis.pt

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [PortugueseMinimalStemFilter]. */
class PortugueseMinimalStemFilterFactory : TokenFilterFactory {
    /** Creates a new PortugueseMinimalStemFilterFactory */
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
        return PortugueseMinimalStemFilter(input)
    }

    companion object {
        const val NAME: String = "portugueseMinimalStem"
    }
}
