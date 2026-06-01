package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for [FingerprintFilter].
 *
 * <pre class="prettyprint">
 * The `maxOutputTokenSize` property is optional and defaults to `1024`.
 * The `separator` property is optional and defaults to the space character.
 * See
 * [FingerprintFilter] for an explanation of its use.
 * </pre>
 *
 * @since 5.4.0
 * @lucene.spi {@value #NAME}
 */
class FingerprintFilterFactory : TokenFilterFactory {
    var maxOutputTokenSize: Int by Delegates.notNull()
    var separator: Char by Delegates.notNull()

    /** Creates a new FingerprintFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        maxOutputTokenSize = getInt(args, MAX_OUTPUT_TOKEN_SIZE_KEY, FingerprintFilter.DEFAULT_MAX_OUTPUT_TOKEN_SIZE)
        separator = getChar(args, SEPARATOR_KEY, FingerprintFilter.DEFAULT_SEPARATOR)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return FingerprintFilter(input, maxOutputTokenSize, separator)
    }

    companion object {
        /** SPI name */
        const val NAME = "fingerprint"
        const val MAX_OUTPUT_TOKEN_SIZE_KEY = "maxOutputTokenSize"
        const val SEPARATOR_KEY = "separator"
    }
}
