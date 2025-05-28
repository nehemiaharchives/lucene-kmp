package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.util.Unwrappable
import okio.IOException

/**
 * A TokenFilter is a TokenStream whose input is another TokenStream.
 *
 *
 * This is an abstract class; subclasses must override [.incrementToken].
 *
 * @see TokenStream
 */
abstract class TokenFilter
/** Construct a token stream filtering the given input.  */ protected constructor(
    /** The source of tokens for this filter.  */
    protected val input: TokenStream
) : TokenStream(input), Unwrappable<TokenStream> {
    /**
     * {@inheritDoc}
     *
     *
     * **NOTE:** The default implementation chains the call to the input TokenStream, so be sure
     * to call `super.end()` first when overriding this method.
     */
    @Throws(IOException::class)
    override fun end() {
        input.end()
    }

    /**
     * {@inheritDoc}
     *
     *
     * **NOTE:** The default implementation chains the call to the input TokenStream, so be sure
     * to call `super.close()` when overriding this method.
     */
    override fun close() {
        input.close()
    }

    /**
     * {@inheritDoc}
     *
     *
     * **NOTE:** The default implementation chains the call to the input TokenStream, so be sure
     * to call `super.reset()` when overriding this method.
     */
    @Throws(IOException::class)
    override fun reset() {
        input.reset()
    }

    override fun unwrap(): TokenStream {
        return input
    }
}
