package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [TrimFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_trm" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.NGramTokenizerFactory"/&gt;
 *     &lt;filter class="solr.TrimFilterFactory" /&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @see TrimFilter
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class TrimFilterFactory : TokenFilterFactory {
    /** Creates a new TrimFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return TrimFilter(input)
    }

    override fun normalize(input: TokenStream): TokenStream {
        return create(input)
    }

    companion object {
        /** SPI name */
        const val NAME = "trim"
    }
}
