package org.gnit.lucenekmp.analysis.bn

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [BengaliStemFilter]. */
class BengaliStemFilterFactory : TokenFilterFactory {
    /** Creates a new BengaliStemFilterFactory. */
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
        return BengaliStemFilter(input)
    }

    companion object {
        const val NAME: String = "bengaliStem"
    }
}
