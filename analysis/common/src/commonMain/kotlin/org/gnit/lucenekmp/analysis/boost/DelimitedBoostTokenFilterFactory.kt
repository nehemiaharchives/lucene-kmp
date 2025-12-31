package org.gnit.lucenekmp.analysis.boost

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates


/**
 * Factory for [DelimitedBoostTokenFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_dlmtd" class="solr.TextField" positionIncrementGap="100"&gt;
 * &lt;analyzer&gt;
 * &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 * &lt;filter class="solr.DelimitedBoostTokenFilterFactory" delimiter="|"/&gt;
 * &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @lucene.spi {@value #NAME}
 */
class DelimitedBoostTokenFilterFactory : TokenFilterFactory {
    private var delimiter by Delegates.notNull<Char>()

    /** Creates a new DelimitedPayloadTokenFilterFactory  */
    constructor(args: MutableMap<String, String>) : super(args) {
        delimiter = getChar(args, DELIMITER_ATTR, DEFAULT_DELIMITER)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI  */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): DelimitedBoostTokenFilter {
        return DelimitedBoostTokenFilter(input, delimiter)
    }

    companion object {
        /** SPI name  */
        const val NAME: String = "delimitedBoost"

        const val DELIMITER_ATTR: String = "delimiter"
        const val DEFAULT_DELIMITER: Char = '|'
    }
}
