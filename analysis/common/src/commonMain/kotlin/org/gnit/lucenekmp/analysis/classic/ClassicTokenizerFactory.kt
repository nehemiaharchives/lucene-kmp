package org.gnit.lucenekmp.analysis.classic

import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.util.AttributeFactory
import kotlin.properties.Delegates

/**
 * Factory for {@link ClassicTokenizer}.
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_clssc" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.ClassicTokenizerFactory" maxTokenLength="120"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class ClassicTokenizerFactory : TokenizerFactory {
    private var maxTokenLength by Delegates.notNull<Int>()

    /** Creates a new ClassicTokenizerFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        maxTokenLength = getInt(args, "maxTokenLength", StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(factory: AttributeFactory): ClassicTokenizer {
        val tokenizer = ClassicTokenizer(factory)
        tokenizer.setMaxTokenLength(maxTokenLength)
        return tokenizer
    }

    companion object {
        /** SPI name */
        const val NAME: String = "classic"
    }
}
