package org.gnit.lucenekmp.analysis.pa

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [PunjabiStemFilter]. */
class PunjabiStemFilterFactory : TokenFilterFactory {
    /** Creates a new PunjabiStemFilterFactory */
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
        return PunjabiStemFilter(input)
    }

    companion object {
        const val NAME: String = "punjabiStem"
    }
}
