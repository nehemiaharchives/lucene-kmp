package org.gnit.lucenekmp.analysis.`as`

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [AssameseStemFilter]. */
class AssameseStemFilterFactory : TokenFilterFactory {
    /** Creates a new AssameseStemFilterFactory. */
    constructor(args: MutableMap<String, String>) : super(args) {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI. */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return AssameseStemFilter(input)
    }

    companion object {
        const val NAME: String = "assameseStem"
    }
}
