package org.gnit.lucenekmp.analysis.de

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [GermanStemFilter]. */
class GermanStemFilterFactory : TokenFilterFactory {
    /** Creates a new GermanStemFilterFactory */
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
        return GermanStemFilter(input)
    }

    companion object {
        const val NAME: String = "germanStem"
    }
}
