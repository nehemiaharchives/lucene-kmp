package org.gnit.lucenekmp.analysis.ru

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [RussianLightStemFilter]. */
class RussianLightStemFilterFactory : TokenFilterFactory {
    /** Creates a new RussianLightStemFilterFactory */
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
        return RussianLightStemFilter(input)
    }

    companion object {
        const val NAME: String = "russianLightStem"
    }
}
