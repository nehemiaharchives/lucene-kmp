package org.gnit.lucenekmp.analysis.gl

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [GalicianMinimalStemFilter].
 *
 * @since 3.6.0
 * @lucene.spi {@value #NAME}
 */
class GalicianMinimalStemFilterFactory : TokenFilterFactory {
    /** Creates a new GalicianMinimalStemFilterFactory */
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
        return GalicianMinimalStemFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "galicianMinimalStem"
    }
}
