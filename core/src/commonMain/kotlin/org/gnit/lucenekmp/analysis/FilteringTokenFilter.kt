package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import okio.IOException

/**
 * Abstract base class for TokenFilters that may remove tokens. You have to implement [ ][.accept] and return a boolean if the current token should be preserved. [.incrementToken]
 * uses this method to decide if a token should be passed to the caller.
 */
abstract class FilteringTokenFilter
/**
 * Create a new [FilteringTokenFilter].
 *
 * @param in the [TokenStream] to consume
 */
    (`in`: TokenStream?) : TokenFilter(`in`!!) {
    private val posIncrAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private var skippedPositions = 0

    /**
     * Override this method and return if the current input token should be returned by [ ][.incrementToken].
     */
    @Throws(IOException::class)
    protected abstract fun accept(): Boolean

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        skippedPositions = 0
        while (input.incrementToken()) {
            if (accept()) {
                if (skippedPositions != 0) {
                    posIncrAtt.setPositionIncrement(posIncrAtt.getPositionIncrement() + skippedPositions)
                }
                return true
            }
            skippedPositions += posIncrAtt.getPositionIncrement()
        }

        // reached EOS -- return false
        return false
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        skippedPositions = 0
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        posIncrAtt.setPositionIncrement(posIncrAtt.getPositionIncrement() + skippedPositions)
    }
}
