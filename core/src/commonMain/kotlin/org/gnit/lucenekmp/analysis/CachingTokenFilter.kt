package org.gnit.lucenekmp.analysis

import okio.IOException

/**
 * This class can be used if the token attributes of a TokenStream are intended to be consumed more
 * than once. It caches all token attribute states locally in a List when the first call to [ ][.incrementToken] is called. Subsequent calls will used the cache.
 *
 *
 * *Important:* Like any proper TokenFilter, [.reset] propagates to the input,
 * although only before [.incrementToken] is called the first time. Prior to Lucene 5, it
 * was never propagated.
 */
class CachingTokenFilter
/**
 * Create a new CachingTokenFilter around `input`. As with any normal TokenFilter, do
 * *not* call reset on the input; this filter will do it normally.
 */
    (input: TokenStream) : TokenFilter(input) {
    private var cache: MutableList<State>? = null
    private var iterator: MutableIterator<State>? = null
    private var finalState: State? = null

    /**
     * Propagates reset if incrementToken has not yet been called. Otherwise it rewinds the iterator
     * to the beginning of the cached list.
     */
    @Throws(IOException::class)
    override fun reset() {
        if (cache == null) { // first time
            input.reset()
        } else {
            iterator = cache!!.iterator()
        }
    }

    /** The first time called, it'll read and cache all tokens from the input.  */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (cache == null) { // first-time
            // fill cache lazily
            cache = ArrayList<State>(64)
            fillCache()
            iterator = cache!!.iterator()
        }

        if (!iterator!!.hasNext()) {
            // the cache is exhausted, return false
            return false
        }
        // Since the TokenFilter can be reset, the tokens need to be preserved as immutable.
        restoreState(iterator!!.next())
        return true
    }

    override fun end() {
        if (finalState != null) {
            restoreState(finalState)
        }
    }

    @Throws(IOException::class)
    private fun fillCache() {
        while (input.incrementToken()) {
            cache!!.add(captureState()!!)
        }
        // capture final state
        input.end()
        finalState = captureState()
    }

    val isCached: Boolean
        /** If the underlying token stream was consumed and cached.  */
        get() = cache != null
}
