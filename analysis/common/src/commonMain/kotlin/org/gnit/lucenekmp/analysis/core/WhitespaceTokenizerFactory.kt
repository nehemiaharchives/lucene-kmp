package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.util.CharTokenizer
import org.gnit.lucenekmp.util.AttributeFactory
import kotlin.properties.Delegates

/**
 * Factory for [WhitespaceTokenizer].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_ws" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory" rule="unicode"  maxTokenLen="256"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * Options:
 *
 * <ul>
 *   <li>rule: either "java" for [WhitespaceTokenizer] or "unicode" for [UnicodeWhitespaceTokenizer]
 *   <li>maxTokenLen: max token length, should be greater than 0 and less than
 *       MAX_TOKEN_LENGTH_LIMIT (1024*1024). It is rare to need to change this else [
 *       CharTokenizer]::DEFAULT_MAX_TOKEN_LEN
 * </ul>
 *
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class WhitespaceTokenizerFactory : TokenizerFactory {
    /** SPI name */
    companion object {
        const val NAME: String = "whitespace"

        const val RULE_JAVA: String = "java"
        const val RULE_UNICODE: String = "unicode"
        private val RULE_NAMES: MutableCollection<String> = mutableListOf(RULE_JAVA, RULE_UNICODE)
    }

    private var rule: String by Delegates.notNull<String>()
    private var maxTokenLen by Delegates.notNull<Int>()

    /** Creates a new WhitespaceTokenizerFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        rule = get(args, "rule", RULE_NAMES, RULE_JAVA)!!
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

    override fun create(factory: AttributeFactory): Tokenizer {
        return when (rule) {
            RULE_JAVA -> WhitespaceTokenizer(factory, maxTokenLen)
            RULE_UNICODE -> UnicodeWhitespaceTokenizer(factory, maxTokenLen)
            else -> throw AssertionError()
        }
    }
}
