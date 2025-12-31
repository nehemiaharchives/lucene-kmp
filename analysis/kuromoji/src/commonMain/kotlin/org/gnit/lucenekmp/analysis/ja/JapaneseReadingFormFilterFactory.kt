package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for {@link org.apache.lucene.analysis.ja.JapaneseReadingFormFilter}.
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_ja" class="solr.TextField"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.JapaneseTokenizerFactory"/&gt;
 *     &lt;filter class="solr.JapaneseReadingFormFilterFactory"
 *             useRomaji="false"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;
 * </pre>
 *
 * @since 3.6.0
 * @lucene.spi {@value #NAME}
 */
class JapaneseReadingFormFilterFactory : TokenFilterFactory {
    private var useRomaji by Delegates.notNull<Boolean>()

    /** Creates a new JapaneseReadingFormFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        useRomaji = getBoolean(args, ROMAJI_PARAM, false)
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return JapaneseReadingFormFilter(input, useRomaji)
    }

    companion object {
        const val NAME: String = "japaneseReadingForm"
        private const val ROMAJI_PARAM: String = "useRomaji"
    }
}
