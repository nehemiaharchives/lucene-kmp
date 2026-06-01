package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Provides a filter that will drop tokens matching a set of flags. This might be used if you had
 * both custom filters that identify tokens to be removed, but need to run before other filters that
 * want to see the token that will eventually be dropped. Alternately you might have separate flag
 * setting filters and then remove tokens that match a particular combination of those filters.<br>
 * <br>
 * In Solr this might be configured such as
 *
 * <pre class="prettyprint">
 *     &lt;analyzer type="index"&gt;
 *       &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *       &lt;-- other filters --&gt;
 *       &lt;filter class="solr.DropIfFlaggedFilterFactory" dropFlags="9"/&gt;
 *     &lt;/analyzer&gt;
 * </pre>
 *
 * The above would drop any token that had the first and fourth bit set.
 *
 * @since 8.8.0
 * @lucene.spi {@value #NAME}
 */
class DropIfFlaggedFilterFactory : TokenFilterFactory {
    private var dropFlags: Int by Delegates.notNull()

    /** Initialize this factory via a set of key-value pairs. */
    constructor(args: MutableMap<String, String>) : super(args) {
        dropFlags = getInt(args, "dropFlags", 2)
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return DropIfFlaggedFilter(input, dropFlags)
    }

    companion object {
        /** SPI name */
        const val NAME = "dropIfFlagged"
    }
}
