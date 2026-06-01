package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.miscellaneous.StemmerOverrideFilter.StemmerOverrideMap
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware

/**
 * Factory for [StemmerOverrideFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_dicstem" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.StemmerOverrideFilterFactory" dictionary="dictionary.txt" ignoreCase="false"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1.0
 * @lucene.spi {@value #NAME}
 */
class StemmerOverrideFilterFactory : TokenFilterFactory, ResourceLoaderAware {
    /** SPI name */
    companion object {
        const val NAME = "stemmerOverride"
    }

    private var dictionary: StemmerOverrideMap? = null
    private var dictionaryFiles: String? = null
    private var ignoreCase: Boolean = false

    /** Creates a new StemmerOverrideFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        dictionaryFiles = get(args, "dictionary")
        ignoreCase = getBoolean(args, "ignoreCase", false)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    @Throws(IOException::class)
    override fun inform(loader: ResourceLoader) {
        if (dictionaryFiles != null) {
            val files = splitFileNames(dictionaryFiles!!)
            if (files.size > 0) {
                val builder = StemmerOverrideFilter.Builder(ignoreCase)
                for (file in files) {
                    val list = getLines(loader, file.trim())
                    for (line in list) {
                        val mapping = line.split("\t", limit = 2)
                        builder.add(mapping[0], mapping[1])
                    }
                }
                dictionary = builder.build()
            }
        }
    }

    fun isIgnoreCase(): Boolean {
        return ignoreCase
    }

    override fun create(input: TokenStream): TokenStream {
        return if (dictionary == null) input else StemmerOverrideFilter(input, dictionary!!)
    }
}
