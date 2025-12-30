package org.gnit.lucenekmp.analysis.ko

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.Ported

/** Factory for [KoreanNumberFilter]. */
@Ported(from = "org.apache.lucene.analysis.ko.KoreanNumberFilterFactory")
class KoreanNumberFilterFactory : TokenFilterFactory {
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream = KoreanNumberFilter(input)

    companion object {
        const val NAME: String = "koreanNumber"
    }
}
