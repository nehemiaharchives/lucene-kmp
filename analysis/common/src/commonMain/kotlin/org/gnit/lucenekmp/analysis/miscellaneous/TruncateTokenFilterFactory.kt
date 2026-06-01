package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for [TruncateTokenFilter]. The following
 * type is recommended for "<i>diacritics-insensitive search</i>" for Turkish.
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_tr_ascii_f5" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.StandardTokenizerFactory"/&gt;
 *     &lt;filter class="solr.ApostropheFilterFactory"/&gt;
 *     &lt;filter class="solr.TurkishLowerCaseFilterFactory"/&gt;
 *     &lt;filter class="solr.ASCIIFoldingFilterFactory" preserveOriginal="true"/&gt;
 *     &lt;filter class="solr.KeywordRepeatFilterFactory"/&gt;
 *     &lt;filter class="solr.TruncateTokenFilterFactory" prefixLength="5"/&gt;
 *     &lt;filter class="solr.RemoveDuplicatesTokenFilterFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 4.8.0
 * @lucene.spi {@value #NAME}
 */
class TruncateTokenFilterFactory : TokenFilterFactory {
    private var prefixLength: Int by Delegates.notNull()

    constructor(args: MutableMap<String, String>) : super(args) {
        prefixLength = get(args, PREFIX_LENGTH_KEY, "5").toInt()
        require(prefixLength >= 1) {
            "$PREFIX_LENGTH_KEY parameter must be a positive number: $prefixLength"
        }
        require(args.isEmpty()) { "Unknown parameter(s): $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return TruncateTokenFilter(input, prefixLength)
    }

    companion object {
        /** SPI name */
        const val NAME = "truncate"
        const val PREFIX_LENGTH_KEY = "prefixLength"
    }
}
