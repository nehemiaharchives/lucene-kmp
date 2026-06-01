package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for [LimitTokenPositionFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_limit_pos" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.LimitTokenPositionFilterFactory" maxTokenPosition="3" consumeAllTokens="false" /&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * <p>The `consumeAllTokens` property is optional and defaults to `false`. See [LimitTokenPositionFilter]
 * for an explanation of its use.
 *
 * @since 4.3.0
 * @lucene.spi {@value #NAME}
 */
class LimitTokenPositionFilterFactory : TokenFilterFactory {
    var maxTokenPosition: Int by Delegates.notNull()
    var consumeAllTokens: Boolean by Delegates.notNull()

    /** Creates a new LimitTokenPositionFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        maxTokenPosition = requireInt(args, MAX_TOKEN_POSITION_KEY)
        consumeAllTokens = getBoolean(args, CONSUME_ALL_TOKENS_KEY, false)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return LimitTokenPositionFilter(input, maxTokenPosition, consumeAllTokens)
    }

    companion object {
        /** SPI name */
        const val NAME = "limitTokenPosition"
        const val MAX_TOKEN_POSITION_KEY = "maxTokenPosition"
        const val CONSUME_ALL_TOKENS_KEY = "consumeAllTokens"
    }
}
