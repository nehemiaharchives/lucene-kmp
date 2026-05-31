package org.gnit.lucenekmp.analysis.core

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware
import kotlin.properties.Delegates

/**
 * Factory class for [TypeTokenFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="chars" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.StandardTokenizerFactory"/&gt;
 *     &lt;filter class="solr.TypeTokenFilterFactory" types="stoptypes.txt"
 *                   useWhitelist="false"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.6.0
 * @lucene.spi {@value #NAME}
 */
class TypeTokenFilterFactory : TokenFilterFactory, ResourceLoaderAware {
    /** SPI name */
    companion object {
        const val NAME: String = "type"
    }

    private var useWhitelist by Delegates.notNull<Boolean>()
    private var stopTypesFiles: String by Delegates.notNull<String>()
    private var stopTypes: MutableSet<String>? = null

    /** Creates a new TypeTokenFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        stopTypesFiles = require(args, "types")
        useWhitelist = getBoolean(args, "useWhitelist", false)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    @Throws(IOException::class)
    override fun inform(loader: ResourceLoader) {
        val files = splitFileNames(stopTypesFiles)
        if (files.isNotEmpty()) {
            stopTypes = mutableSetOf()
            for (file in files) {
                val typesLines = getLines(loader, file.trim())
                stopTypes!!.addAll(typesLines)
            }
        }
    }

    fun getStopTypes(): Set<String>? {
        return stopTypes
    }

    override fun create(input: TokenStream): TokenStream {
        return TypeTokenFilter(input, stopTypes ?: emptySet(), useWhitelist)
    }
}
