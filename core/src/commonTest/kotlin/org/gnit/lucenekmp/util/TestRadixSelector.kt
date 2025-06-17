package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestRadixSelector : LuceneTestCase() {

    @Test
    fun testSelect() {
        for (iter in 0 until 100) {
            doTestSelect()
        }
    }

    private fun doTestSelect() {
        val from = random().nextInt(5)
        val to = from + TestUtil.nextInt(random(), 1, 5)
        val maxLen = TestUtil.nextInt(random(), 1, 12)
        val arr = Array(from + to + random().nextInt(5)) {
            val bytes = ByteArray(TestUtil.nextInt(random(), 0, maxLen))
            for (i in bytes.indices) {
                bytes[i] = random().nextInt(128).toByte()
            }
            newBytesRef(bytes)
        }
        doTest(arr, from, to, maxLen)
    }

    @Test
    fun testSharedPrefixes() {
        for (iter in 0 until 100) {
            doTestSharedPrefixes()
        }
    }

    private fun doTestSharedPrefixes() {
        val from = random().nextInt(5)
        val to = from + TestUtil.nextInt(random(), 1, 5)
        val maxLen = TestUtil.nextInt(random(), 1, 12)
        val arr = Array(from + to + random().nextInt(5)) {
            val bytes = ByteArray(TestUtil.nextInt(random(), 0, maxLen))
            for (i in bytes.indices) {
                bytes[i] = random().nextInt(128).toByte()
            }
            newBytesRef(bytes)
        }
        val sharedPrefixLength = minOf(arr[0].length, TestUtil.nextInt(random(), 1, maxLen))
        for (i in 1 until arr.size) {
            val len = minOf(sharedPrefixLength, arr[i].length)
            arr[0].bytes.copyInto(
                destination = arr[i].bytes,
                destinationOffset = arr[i].offset,
                startIndex = arr[0].offset,
                endIndex = arr[0].offset + len
            )
        }
        doTest(arr, from, to, maxLen)
    }

    private fun doTest(arr: Array<BytesRef>, from: Int, to: Int, maxLen: Int) {
        val k = TestUtil.nextInt(random(), from, to - 1)

        val expected = arr.copyOf()
        Arrays.sort(expected, from, to)

        val actual = arr.copyOf()
        val enforcedMaxLen = if (random().nextBoolean()) maxLen else Int.MAX_VALUE
        val selector = object : RadixSelector(enforcedMaxLen) {
            override fun swap(i: Int, j: Int) {
                ArrayUtil.swap(actual, i, j)
            }
            override fun byteAt(i: Int, k: Int): Int {
                assertTrue(k < enforcedMaxLen)
                val b = actual[i]
                return if (k >= b.length) -1 else Byte.toUnsignedInt(b.bytes[b.offset + k])
            }
        }
        selector.select(from, to, k)

        assertEquals(expected[k], actual[k])
        for (i in actual.indices) {
            if (i < from || i >= to) {
                assertSame(arr[i], actual[i])
            } else if (i <= k) {
                assertTrue(actual[i].compareTo(actual[k]) <= 0)
            } else {
                assertTrue(actual[i].compareTo(actual[k]) >= 0)
            }
        }
    }
}
