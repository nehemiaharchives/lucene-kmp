package org.gnit.lucenekmp.analysis.cjk

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream


/**
 * Factory for [CJKWidthFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_cjk" class="solr.TextField"&gt;
 * &lt;analyzer&gt;
 * &lt;tokenizer class="solr.StandardTokenizerFactory"/&gt;
 * &lt;filter class="solr.CJKWidthFilterFactory"/&gt;
 * &lt;filter class="solr.LowerCaseFilterFactory"/&gt;
 * &lt;filter class="solr.CJKBigramFilterFactory"/&gt;
 * &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.6.0
 * @lucene.spi {@value #NAME}
 */
class CJKWidthFilterFactory : TokenFilterFactory {
    /** Creates a new CJKWidthFilterFactory  */
    constructor(args: MutableMap<String, String>) : super(args) {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI  */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return CJKWidthFilter(input)
    }

    override fun normalize(input: TokenStream): TokenStream {
        return create(input)
    }

    companion object {
        /** SPI name  */
        const val NAME: String = "cjkWidth"
    }
}
