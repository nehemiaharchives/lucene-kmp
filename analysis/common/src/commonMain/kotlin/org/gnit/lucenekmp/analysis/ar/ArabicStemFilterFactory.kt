package org.gnit.lucenekmp.analysis.ar

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [ArabicStemFilter]. */
class ArabicStemFilterFactory : TokenFilterFactory {
    /** Creates a new ArabicStemFilterFactory */
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
        return ArabicStemFilter(input)
    }

    companion object {
        const val NAME: String = "arabicStem"
    }
}

