package org.gnit.lucenekmp.analysis.sv

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [SwedishMinimalStemFilter]. */
class SwedishMinimalStemFilterFactory : TokenFilterFactory {
    /** Creates a new SwedishMinimalStemFilterFactory */
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
        return SwedishMinimalStemFilter(input)
    }

    companion object {
        const val NAME: String = "swedishMinimalStem"
    }
}
