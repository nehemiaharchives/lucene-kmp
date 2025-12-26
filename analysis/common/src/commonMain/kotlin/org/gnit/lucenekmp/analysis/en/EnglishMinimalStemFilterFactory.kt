package org.gnit.lucenekmp.analysis.en

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [EnglishMinimalStemFilter]. */
class EnglishMinimalStemFilterFactory : TokenFilterFactory {
    /** Creates a new EnglishMinimalStemFilterFactory */
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
        return EnglishMinimalStemFilter(input)
    }

    companion object {
        const val NAME: String = "englishMinimalStem"
    }
}
