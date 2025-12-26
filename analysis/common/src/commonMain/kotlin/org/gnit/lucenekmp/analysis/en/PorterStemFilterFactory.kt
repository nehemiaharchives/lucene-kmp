package org.gnit.lucenekmp.analysis.en

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [PorterStemFilter]. */
class PorterStemFilterFactory : TokenFilterFactory {
    /** Creates a new PorterStemFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): PorterStemFilter {
        return PorterStemFilter(input)
    }

    companion object {
        const val NAME: String = "porterStem"
    }
}
