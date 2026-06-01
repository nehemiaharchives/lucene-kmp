package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for [LengthFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_lngth" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.LengthFilterFactory" min="0" max="1" /&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class LengthFilterFactory : TokenFilterFactory {
    var min: Int by Delegates.notNull()
    var max: Int by Delegates.notNull()

    /** Creates a new LengthFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        min = requireInt(args, MIN_KEY)
        max = requireInt(args, MAX_KEY)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): LengthFilter {
        val filter = LengthFilter(input, min, max)
        return filter
    }

    companion object {
        /** SPI name */
        const val NAME = "length"
        const val MIN_KEY = "min"
        const val MAX_KEY = "max"
    }
}
