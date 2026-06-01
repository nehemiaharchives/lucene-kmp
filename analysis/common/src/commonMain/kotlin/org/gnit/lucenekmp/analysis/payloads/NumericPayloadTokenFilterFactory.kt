package org.gnit.lucenekmp.analysis.payloads

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for [NumericPayloadTokenFilter].
 *
 * @since 3.1
 * @lucene.spi [NAME]
 */
class NumericPayloadTokenFilterFactory : TokenFilterFactory {
    private var payload: Float by Delegates.notNull()
    private lateinit var typeMatch: String

    /** Creates a new NumericPayloadTokenFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        payload = requireFloat(args, PAYLOAD_ATTR)
        typeMatch = require(args, TYPE_MATCH_ATTR)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() : super() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): NumericPayloadTokenFilter {
        return NumericPayloadTokenFilter(input, payload, typeMatch)
    }

    companion object {
        /** SPI name */
        const val NAME = "numericPayload"

        const val PAYLOAD_ATTR = "payload"
        const val TYPE_MATCH_ATTR = "typeMatch"
    }
}
