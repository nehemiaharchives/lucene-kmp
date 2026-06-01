package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for [LimitTokenOffsetFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_limit_pos" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.LimitTokenOffsetFilter" maxStartOffset="100000" consumeAllTokens="false" /&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * <p>The `consumeAllTokens` property is optional and defaults to `false`.
 *
 * @since 5.2.0
 * @lucene.spi {@value #NAME}
 */
class LimitTokenOffsetFilterFactory : TokenFilterFactory {
    private var maxStartOffset: Int by Delegates.notNull()
    private var consumeAllTokens: Boolean by Delegates.notNull()

    constructor(args: MutableMap<String, String>) : super(args) {
        maxStartOffset = requireInt(args, MAX_START_OFFSET)
        consumeAllTokens = getBoolean(args, CONSUME_ALL_TOKENS_KEY, false)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return LimitTokenOffsetFilter(input, maxStartOffset, consumeAllTokens)
    }

    companion object {
        /** SPI name */
        const val NAME = "limitTokenOffset"
        const val MAX_START_OFFSET = "maxStartOffset"
        const val CONSUME_ALL_TOKENS_KEY = "consumeAllTokens"
    }
}
