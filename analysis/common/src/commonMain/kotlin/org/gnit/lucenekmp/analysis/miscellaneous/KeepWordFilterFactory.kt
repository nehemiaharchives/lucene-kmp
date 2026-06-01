package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.en.AbstractWordsFileFilterFactory

/**
 * Factory for [KeepWordFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_keepword" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.KeepWordFilterFactory" words="keepwords.txt" ignoreCase="false"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class KeepWordFilterFactory : AbstractWordsFileFilterFactory {
    /** Creates a new KeepWordFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args)

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun createDefaultWords(): CharArraySet? {
        return null
    }

    override fun create(input: TokenStream): TokenStream {
        return if (getWords() == null) {
            input
        } else {
            val filter: TokenStream = KeepWordFilter(input, getWords()!!)
            filter
        }
    }

    companion object {
        /** SPI name */
        const val NAME = "keepWord"
    }
}
