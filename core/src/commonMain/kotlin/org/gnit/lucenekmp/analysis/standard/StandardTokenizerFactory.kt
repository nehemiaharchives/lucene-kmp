package org.gnit.lucenekmp.analysis.standard

import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.util.AttributeFactory
import kotlin.properties.Delegates

/**
 * Factory for [StandardTokenizer].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_stndrd" class="solr.TextField" positionIncrementGap="100"&gt;
 * &lt;analyzer&gt;
 * &lt;tokenizer class="solr.StandardTokenizerFactory" maxTokenLength="255"/&gt;
 * &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class StandardTokenizerFactory : TokenizerFactory {
    var maxTokenLength by Delegates.notNull<Int>()

    /** Creates a new StandardTokenizerFactory  */
    constructor(args: MutableMap<String, String>) : super(args) {
        maxTokenLength = getInt(
            args,
            "maxTokenLength",
            StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH
        )
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI  */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(factory: AttributeFactory): StandardTokenizer {
        val tokenizer = StandardTokenizer(factory)
        tokenizer.setMaxTokenLength(maxTokenLength)
        return tokenizer
    }

    companion object {
        /** SPI name  */
        const val NAME: String = "standard"
    }
}
