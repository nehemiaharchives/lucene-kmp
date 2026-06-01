package org.gnit.lucenekmp.analysis.fr

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [FrenchLightStemFilter].
 *
 * @since 3.1.0
 * @lucene.spi {@value #NAME}
 */
class FrenchLightStemFilterFactory : TokenFilterFactory {
    /** Creates a new FrenchLightStemFilterFactory */
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
        return FrenchLightStemFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "frenchLightStem"
    }
}
