package org.gnit.lucenekmp.tests.analysis

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random

/**
 * Randomly inserts overlapping tokens with variable position length.
 */
class MockGraphTokenFilter(private val random: Random, input: TokenStream) : LookaheadTokenFilter<MockGraphTokenFilter.Position>(input) {
    private val termAtt = addAttribute(CharTermAttribute::class)
    private val seed = random.nextLong()
    private var r: Random? = null

    class Position : LookaheadTokenFilter.Position()

    override fun newPosition(): Position = Position()

    override fun afterPosition() {
        if (r!!.nextInt(7) == 5) {
            val posLength = TestUtil.nextInt(r!!, 1, 5)
            val posEndData = positions.get(outputPos + posLength)
            while (!end && posEndData.endOffset == -1 && inputPos <= outputPos + posLength) {
                if (!peekToken()) break
            }
            if (posEndData.endOffset != -1) {
                insertToken()
                clearAttributes()
                posLenAtt.positionLength = posLength
                termAtt.append(TestUtil.randomUnicodeString(r!!))
                posIncAtt.setPositionIncrement(0)
                offsetAtt.setOffset(positions.get(outputPos).startOffset, posEndData.endOffset)
            }
        }
    }

    override fun reset() {
        super.reset()
        r = Random(seed)
    }

    override fun close() {
        super.close()
        r = null
    }
}
