package org.gnit.lucenekmp.analysis.util

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.fr.FrenchAnalyzer
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware

/**
 * Factory for [ElisionFilter].
 *
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class ElisionFilterFactory : TokenFilterFactory, ResourceLoaderAware {
    private var articlesFile: String = ""
    private var ignoreCase: Boolean = false
    private var articles: CharArraySet? = null

    /** Creates a new ElisionFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        articlesFile = get(args, "articles", "")
        ignoreCase = getBoolean(args, "ignoreCase", false)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }


    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    @Throws(IOException::class)
    override fun inform(loader: ResourceLoader) {
        articles = if (articlesFile.isEmpty()) {
            FrenchAnalyzer.DEFAULT_ARTICLES
        } else {
            getWordSet(loader, articlesFile, ignoreCase)
        }
    }

    override fun create(input: TokenStream): TokenStream {
        val effectiveArticles = requireNotNull(articles) { "ElisionFilterFactory was not initialized" }
        return ElisionFilter(input, effectiveArticles)
    }

    override fun normalize(input: TokenStream): TokenStream {
        return create(input)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "elision"
    }
}
