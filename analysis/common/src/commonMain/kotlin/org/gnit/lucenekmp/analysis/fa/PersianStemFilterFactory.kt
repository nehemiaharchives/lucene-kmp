package org.gnit.lucenekmp.analysis.fa

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [PersianStemFilter]. */
class PersianStemFilterFactory : TokenFilterFactory {
    /** Creates a new PersianStemFilterFactory */
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
        return PersianStemFilter(input)
    }

    companion object {
        const val NAME: String = "persianStem"
    }
}

