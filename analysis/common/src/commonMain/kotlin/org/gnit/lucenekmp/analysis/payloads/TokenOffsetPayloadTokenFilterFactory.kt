package org.gnit.lucenekmp.analysis.payloads

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [TokenOffsetPayloadTokenFilter].
 *
 * @since 3.1
 * @lucene.spi [NAME]
 */
class TokenOffsetPayloadTokenFilterFactory : TokenFilterFactory {
    /** Creates a new TokenOffsetPayloadTokenFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() : super() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenOffsetPayloadTokenFilter {
        return TokenOffsetPayloadTokenFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME = "tokenOffsetPayload"
    }
}
