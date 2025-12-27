package org.gnit.lucenekmp.analysis.hi

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [HindiStemFilter]. */
class HindiStemFilterFactory : TokenFilterFactory {
    /** Creates a new HindiStemFilterFactory */
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
        return HindiStemFilter(input)
    }

    companion object {
        const val NAME: String = "hindiStem"
    }
}
