package org.gnit.lucenekmp.analysis.fi

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [FinnishLightStemFilter].
 *
 * @since 3.1.0
 * @lucene.spi {@value #NAME}
 */
class FinnishLightStemFilterFactory : TokenFilterFactory {
    /** Creates a new FinnishLightStemFilterFactory */
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
        return FinnishLightStemFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "finnishLightStem"
    }
}
