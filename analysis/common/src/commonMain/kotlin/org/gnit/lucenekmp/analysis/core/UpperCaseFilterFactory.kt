package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [UpperCaseFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_uppercase" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.UpperCaseFilterFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * <p><b>NOTE:</b> In Unicode, this transformation may lose information when the upper case
 * character represents more than one lower case character. Use this filter when you require
 * uppercase tokens. Use the [LowerCaseFilterFactory] for general search matching
 *
 * @since 4.7.0
 * @lucene.spi {@value #NAME}
 */
class UpperCaseFilterFactory : TokenFilterFactory {
    /** SPI name */
    companion object {
        const val NAME: String = "uppercase"
    }

    /** Creates a new UpperCaseFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return UpperCaseFilter(input)
    }

    override fun normalize(input: TokenStream): TokenStream {
        return create(input)
    }
}

