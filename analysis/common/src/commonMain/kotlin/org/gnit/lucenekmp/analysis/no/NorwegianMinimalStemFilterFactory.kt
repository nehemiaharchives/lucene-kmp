package org.gnit.lucenekmp.analysis.no

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.no.NorwegianLightStemmer.Companion.BOKMAAL
import org.gnit.lucenekmp.analysis.no.NorwegianLightStemmer.Companion.NYNORSK

/** Factory for [NorwegianMinimalStemFilter]. */
class NorwegianMinimalStemFilterFactory : TokenFilterFactory {
    private var flags: Int = BOKMAAL

    /** Creates a new NorwegianMinimalStemFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        val variant = get(args, "variant")
        if (variant == null || "nb" == variant) {
            flags = BOKMAAL
        } else if ("nn" == variant) {
            flags = NYNORSK
        } else if ("no" == variant) {
            flags = BOKMAAL or NYNORSK
        } else {
            throw IllegalArgumentException("invalid variant: $variant")
        }
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return NorwegianMinimalStemFilter(input, flags)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "norwegianMinimalStem"
    }
}
