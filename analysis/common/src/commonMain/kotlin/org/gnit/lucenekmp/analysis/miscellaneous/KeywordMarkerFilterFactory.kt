package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware
import kotlin.properties.Delegates

/**
 * Factory for [KeywordMarkerFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_keyword" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.KeywordMarkerFilterFactory" protected="protectedkeyword.txt" pattern="^.+er$" ignoreCase="false"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1.0
 * @lucene.spi {@value #NAME}
 */
class KeywordMarkerFilterFactory : TokenFilterFactory, ResourceLoaderAware {
    private var wordFiles: String? = null
    private var stringPattern: String? = null
    private var ignoreCase: Boolean by Delegates.notNull()
    private var pattern: Regex? = null
    private var protectedWords: CharArraySet? = null

    /** Creates a new KeywordMarkerFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        wordFiles = get(args, PROTECTED_TOKENS)
        stringPattern = get(args, PATTERN)
        ignoreCase = getBoolean(args, "ignoreCase", false)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    @Throws(IOException::class)
    override fun inform(loader: ResourceLoader) {
        val wordFiles = wordFiles
        if (wordFiles != null) {
            protectedWords = getWordSet(loader, wordFiles, ignoreCase)
        }
        val stringPattern = stringPattern
        if (stringPattern != null) {
            pattern =
                if (ignoreCase) {
                    Regex(stringPattern, setOf(RegexOption.IGNORE_CASE))
                } else {
                    Regex(stringPattern)
                }
        }
    }

    fun isIgnoreCase(): Boolean {
        return ignoreCase
    }

    override fun create(input: TokenStream): TokenStream {
        var inputVar = input
        if (pattern != null) {
            inputVar = PatternKeywordMarkerFilter(inputVar, pattern!!)
        }
        if (protectedWords != null) {
            inputVar = SetKeywordMarkerFilter(inputVar, protectedWords!!)
        }
        return inputVar
    }

    companion object {
        /** SPI name */
        const val NAME = "keywordMarker"
        const val PROTECTED_TOKENS = "protected"
        const val PATTERN = "pattern"
    }
}
