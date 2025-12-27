package org.gnit.lucenekmp.analysis.th

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.util.AttributeFactory

/** Factory for [ThaiTokenizer]. */
class ThaiTokenizerFactory : TokenizerFactory {
    companion object {
        /** SPI name */
        const val NAME: String = "thai"
    }

    /** Creates a new ThaiTokenizerFactory. */
    constructor(args: MutableMap<String, String>) : super(args) {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI. */
    constructor() : super() {
        throw defaultCtorException()
    }

    override fun create(factory: AttributeFactory): Tokenizer {
        return ThaiTokenizer(factory)
    }
}
