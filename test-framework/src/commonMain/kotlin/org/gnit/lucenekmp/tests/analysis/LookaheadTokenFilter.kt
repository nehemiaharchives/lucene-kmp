package org.gnit.lucenekmp.tests.analysis

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.util.RollingBuffer

/**
 * Simplified Kotlin port of Lucene's LookaheadTokenFilter.
 */
abstract class LookaheadTokenFilter<T : LookaheadTokenFilter.Position>(input: TokenStream) : TokenFilter(input) {
    protected val posIncAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    protected val posLenAtt: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)
    protected val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

    protected var inputPos = -1
    protected var outputPos = 0
    protected var end = false
    private var tokenPending = false
    private var insertPending = false

    open class Position : RollingBuffer.Resettable {
        val inputTokens = mutableListOf<org.gnit.lucenekmp.util.AttributeSource.State>()
        var nextRead = 0
        var startOffset = -1
        var endOffset = -1
        override fun reset() {
            inputTokens.clear()
            nextRead = 0
            startOffset = -1
            endOffset = -1
        }
        fun add(state: org.gnit.lucenekmp.util.AttributeSource.State) { inputTokens.add(state) }
        fun nextState(): org.gnit.lucenekmp.util.AttributeSource.State { return inputTokens[nextRead++] }
    }

    protected abstract fun newPosition(): T

    protected val positions: RollingBuffer<T> = object : RollingBuffer<T>() {
        override fun newInstance(): T = newPosition()
    }

    protected fun insertToken() {
        if (tokenPending) {
            positions.get(inputPos).add(captureState()!!)
            tokenPending = false
        }
        insertPending = true
    }

    protected open fun afterPosition() {}

    protected fun peekToken(): Boolean {
        if (tokenPending) {
            positions.get(inputPos).add(captureState()!!)
            tokenPending = false
        }
        val gotToken = input.incrementToken()
        if (gotToken) {
            inputPos += posIncAtt.getPositionIncrement()
            val startPosData = positions.get(inputPos)
            val endPosData = positions.get(inputPos + posLenAtt.positionLength)
            val startOffset = offsetAtt.startOffset()
            if (startPosData.startOffset == -1) {
                startPosData.startOffset = startOffset
            }
            val endOffset = offsetAtt.endOffset()
            if (endPosData.endOffset == -1) {
                endPosData.endOffset = endOffset
            }
            tokenPending = true
        } else {
            end = true
        }
        return gotToken
    }

    protected fun nextToken(): Boolean {
        var posData = positions.get(outputPos)
        while (true) {
            if (posData.nextRead < posData.inputTokens.size) {
                if (tokenPending) {
                    positions.get(inputPos).add(captureState()!!)
                    tokenPending = false
                }
                restoreState(positions.get(outputPos).nextState())
                return true
            }
            if (inputPos == -1 || outputPos == inputPos) {
                if (tokenPending) {
                    tokenPending = false
                    return true
                } else if (end || !peekToken()) {
                    afterPosition()
                    if (insertPending) {
                        insertPending = false
                        return true
                    }
                    return false
                }
            } else {
                if (posData.startOffset != -1) {
                    afterPosition()
                    if (insertPending) {
                        insertPending = false
                        return true
                    }
                }
                outputPos++
                positions.freeBefore(outputPos)
                posData = positions.get(outputPos)
            }
        }
    }

    override fun reset() {
        super.reset()
        positions.reset()
        inputPos = -1
        outputPos = 0
        tokenPending = false
        end = false
    }

    override fun incrementToken(): Boolean {
        return nextToken()
    }
}
