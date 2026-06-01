package org.gnit.lucenekmp.analysis.reverse

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/** Factory for [ReverseStringFilter]. */
class ReverseStringFilterFactory : TokenFilterFactory {
    companion object {
        const val NAME = "reverseString"
    }

    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return ReverseStringFilter(input)
    }
}
