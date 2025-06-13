package org.gnit.lucenekmp.tests.geo

import org.gnit.lucenekmp.geo.XYLine
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random

object ShapeTestUtil {
    private fun nextFloat(random: Random): Float {
        return (random.nextFloat() * 2 - 1) * Float.MAX_VALUE
    }

    fun nextLine(): XYLine {
        val random = LuceneTestCase.random()
        val count = TestUtil.nextInt(random, 2, 10)
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        for (i in 0 until count) {
            xs[i] = nextFloat(random)
            ys[i] = nextFloat(random)
        }
        return XYLine(xs, ys)
    }
}
