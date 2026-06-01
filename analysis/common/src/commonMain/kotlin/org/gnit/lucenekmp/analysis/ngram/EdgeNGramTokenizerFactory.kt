package org.gnit.lucenekmp.analysis.ngram

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * Creates new instances of [EdgeNGramTokenizer].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_edgngrm" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.EdgeNGramTokenizerFactory" minGramSize="1" maxGramSize="1"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 */
class EdgeNGramTokenizerFactory(args: MutableMap<String, String>) : TokenizerFactory(args) {
    companion object {
        const val NAME = "edgeNGram"
    }

    private val maxGramSize = getInt(args, "maxGramSize", EdgeNGramTokenizer.DEFAULT_MAX_GRAM_SIZE)
    private val minGramSize = getInt(args, "minGramSize", EdgeNGramTokenizer.DEFAULT_MIN_GRAM_SIZE)

    init {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    constructor() : this(mutableMapOf()) {
        throw defaultCtorException()
    }

    override fun create(factory: AttributeFactory): Tokenizer {
        return EdgeNGramTokenizer(factory, minGramSize, maxGramSize)
    }
}
