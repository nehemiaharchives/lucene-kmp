package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [FixBrokenOffsetsFilter].
 *
 * @since 7.0.0
 * @lucene.spi {@value #NAME}
 */
@Deprecated("")
class FixBrokenOffsetsFilterFactory : TokenFilterFactory {
    /** Sole constructor */
    constructor(args: MutableMap<String, String>) : super(args)

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return FixBrokenOffsetsFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME = "fixBrokenOffsets"
    }
}
