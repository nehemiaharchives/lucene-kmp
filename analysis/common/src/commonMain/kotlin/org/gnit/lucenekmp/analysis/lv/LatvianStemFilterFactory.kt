package org.gnit.lucenekmp.analysis.lv

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [LatvianStemFilter].
 *
 * ```
 * <fieldType name="text_lvstem" class="solr.TextField" positionIncrementGap="100">
 *   <analyzer>
 *     <tokenizer class="solr.StandardTokenizerFactory"/>
 *     <filter class="solr.LowerCaseFilterFactory"/>
 *     <filter class="solr.LatvianStemFilterFactory"/>
 *   </analyzer>
 * </fieldType>
 * ```
 *
 * @since 3.2.0
 */
class LatvianStemFilterFactory : TokenFilterFactory {

    /** Creates a new LatvianStemFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return LatvianStemFilter(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "latvianStem"
    }
}
