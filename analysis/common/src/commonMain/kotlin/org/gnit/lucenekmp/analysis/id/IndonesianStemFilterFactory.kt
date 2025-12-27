package org.gnit.lucenekmp.analysis.id

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [IndonesianStemFilter]. */
class IndonesianStemFilterFactory : TokenFilterFactory {
    private var stemDerivational: Boolean = true

    /** Creates a new IndonesianStemFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        stemDerivational = getBoolean(args, "stemDerivational", true)
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return IndonesianStemFilter(input, stemDerivational)
    }

    companion object {
        const val NAME: String = "indonesianStem"
    }
}
