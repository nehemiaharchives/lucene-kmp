package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for [CodepointCountFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_lngth" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.CodepointCountFilterFactory" min="0" max="1" /&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 4.5.1
 * @lucene.spi {@value #NAME}
 */
class CodepointCountFilterFactory : TokenFilterFactory {
    var min: Int by Delegates.notNull()
    var max: Int by Delegates.notNull()

    /** Creates a new CodepointCountFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        min = requireInt(args, MIN_KEY)
        max = requireInt(args, MAX_KEY)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): CodepointCountFilter {
        return CodepointCountFilter(input, min, max)
    }

    companion object {
        /** SPI name */
        const val NAME = "codepointCount"
        const val MIN_KEY = "min"
        const val MAX_KEY = "max"
    }
}
