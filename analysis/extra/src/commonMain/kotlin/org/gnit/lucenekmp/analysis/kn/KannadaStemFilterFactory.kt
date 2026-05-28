package org.gnit.lucenekmp.analysis.kn

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [KannadaStemFilter]. */
class KannadaStemFilterFactory : TokenFilterFactory {
    /** Creates a new KannadaStemFilterFactory. */
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
        return KannadaStemFilter(input)
    }

    companion object {
        const val NAME: String = "kannadaStem"
    }
}
