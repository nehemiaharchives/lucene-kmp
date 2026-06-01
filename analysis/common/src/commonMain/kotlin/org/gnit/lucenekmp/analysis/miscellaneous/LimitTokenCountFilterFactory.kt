package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for [LimitTokenCountFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_lngthcnt" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.LimitTokenCountFilterFactory" maxTokenCount="10" consumeAllTokens="false" /&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * <p>The `consumeAllTokens` property is optional and defaults to `false`. See [LimitTokenCountFilter]
 * for an explanation of its use.
 *
 * @since 3.1.0
 * @lucene.spi {@value #NAME}
 */
class LimitTokenCountFilterFactory : TokenFilterFactory {
    var maxTokenCount: Int by Delegates.notNull()
    var consumeAllTokens: Boolean by Delegates.notNull()

    /** Creates a new LimitTokenCountFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        maxTokenCount = requireInt(args, MAX_TOKEN_COUNT_KEY)
        consumeAllTokens = getBoolean(args, CONSUME_ALL_TOKENS_KEY, false)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return LimitTokenCountFilter(input, maxTokenCount, consumeAllTokens)
    }

    companion object {
        /** SPI name */
        const val NAME = "limitTokenCount"
        const val MAX_TOKEN_COUNT_KEY = "maxTokenCount"
        const val CONSUME_ALL_TOKENS_KEY = "consumeAllTokens"
    }
}
