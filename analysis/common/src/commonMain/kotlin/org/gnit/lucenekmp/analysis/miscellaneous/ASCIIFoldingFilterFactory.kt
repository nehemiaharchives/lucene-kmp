package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for [ASCIIFoldingFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_ascii" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.ASCIIFoldingFilterFactory" preserveOriginal="false"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1
 * @lucene.spi [NAME]
 */
class ASCIIFoldingFilterFactory : TokenFilterFactory {
    companion object {
        /** SPI name */
        const val NAME = "asciiFolding"

        private const val PRESERVE_ORIGINAL = "preserveOriginal"
    }

    private var preserveOriginal: Boolean by Delegates.notNull()

    /** Creates a new ASCIIFoldingFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        preserveOriginal = getBoolean(args, PRESERVE_ORIGINAL, false)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() : super() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return ASCIIFoldingFilter(input, preserveOriginal)
    }

    override fun normalize(input: TokenStream): TokenStream {
        // The main use-case for using preserveOriginal is to match regardless of
        // case and to give better scores to exact matches. Since most multi-term
        // queries return constant scores anyway, for normalization we
        // emit only the folded token
        return ASCIIFoldingFilter(input, false)
    }
}
