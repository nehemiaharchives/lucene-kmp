package org.gnit.lucenekmp.analysis.es

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [SpanishPluralStemFilter]. */
class SpanishPluralStemFilterFactory : TokenFilterFactory {
    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    /** Creates a new SpanishPluralStemFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    override fun create(input: TokenStream): TokenStream {
        return SpanishPluralStemFilter(input)
    }

    companion object {
        const val NAME: String = "spanishPluralStem"
    }
}
