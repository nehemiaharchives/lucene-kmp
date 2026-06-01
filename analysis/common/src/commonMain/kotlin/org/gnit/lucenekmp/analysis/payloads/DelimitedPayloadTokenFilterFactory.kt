package org.gnit.lucenekmp.analysis.payloads

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware
import kotlin.properties.Delegates

/**
 * Factory for [DelimitedPayloadTokenFilter].
 *
 * @since 3.1
 * @lucene.spi [NAME]
 */
class DelimitedPayloadTokenFilterFactory : TokenFilterFactory, ResourceLoaderAware {
    companion object {
        /** SPI name */
        const val NAME = "delimitedPayload"

        const val ENCODER_ATTR = "encoder"
        const val DELIMITER_ATTR = "delimiter"
    }

    private lateinit var encoderClass: String
    private var delimiter: Char by Delegates.notNull()

    private lateinit var encoder: PayloadEncoder

    /** Creates a new DelimitedPayloadTokenFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        encoderClass = require(args, ENCODER_ATTR)
        delimiter = getChar(args, DELIMITER_ATTR, '|')
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() : super() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): DelimitedPayloadTokenFilter {
        return DelimitedPayloadTokenFilter(input, delimiter, encoder)
    }

    override fun inform(loader: ResourceLoader) {
        encoder =
            when (encoderClass) {
                "float" -> FloatEncoder()
                "integer" -> IntegerEncoder()
                "identity" -> IdentityEncoder()
                else -> loader.newInstance(encoderClass, PayloadEncoder::class)
            }
    }
}
