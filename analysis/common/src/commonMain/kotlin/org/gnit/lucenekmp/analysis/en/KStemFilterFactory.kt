package org.gnit.lucenekmp.analysis.en

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [KStemFilter]. */
class KStemFilterFactory : TokenFilterFactory {
    /** Creates a new KStemFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): KStemFilter {
        return KStemFilter(input)
    }

    companion object {
        const val NAME: String = "kStem"
    }
}
