package org.gnit.lucenekmp.analysis.hu

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [HungarianLightStemFilter].
 *
 * @since 3.1.0
 */
class HungarianLightStemFilterFactory : TokenFilterFactory {
    /** Creates a new HungarianLightStemFilterFactory */
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
        return HungarianLightStemFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "hungarianLightStem"
    }
}
