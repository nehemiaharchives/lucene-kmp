package org.gnit.lucenekmp.analysis.ngram

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Creates new instances of [EdgeNGramTokenFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_edgngrm" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.EdgeNGramFilterFactory" minGramSize="1" maxGramSize="2" preserveOriginal="true"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 */
class EdgeNGramFilterFactory(args: MutableMap<String, String>) : TokenFilterFactory(args) {
    companion object {
        const val NAME = "edgeNGram"
    }

    private val maxGramSize = requireInt(args, "maxGramSize")
    private val minGramSize = requireInt(args, "minGramSize")
    private val preserveOriginal = getBoolean(args, "preserveOriginal", EdgeNGramTokenFilter.DEFAULT_PRESERVE_ORIGINAL)

    init {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    constructor() : this(mutableMapOf()) {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenFilter {
        return EdgeNGramTokenFilter(input, minGramSize, maxGramSize, preserveOriginal)
    }
}
