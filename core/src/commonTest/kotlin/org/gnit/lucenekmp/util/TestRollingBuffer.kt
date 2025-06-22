package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRollingBuffer : LuceneTestCase() {
    private fun rarely(random: Random): Boolean = TestUtil.rarely(random)

    private class Position : RollingBuffer.Resettable {
        var pos: Int = -1
        override fun reset() { pos = -1 }
    }

    @Test
    fun test() {
        val buffer = object : RollingBuffer<Position>() {
            override fun newInstance(): Position = Position()
        }

        repeat(100 * RANDOM_MULTIPLIER) {
            var freeBeforePos = 0
            val maxPos = atLeast(10000)
            val posSet = FixedBitSet(maxPos + 1000)
            var posUpto = 0
            val random = random()
            while (freeBeforePos < maxPos) {
                if (random.nextInt(4) == 1) {
                    val limit = if (rarely(random)) 1000 else 20
                    val inc = random.nextInt(limit)
                    val pos = freeBeforePos + inc
                    posUpto = kotlin.math.max(posUpto, pos)
                    val posData = buffer.get(pos)
                    if (!posSet.getAndSet(pos)) {
                        assertEquals(-1, posData.pos)
                        posData.pos = pos
                    } else {
                        assertEquals(pos, posData.pos)
                    }
                } else {
                    if (posUpto > freeBeforePos) {
                        freeBeforePos += random.nextInt(posUpto - freeBeforePos)
                    }
                    buffer.freeBefore(freeBeforePos)
                }
            }
            buffer.reset()
        }
    }
}
