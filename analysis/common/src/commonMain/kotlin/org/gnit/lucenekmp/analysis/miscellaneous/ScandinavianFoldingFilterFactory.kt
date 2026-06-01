package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [ScandinavianFoldingFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_scandfold" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.ScandinavianFoldingFilterFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 4.4.0
 * @lucene.spi {@value #NAME}
 */
class ScandinavianFoldingFilterFactory : TokenFilterFactory {
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return ScandinavianFoldingFilter(input)
    }

    override fun normalize(input: TokenStream): TokenStream {
        return create(input)
    }

    companion object {
        /** SPI name */
        const val NAME = "scandinavianFolding"
    }
}
