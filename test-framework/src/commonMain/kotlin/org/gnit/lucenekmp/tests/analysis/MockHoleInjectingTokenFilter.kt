package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random

// TODO: maybe, instead to be more "natural", we should make
// a MockRemovesTokensTF, ideally subclassing FilteringTF
// (in modules/analysis)

/** Randomly injects holes (similar to what a stopfilter would do) */
class MockHoleInjectingTokenFilter(random: Random, `in`: TokenStream) : TokenFilter(`in`) {

    private val randomSeed: Long = random.nextLong()
    private var random: Random? = null
    private val posIncAtt: PositionIncrementAttribute =
        addAttribute(PositionIncrementAttribute::class)
    private val posLenAtt: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)
    private var maxPos: Int = 0
    private var pos: Int = 0

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        random = Random(randomSeed)
        maxPos = -1
        pos = -1
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            val posInc = posIncAtt.getPositionIncrement()

            var nextPos = pos + posInc

            // Carefully inject a hole only where it won't mess up
            // the graph:
            if (posInc > 0 && maxPos <= nextPos && random!!.nextInt(5) == 3) {
                val holeSize = TestUtil.nextInt(random!!, 1, 5)
                posIncAtt.setPositionIncrement(posInc + holeSize)
                nextPos += holeSize
            }

            pos = nextPos
            maxPos = maxOf(maxPos, pos + posLenAtt.positionLength)

            return true
        } else {
            return false
        }
    }

    // TODO: end?
}
