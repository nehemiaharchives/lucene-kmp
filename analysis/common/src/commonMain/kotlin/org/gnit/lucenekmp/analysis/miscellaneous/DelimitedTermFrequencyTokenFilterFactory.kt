package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for [DelimitedTermFrequencyTokenFilter]. The field must have `omitPositions=true`.
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_tfdl" class="solr.TextField" omitPositions="true"&gt;
 * &lt;analyzer&gt;
 * &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 * &lt;filter class="solr.DelimitedTermFrequencyTokenFilterFactory" delimiter="|"/&gt;
 * &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 7.0.0
 * @lucene.spi {@value #NAME}
 */
class DelimitedTermFrequencyTokenFilterFactory : TokenFilterFactory {
    private var delimiter by Delegates.notNull<Char>()

    /** Creates a new DelimitedPayloadTokenFilterFactory  */
    constructor(args: MutableMap<String, String>) : super(args) {
        delimiter =
            getChar(args, DELIMITER_ATTR, DelimitedTermFrequencyTokenFilter.DEFAULT_DELIMITER)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI  */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): DelimitedTermFrequencyTokenFilter {
        return DelimitedTermFrequencyTokenFilter(input, delimiter)
    }

    companion object {
        /** SPI name  */
        const val NAME: String = "delimitedTermFrequency"

        const val DELIMITER_ATTR: String = "delimiter"
    }
}
