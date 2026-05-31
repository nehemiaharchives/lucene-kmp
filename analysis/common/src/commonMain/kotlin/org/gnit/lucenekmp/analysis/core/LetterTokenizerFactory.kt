package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.util.CharTokenizer
import org.gnit.lucenekmp.util.AttributeFactory
import kotlin.properties.Delegates

/**
 * Factory for [LetterTokenizer].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_letter" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.LetterTokenizerFactory" maxTokenLen="256"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * Options:
 *
 * <ul>
 *   <li>maxTokenLen: max token length, must be greater than 0 and less than MAX_TOKEN_LENGTH_LIMIT
 *       (1024*1024). It is rare to need to change this else [CharTokenizer]::DEFAULT_MAX_TOKEN_LEN
 * </ul>
 *
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class LetterTokenizerFactory : TokenizerFactory {
    /** SPI name */
    companion object {
        const val NAME: String = "letter"
    }

    private var maxTokenLen by Delegates.notNull<Int>()

    /** Creates a new LetterTokenizerFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        maxTokenLen = getInt(args, "maxTokenLen", CharTokenizer.DEFAULT_MAX_WORD_LEN)
        require(!(maxTokenLen > StandardTokenizer.MAX_TOKEN_LENGTH_LIMIT || maxTokenLen <= 0)) {
            "maxTokenLen must be greater than 0 and less than ${StandardTokenizer.MAX_TOKEN_LENGTH_LIMIT} passed: $maxTokenLen"
        }
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(factory: AttributeFactory): LetterTokenizer {
        return LetterTokenizer(factory, maxTokenLen)
    }
}
