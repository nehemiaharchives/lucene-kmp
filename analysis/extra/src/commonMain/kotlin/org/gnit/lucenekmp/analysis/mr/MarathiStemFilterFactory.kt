package org.gnit.lucenekmp.analysis.mr

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [MarathiStemFilter]. */
class MarathiStemFilterFactory : TokenFilterFactory {
    /** Creates a new MarathiStemFilterFactory */
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
        return MarathiStemFilter(input)
    }

    companion object {
        const val NAME: String = "marathiStem"
    }
}
