package org.gnit.lucenekmp.analysis.payloads

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [TypeAsPayloadTokenFilter].
 *
 * @since 3.1
 * @lucene.spi [NAME]
 */
class TypeAsPayloadTokenFilterFactory : TokenFilterFactory {
    /** Creates a new TypeAsPayloadTokenFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() : super() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TypeAsPayloadTokenFilter {
        return TypeAsPayloadTokenFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME = "typeAsPayload"
    }
}
