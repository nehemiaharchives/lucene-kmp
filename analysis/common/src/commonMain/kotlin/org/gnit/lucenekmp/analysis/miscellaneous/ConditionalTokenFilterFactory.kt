package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware

/**
 * Abstract parent class for analysis factories that create [ConditionalTokenFilter] instances
 *
 * @since 7.4.0
 */
abstract class ConditionalTokenFilterFactory protected constructor(args: MutableMap<String, String>) : TokenFilterFactory(args), ResourceLoaderAware {
    private var innerFilters: MutableList<TokenFilterFactory>? = null

    /** Default ctor for compatibility with SPI */
    constructor() : this(mutableMapOf()) {
        throw defaultCtorException()
    }

    /**
     * Set the inner filter factories to produce the [TokenFilter]s that will be wrapped by the
     * [ConditionalTokenFilter]
     */
    fun setInnerFilters(innerFilters: MutableList<TokenFilterFactory>) {
        this.innerFilters = innerFilters
    }

    override fun create(input: TokenStream): TokenStream {
        if (innerFilters == null || innerFilters!!.size == 0) {
            return input
        }
        val innerStream: (TokenStream) -> TokenStream = { ts ->
            var current = ts
            for (factory in innerFilters!!) {
                current = factory.create(current)
            }
            current
        }
        return create(input, innerStream)
    }

    @Throws(IOException::class)
    final override fun inform(loader: ResourceLoader) {
        if (innerFilters == null) {
            return
        }
        for (factory in innerFilters!!) {
            if (factory is ResourceLoaderAware) {
                factory.inform(loader)
            }
        }
        doInform(loader)
    }

    /** Initialises this component with the corresponding [ResourceLoader] */
    @Throws(IOException::class)
    protected open fun doInform(loader: ResourceLoader) {}

    /** Modify the incoming [TokenStream] with a [ConditionalTokenFilter] */
    protected abstract fun create(input: TokenStream, inner: (TokenStream) -> TokenStream): ConditionalTokenFilter
}
