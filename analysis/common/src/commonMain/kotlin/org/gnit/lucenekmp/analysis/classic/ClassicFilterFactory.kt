package org.gnit.lucenekmp.analysis.classic

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for {@link ClassicFilter}.
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_clssc" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.ClassicTokenizerFactory"/&gt;
 *     &lt;filter class="solr.ClassicFilterFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1.0
 * @lucene.spi {@value #NAME}
 */
class ClassicFilterFactory : TokenFilterFactory {

    /** Default ctor for compatibility with SPI */
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenFilter = ClassicFilter(input)

    companion object {
        /** SPI name */
        const val NAME: String = "classic"
    }
}
