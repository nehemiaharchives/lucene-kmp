package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware

/**
 * Factory for {@link org.apache.lucene.analysis.ja.JapanesePartOfSpeechStopFilter}.
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_ja" class="solr.TextField"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.JapaneseTokenizerFactory"/&gt;
 *     &lt;filter class="solr.JapanesePartOfSpeechStopFilterFactory"
 *             tags="stopTags.txt"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;
 * </pre>
 *
 * @since 3.6.0
 * @lucene.spi {@value #NAME}
 */
class JapanesePartOfSpeechStopFilterFactory : TokenFilterFactory, ResourceLoaderAware {

    private var stopTagFiles: String? = null
    private var stopTags: Set<String>? = null

    /** Creates a new JapanesePartOfSpeechStopFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        stopTagFiles = get(args, TAGS_PARAM)
        stopTags = if (stopTagFiles == null) JapaneseAnalyzer.getDefaultStopTags() else null
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    @Throws(IOException::class)
    override fun inform(loader: ResourceLoader) {
        if (stopTagFiles != null) {
            stopTags = null
            val cas: CharArraySet? = getWordSet(loader, stopTagFiles!!, false)
            if (cas != null) {
                val tags = mutableSetOf<String>()
                for (element in cas) {
                    val chars = element as CharArray
                    tags.add(chars.concatToString())
                }
                stopTags = tags
            }
        }
    }

    override fun create(stream: TokenStream): TokenStream {
        val tags = stopTags
        return if (tags != null) JapanesePartOfSpeechStopFilter(stream, tags) else stream
    }

    companion object {
        const val NAME: String = "japanesePartOfSpeechStop"
        private const val TAGS_PARAM: String = "tags"
    }
}
