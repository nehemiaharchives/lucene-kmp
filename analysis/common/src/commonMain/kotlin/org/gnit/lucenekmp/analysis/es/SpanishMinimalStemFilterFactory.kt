package org.gnit.lucenekmp.analysis.es

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [SpanishMinimalStemFilter].
 *
 * @lucene.spi {@value #NAME}
 * @deprecated Use [SpanishPluralStemFilterFactory] instead
 */
@Deprecated("Use SpanishPluralStemFilterFactory instead")
class SpanishMinimalStemFilterFactory : TokenFilterFactory {
    /** Creates a new SpanishMinimalStemFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return SpanishMinimalStemFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "spanishMinimalStem"
    }
}
