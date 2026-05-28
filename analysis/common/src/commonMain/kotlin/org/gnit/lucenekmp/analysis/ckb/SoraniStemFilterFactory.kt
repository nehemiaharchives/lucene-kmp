package org.gnit.lucenekmp.analysis.ckb

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [SoraniStemFilter]. */
class SoraniStemFilterFactory : TokenFilterFactory {

    /** Creates a new SoraniStemFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): SoraniStemFilter {
        return SoraniStemFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "soraniStem"
    }
}

