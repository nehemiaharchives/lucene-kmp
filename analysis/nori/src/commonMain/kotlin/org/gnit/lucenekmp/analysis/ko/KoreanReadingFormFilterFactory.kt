package org.gnit.lucenekmp.analysis.ko

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [KoreanReadingFormFilter]. */
class KoreanReadingFormFilterFactory : TokenFilterFactory {
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream = KoreanReadingFormFilter(input)

    companion object {
        const val NAME: String = "koreanReadingForm"
    }
}
