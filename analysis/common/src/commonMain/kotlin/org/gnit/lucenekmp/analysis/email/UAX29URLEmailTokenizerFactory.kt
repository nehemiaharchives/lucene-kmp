package org.gnit.lucenekmp.analysis.email

import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.util.AttributeFactory
import kotlin.properties.Delegates

/**
 * Factory for [UAX29URLEmailTokenizer].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_urlemail" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.UAX29URLEmailTokenizerFactory" maxTokenLength="255"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class UAX29URLEmailTokenizerFactory : TokenizerFactory {
    private var maxTokenLength by Delegates.notNull<Int>()

    /** Creates a new UAX29URLEmailTokenizerFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        maxTokenLength = getInt(args, "maxTokenLength", StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(factory: AttributeFactory): UAX29URLEmailTokenizer {
        val tokenizer = UAX29URLEmailTokenizer(factory)
        tokenizer.setMaxTokenLength(maxTokenLength)
        return tokenizer
    }

    companion object {
        /** SPI name */
        const val NAME: String = "uax29UrlEmail"
    }
}
