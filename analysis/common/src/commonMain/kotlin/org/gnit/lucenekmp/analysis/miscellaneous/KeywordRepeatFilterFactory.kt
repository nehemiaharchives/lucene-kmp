package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [KeywordRepeatFilter].
 *
 * Since [KeywordRepeatFilter] emits two tokens for every input token, and any tokens that
 * aren't transformed later in the analysis chain will be in the document twice. Therefore, consider
 * adding [RemoveDuplicatesTokenFilterFactory] later in the analysis chain.
 *
 * @since 4.3.0
 * @lucene.spi {@value #NAME}
 */
class KeywordRepeatFilterFactory : TokenFilterFactory {
    /** Creates a new KeywordRepeatFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return KeywordRepeatFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME = "keywordRepeat"
    }
}
