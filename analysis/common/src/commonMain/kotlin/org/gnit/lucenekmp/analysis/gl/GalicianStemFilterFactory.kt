package org.gnit.lucenekmp.analysis.gl

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [GalicianStemFilter].
 *
 * @since 3.1.0
 * @lucene.spi {@value #NAME}
 */
class GalicianStemFilterFactory : TokenFilterFactory {
    /** Creates a new GalicianStemFilterFactory */
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
        return GalicianStemFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "galicianStem"
    }
}
