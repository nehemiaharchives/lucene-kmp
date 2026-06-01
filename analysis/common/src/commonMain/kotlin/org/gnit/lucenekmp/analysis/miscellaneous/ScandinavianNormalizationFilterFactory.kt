package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [ScandinavianNormalizationFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_scandnorm" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.ScandinavianNormalizationFilterFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 4.4.0
 * @lucene.spi {@value #NAME}
 */
class ScandinavianNormalizationFilterFactory : TokenFilterFactory {
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): ScandinavianNormalizationFilter {
        return ScandinavianNormalizationFilter(input)
    }

    override fun normalize(input: TokenStream): TokenStream {
        return create(input)
    }

    companion object {
        /** SPI name */
        const val NAME = "scandinavianNormalization"
    }
}
