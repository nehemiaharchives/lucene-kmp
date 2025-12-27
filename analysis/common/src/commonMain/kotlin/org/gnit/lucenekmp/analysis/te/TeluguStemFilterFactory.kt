package org.gnit.lucenekmp.analysis.te

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [TeluguStemFilter]. */
class TeluguStemFilterFactory : TokenFilterFactory {
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
        return TeluguStemFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "teluguStem"
    }
}
