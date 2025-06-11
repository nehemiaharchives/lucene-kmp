package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.packed.PackedInts
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertContentEquals

class TestLSBRadixSorter : LuceneTestCase() {

    private fun test(sorter: LSBRadixSorter, maxLen: Int) {
        for (iter in 0 until 10) {
            val len = TestUtil.nextInt(random(), 0, maxLen)
            val arr = IntArray(len + random().nextInt(10))
            val numBits = random().nextInt(31)
            val maxValue = (1 shl numBits) - 1
            for (i in arr.indices) {
                arr[i] = TestUtil.nextInt(random(), 0, maxValue)
            }
            test(sorter, arr, len)
        }
    }

    private fun test(sorter: LSBRadixSorter, arr: IntArray, len: Int) {
        val expected = ArrayUtil.copyOfSubArray(arr, 0, len)
        expected.sort()

        var numBits = 0
        for (i in 0 until len) {
            numBits = max(numBits, PackedInts.bitsRequired(arr[i].toLong()))
        }

        if (random().nextBoolean()) {
            numBits = TestUtil.nextInt(random(), numBits, 32)
        }

        sorter.sort(numBits, arr, len)
        val actual = ArrayUtil.copyOfSubArray(arr, 0, len)
        assertContentEquals(expected, actual)
    }

    @Test
    fun testEmpty() {
        test(LSBRadixSorter(), 0)
    }

    @Test
    fun testOne() {
        test(LSBRadixSorter(), 1)
    }

    @Test
    fun testTwo() {
        test(LSBRadixSorter(), 2)
    }

    @Test
    fun testSimple() {
        test(LSBRadixSorter(), 100)
    }

    @Test
    fun testRandom() {
        test(LSBRadixSorter(), 10000)
    }

    @Test
    fun testSorted() {
        val sorter = LSBRadixSorter()
        for (iter in 0 until 10) {
            val arr = IntArray(10000)
            var a = 0
            for (i in arr.indices) {
                a += random().nextInt(10)
                arr[i] = a
            }
            val len = TestUtil.nextInt(random(), 0, arr.size)
            test(sorter, arr, len)
        }
    }
}

