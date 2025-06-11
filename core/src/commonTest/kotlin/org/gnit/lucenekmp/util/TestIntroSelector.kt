package org.gnit.lucenekmp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertSame
import kotlin.random.Random
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.jdkport.Arrays

class TestIntroSelector : LuceneTestCase() {
    @Test
    fun testSelect() {
        val random: Random = random()
        repeat(100) {
            doTestSelect(random)
        }
    }

    private fun doTestSelect(random: Random) {
        val from = random.nextInt(5)
        val to = from + TestUtil.nextInt(random, 1, 10000)
        val max = if (random.nextBoolean()) random.nextInt(100) else random.nextInt(100000)
        val arr = Array(to + random.nextInt(5)) { TestUtil.nextInt(random, 0, max) }
        val k = TestUtil.nextInt(random, from, to - 1)

        val expected = arr.copyOf()
        Arrays.sort(expected, from, to)

        val actual = arr.copyOf()
        val selector = object : IntroSelector() {
            private var pivot: Int? = null

            override fun swap(i: Int, j: Int) {
                ArrayUtil.swap(actual, i, j)
            }

            override fun setPivot(i: Int) {
                pivot = actual[i]
            }

            override fun comparePivot(j: Int): Int {
                return pivot!!.compareTo(actual[j])
            }
        }
        if (random.nextBoolean()) {
            selector.select(from, to, k)
        } else {
            selector.select(from, to, k, random.nextInt(3))
        }

        assertEquals(expected[k], actual[k])
        for (i in actual.indices) {
            when {
                i < from || i >= to -> assertSame(arr[i], actual[i])
                i <= k -> assertTrue(actual[i] <= actual[k])
                else -> assertTrue(actual[i] >= actual[k])
            }
        }
    }
}
