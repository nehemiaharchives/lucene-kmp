package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.AttributeSource

/**
 * Allows skipping TokenFilters based on the current set of attributes.
 *
 * <p>To use, implement the [shouldFilter] method. If it returns `true`, then calling
 * [incrementToken] will use the wrapped TokenFilter(s) to make changes to the tokenstream. If it
 * returns `false`, then the wrapped filter(s) will be skipped.
 */
abstract class ConditionalTokenFilter : TokenFilter {
    private enum class TokenState {
        READING,
        PREBUFFERING,
        DELEGATING
    }

    private inner class OneTimeWrapper(attributeSource: AttributeSource) : TokenStream(attributeSource) {
        private val offsetAtt = attributeSource.addAttribute(OffsetAttribute::class)
        private val posIncAtt = attributeSource.addAttribute(PositionIncrementAttribute::class)

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (state == TokenState.PREBUFFERING) {
                if (posIncAtt.getPositionIncrement() == 0) {
                    adjustPosition = true
                    posIncAtt.setPositionIncrement(1)
                }
                state = TokenState.DELEGATING
                return true
            }
            assert(state == TokenState.DELEGATING)
            if (input.incrementToken()) {
                if (shouldFilter()) {
                    return true
                }
                endOffset = offsetAtt.endOffset()
                bufferedState = captureState()
            } else {
                exhausted = true
            }
            return false
        }

        @Throws(IOException::class)
        override fun reset() {
            // clearing attributes etc is done by the parent stream,
            // so must be avoided here
        }

        @Throws(IOException::class)
        override fun end() {
            // imitate Tokenizer.end() call - endAttributes, set final offset
            if (exhausted) {
                if (endState == null) {
                    input.end()
                    endState = captureState()
                }
                endOffset = offsetAtt.endOffset()
            }
            endAttributes()
            offsetAtt.setOffset(endOffset, endOffset)
        }
    }

    private val delegate: TokenStream
    private var state = TokenState.READING
    private var lastTokenFiltered = false
    private var bufferedState: State? = null
    private var exhausted = false
    private var adjustPosition = false
    private var endState: State? = null
    private var endOffset = 0

    private val posIncAtt = addAttribute(PositionIncrementAttribute::class)

    /**
     * Create a new ConditionalTokenFilter
     *
     * @param input the input TokenStream
     * @param inputFactory a factory function to create the wrapped filter(s)
     */
    protected constructor(input: TokenStream, inputFactory: (TokenStream) -> TokenStream) : super(input) {
        this.delegate = inputFactory(OneTimeWrapper(this.input))
    }

    /** Whether or not to execute the wrapped TokenFilter(s) for the current token */
    @Throws(IOException::class)
    protected abstract fun shouldFilter(): Boolean

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        this.delegate.reset()
        this.state = TokenState.READING
        this.lastTokenFiltered = false
        this.bufferedState = null
        this.exhausted = false
        this.adjustPosition = false
        this.endOffset = -1
        this.endState = null
    }

    @Throws(IOException::class)
    override fun end() {
        if (endState == null) {
            super.end()
            endState = captureState()
        } else {
            restoreState(endState)
        }
        endOffset = getAttribute(OffsetAttribute::class)!!.endOffset()
        if (lastTokenFiltered) {
            this.delegate.end()
            endState = captureState()
        }
    }

    override fun close() {
        super.close()
        this.delegate.close()
    }

    @Throws(IOException::class)
    final override fun incrementToken(): Boolean {
        lastTokenFiltered = false
        while (true) {
            if (state == TokenState.READING) {
                if (bufferedState != null) {
                    restoreState(bufferedState)
                    bufferedState = null
                    lastTokenFiltered = false
                    return true
                }
                if (exhausted) {
                    return false
                }
                if (!input.incrementToken()) {
                    exhausted = true
                    return false
                }
                if (shouldFilter()) {
                    lastTokenFiltered = true
                    state = TokenState.PREBUFFERING
                    // we determine that the delegate has emitted all the tokens it can at the current
                    // position when OneTimeWrapper.incrementToken() is called in DELEGATING state.  To
                    // signal this back to the delegate, we return false, so we now need to reset it
                    // to ensure that it can continue to emit more tokens
                    delegate.reset()
                    val more = delegate.incrementToken()
                    if (more) {
                        state = TokenState.DELEGATING
                        if (adjustPosition) {
                            val posInc = posIncAtt.getPositionIncrement()
                            posIncAtt.setPositionIncrement(posInc - 1)
                        }
                        adjustPosition = false
                    } else {
                        state = TokenState.READING
                        return endDelegating()
                    }
                    return true
                }
                return true
            }
            if (state == TokenState.DELEGATING) {
                lastTokenFiltered = true
                if (delegate.incrementToken()) {
                    return true
                }
                // no more cached tokens
                state = TokenState.READING
                return endDelegating()
            }
        }
    }

    @Throws(IOException::class)
    private fun endDelegating(): Boolean {
        if (bufferedState == null) {
            assert(exhausted)
            return false
        }
        delegate.end()
        val posInc = posIncAtt.getPositionIncrement()
        restoreState(bufferedState)
        // System.out.println("Buffered posInc: " + posIncAtt.getPositionIncrement() + "   Delegated
        // posInc: " + posInc);
        posIncAtt.setPositionIncrement(posIncAtt.getPositionIncrement() + posInc)
        if (adjustPosition) {
            posIncAtt.setPositionIncrement(posIncAtt.getPositionIncrement() - 1)
            adjustPosition = false
        }
        bufferedState = null
        return true
    }
}
