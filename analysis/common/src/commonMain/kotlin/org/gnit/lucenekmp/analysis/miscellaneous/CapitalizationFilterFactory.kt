package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import kotlin.properties.Delegates

/**
 * Factory for [CapitalizationFilter].
 *
 * <p>The factory takes parameters:
 *
 * <ul>
 *   <li>"onlyFirstWord" - should each word be capitalized or all of the words?
 *   <li>"keep" - a keep word list. Each word that should be kept separated by whitespace.
 *   <li>"keepIgnoreCase - true or false. If true, the keep list will be considered
 *       case-insensitive.
 *   <li>"forceFirstLetter" - Force the first letter to be capitalized even if it is in the keep
 *       list
 *   <li>"okPrefix" - do not change word capitalization if a word begins with something in this
 *       list. for example if "McK" is on the okPrefix list, the word "McKinley" should not be
 *       changed to "Mckinley"
 *   <li>"minWordLength" - how long the word needs to be to get capitalization applied. If the
 *       minWordLength is 3, "and" &gt; "And" but "or" stays "or"
 *   <li>"maxWordCount" - if the token contains more then maxWordCount words, the capitalization is
 *       assumed to be correct.
 * </ul>
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_cptlztn" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.CapitalizationFilterFactory" onlyFirstWord="true"
 *           keep="java solr lucene" keepIgnoreCase="false"
 *           okPrefix="McK McD McA"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since solr 1.3
 * @lucene.spi {@value #NAME}
 */
class CapitalizationFilterFactory : TokenFilterFactory {
    var keep: CharArraySet? = null

    var okPrefix: Collection<CharArray> = emptyList() // for Example: McK

    var minWordLength: Int by Delegates.notNull() // don't modify capitalization for words shorter then this
    var maxWordCount: Int by Delegates.notNull()
    var maxTokenLength: Int by Delegates.notNull()
    var onlyFirstWord: Boolean by Delegates.notNull()

    // make sure the first letter is capital even if it is in the keep list
    var forceFirstLetter: Boolean by Delegates.notNull()

    /** Creates a new CapitalizationFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        val ignoreCase = getBoolean(args, KEEP_IGNORE_CASE, false)
        var k = getSet(args, KEEP)
        if (k != null) {
            keep = CharArraySet(10, ignoreCase)
            keep!!.addAll(k)
        }

        k = getSet(args, OK_PREFIX)
        if (k != null) {
            okPrefix = mutableListOf()
            for (item in k) {
                (okPrefix as MutableList<CharArray>).add(item.toCharArray())
            }
        }

        minWordLength = getInt(args, MIN_WORD_LENGTH, 0)
        maxWordCount = getInt(args, MAX_WORD_COUNT, CapitalizationFilter.DEFAULT_MAX_WORD_COUNT)
        maxTokenLength = getInt(args, MAX_TOKEN_LENGTH, CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH)
        onlyFirstWord = getBoolean(args, ONLY_FIRST_WORD, true)
        forceFirstLetter = getBoolean(args, FORCE_FIRST_LETTER, true)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): CapitalizationFilter {
        return CapitalizationFilter(
            input,
            onlyFirstWord,
            keep,
            forceFirstLetter,
            okPrefix,
            minWordLength,
            maxWordCount,
            maxTokenLength
        )
    }

    companion object {
        /** SPI name */
        const val NAME = "capitalization"
        const val KEEP = "keep"
        const val KEEP_IGNORE_CASE = "keepIgnoreCase"
        const val OK_PREFIX = "okPrefix"
        const val MIN_WORD_LENGTH = "minWordLength"
        const val MAX_WORD_COUNT = "maxWordCount"
        const val MAX_TOKEN_LENGTH = "maxTokenLength"
        const val ONLY_FIRST_WORD = "onlyFirstWord"
        const val FORCE_FIRST_LETTER = "forceFirstLetter"
    }
}
