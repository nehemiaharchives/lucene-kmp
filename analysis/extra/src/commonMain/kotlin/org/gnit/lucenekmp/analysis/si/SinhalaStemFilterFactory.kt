package org.gnit.lucenekmp.analysis.si

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [SinhalaStemFilter]. */
class SinhalaStemFilterFactory : TokenFilterFactory {
    /** Creates a new SinhalaStemFilterFactory. */
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
        return SinhalaStemFilter(input)
    }

    companion object {
        const val NAME: String = "sinhalaStem"
    }
}
