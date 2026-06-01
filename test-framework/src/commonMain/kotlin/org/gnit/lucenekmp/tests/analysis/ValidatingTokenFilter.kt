package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute

/**
 * A TokenFilter that checks consistency of the tokens (eg offsets are consistent with one another).
 */
class ValidatingTokenFilter(input: TokenStream, private val name: String) : TokenFilter(input) {
    companion object {
        private const val MAX_DEBUG_TOKENS = 20
    }

    private var pos = 0
    private var lastStartOffset = 0

    // Maps position to the start/end offset:
    private val posToStartOffset = hashMapOf<Int, Int>()
    private val posToEndOffset = hashMapOf<Int, Int>()

    private val posIncAtt = getAttribute(PositionIncrementAttribute::class)
    private val posLenAtt = getAttribute(PositionLengthAttribute::class)
    private val offsetAtt = getAttribute(OffsetAttribute::class)
    private val termAtt = getAttribute(CharTermAttribute::class)

    // record the last MAX_DEBUG_TOKENS tokens seen so they can be dumped on failure
    private val tokens = ArrayDeque<Token>()

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }

        var startOffset = 0
        var endOffset = 0

        val posInc = posIncAtt?.getPositionIncrement() ?: 0
        if (offsetAtt != null) {
            startOffset = offsetAtt.startOffset()
            endOffset = offsetAtt.endOffset()
        }
        val posLen = posLenAtt?.positionLength ?: 1

        addToken(startOffset, endOffset, posInc)

        if (posIncAtt != null) {
            pos += posInc
            if (pos == -1) {
                throw IllegalStateException("$name: first posInc must be > 0")
            }
        }

        if (offsetAtt != null) {
            if (startOffset < lastStartOffset) {
                throw IllegalStateException(
                    "$name: offsets must not go backwards startOffset=$startOffset is < lastStartOffset=$lastStartOffset"
                )
            }
            lastStartOffset = offsetAtt.startOffset()
        }

        if (offsetAtt != null && posIncAtt != null) {
            if (!posToStartOffset.containsKey(pos)) {
                posToStartOffset[pos] = startOffset
            } else {
                val oldStartOffset = posToStartOffset[pos]!!
                if (oldStartOffset != startOffset) {
                    throw IllegalStateException(
                        "$name: inconsistent startOffset at pos=$pos: $oldStartOffset vs $startOffset; token=$termAtt"
                    )
                }
            }

            val endPos = pos + posLen
            if (!posToEndOffset.containsKey(endPos)) {
                posToEndOffset[endPos] = endOffset
            } else {
                val oldEndOffset = posToEndOffset[endPos]!!
                if (oldEndOffset != endOffset) {
                    throw IllegalStateException(
                        "$name: inconsistent endOffset at pos=$endPos: $oldEndOffset vs $endOffset; token=$termAtt"
                    )
                }
            }
        }

        return true
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        pos = -1
        posToStartOffset.clear()
        posToEndOffset.clear()
        lastStartOffset = 0
        tokens.clear()
    }

    private fun addToken(startOffset: Int, endOffset: Int, posInc: Int) {
        if (tokens.size == MAX_DEBUG_TOKENS) {
            tokens.removeFirst()
        }
        tokens.addLast(Token(termAtt.toString(), posInc, startOffset, endOffset))
    }

    fun dump(): String {
        val buf = StringBuilder()
        buf.append(name).append(": ")
        for (token in tokens) {
            buf.append(token.toString())
                .append("<[")
                .append(token.startOffset())
                .append("-")
                .append(token.endOffset())
                .append("] +")
                .append(token.getPositionIncrement())
                .append("> ")
        }
        return buf.toString()
    }
}
