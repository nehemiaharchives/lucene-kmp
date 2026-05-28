package org.gnit.lucenekmp.analysis.cz

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [CzechStemFilter]. */
class CzechStemFilterFactory : TokenFilterFactory {
    /** Creates a new CzechStemFilterFactory. */
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI. */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return CzechStemFilter(input)
    }

    companion object {
        const val NAME: String = "czechStem"
    }
}
