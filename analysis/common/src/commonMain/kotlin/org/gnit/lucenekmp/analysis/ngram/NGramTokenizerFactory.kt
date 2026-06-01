package org.gnit.lucenekmp.analysis.ngram

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.util.AttributeFactory

/**
 * Factory for [NGramTokenizer].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_ngrm" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.NGramTokenizerFactory" minGramSize="1" maxGramSize="2"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 */
class NGramTokenizerFactory(args: MutableMap<String, String>) : TokenizerFactory(args) {
    companion object {
        const val NAME = "nGram"
    }

    private val maxGramSize = getInt(args, "maxGramSize", NGramTokenizer.DEFAULT_MAX_NGRAM_SIZE)
    private val minGramSize = getInt(args, "minGramSize", NGramTokenizer.DEFAULT_MIN_NGRAM_SIZE)

    init {
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    constructor() : this(mutableMapOf()) {
        throw defaultCtorException()
    }

    override fun create(factory: AttributeFactory): Tokenizer {
        return NGramTokenizer(factory, minGramSize, maxGramSize)
    }
}
