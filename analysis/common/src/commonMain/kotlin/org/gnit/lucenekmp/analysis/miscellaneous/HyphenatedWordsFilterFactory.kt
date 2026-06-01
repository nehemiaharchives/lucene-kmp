package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [HyphenatedWordsFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_hyphn" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.HyphenatedWordsFilterFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class HyphenatedWordsFilterFactory : TokenFilterFactory {
    /** Creates a new HyphenatedWordsFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): HyphenatedWordsFilter {
        return HyphenatedWordsFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME = "hyphenatedWords"
    }
}
