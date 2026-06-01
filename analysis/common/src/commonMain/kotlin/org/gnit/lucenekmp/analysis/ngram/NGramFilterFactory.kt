package org.gnit.lucenekmp.analysis.ngram

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Factory for [NGramTokenFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_ngrm" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.NGramFilterFactory" minGramSize="1" maxGramSize="2" preserveOriginal="true"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 */
class NGramFilterFactory(args: MutableMap<String, String>) : TokenFilterFactory(args) {
    companion object {
        const val NAME = "nGram"
    }

    private val maxGramSize = requireInt(args, "maxGramSize")
    private val minGramSize = requireInt(args, "minGramSize")
    private val preserveOriginal = getBoolean(args, "preserveOriginal", NGramTokenFilter.DEFAULT_PRESERVE_ORIGINAL)

    init {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    constructor() : this(mutableMapOf()) {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenFilter {
        return NGramTokenFilter(input, minGramSize, maxGramSize, preserveOriginal)
    }
}
