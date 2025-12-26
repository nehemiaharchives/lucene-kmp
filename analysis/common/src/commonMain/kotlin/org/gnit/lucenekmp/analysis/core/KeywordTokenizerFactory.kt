package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.AbstractAnalysisFactory
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.util.AttributeFactory
import kotlin.properties.Delegates

/**
 * Factory for [KeywordTokenizer].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_keyword" class="solr.TextField" positionIncrementGap="100"&gt;
 * &lt;analyzer&gt;
 * &lt;tokenizer class="solr.KeywordTokenizerFactory" maxTokenLen="256"/&gt;
 * &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * Options:
 *
 *
 *  * maxTokenLen: max token length, should be greater than 0 and less than
 * MAX_TOKEN_LENGTH_LIMIT (1024*1024). It is rare to need to change this else [       ]::DEFAULT_BUFFER_SIZE
 *
 *
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class KeywordTokenizerFactory : TokenizerFactory {
    private var maxTokenLen by Delegates.notNull<Int>()

    /** Creates a new KeywordTokenizerFactory  */
    constructor(args: MutableMap<String, String>) : super(args) {
        maxTokenLen = getInt(
            args,
            "maxTokenLen",
            KeywordTokenizer.DEFAULT_BUFFER_SIZE
        )
        require(!(maxTokenLen > StandardTokenizer.MAX_TOKEN_LENGTH_LIMIT || maxTokenLen <= 0)) {
            ("maxTokenLen must be greater than 0 and less than "
                    + StandardTokenizer.MAX_TOKEN_LENGTH_LIMIT
                    + " passed: "
                    + maxTokenLen)
        }
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI  */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(factory: AttributeFactory): KeywordTokenizer {
        return KeywordTokenizer(factory, maxTokenLen)
    }

    companion object {
        /** SPI name  */
        const val NAME: String = "keyword"
    }
}
