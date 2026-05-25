package org.gnit.lucenekmp.analysis.sw

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [SwahiliStemFilter]. */
class SwahiliStemFilterFactory : TokenFilterFactory {
    /** Creates a new SwahiliStemFilterFactory */
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
        return SwahiliStemFilter(input)
    }

    override fun normalize(input: TokenStream): TokenStream {
        return create(input)
    }

    companion object {
        const val NAME: String = "swahiliStem"
    }
}
