package org.gnit.lucenekmp.analysis.pattern

import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.util.AttributeFactory
import kotlin.properties.Delegates

/**
 * Factory for [PatternTokenizer]. This tokenizer uses regex pattern matching to construct
 * distinct tokens for the input stream. It takes two arguments: "pattern" and "group". <br></br>
 *
 * <ul>
 *   <li>"pattern" is the regular expression.
 *   <li>"group" says which group to extract into tokens.
 * </ul>
 *
 * <p>group=-1 (the default) is equivalent to "split". In this case, the tokens will be equivalent
 * to the output from (without empty tokens): [String.split]
 *
 * <p>Using group >= 0 selects the matching group as the token. For example, if you have:<br></br>
 *
 * <pre>
 *  pattern = \'([^\']+)\'
 *  group = 0
 *  input = aaa 'bbb' 'ccc'
 * </pre>
 *
 * the output will be two tokens: 'bbb' and 'ccc' (including the ' marks). With the same input but
 * using group=1, the output would be: bbb and ccc (no ' marks)
 *
 * <p>NOTE: This Tokenizer does not output tokens that are of zero length.
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_ptn" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.PatternTokenizerFactory" pattern="\'([^\']+)\'" group="1"/&gt;
 *   &lt;/analyzer&gt;</pre>
 *
 * @see PatternTokenizer
 * @since solr1.2
 * @lucene.spi {@value #NAME}
 */
open class PatternTokenizerFactory : TokenizerFactory {
    companion object {
        /** SPI name */
        const val NAME = "pattern"

        const val PATTERN = "pattern"
        const val GROUP = "group"
    }

    protected lateinit var pattern: Regex
    protected var group by Delegates.notNull<Int>()

    /** Creates a new PatternTokenizerFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        pattern = getPattern(args, PATTERN)
        group = getInt(args, GROUP, -1)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    /** Split the input using configured pattern */
    override fun create(factory: AttributeFactory): PatternTokenizer {
        return PatternTokenizer(factory, pattern, group)
    }
}
