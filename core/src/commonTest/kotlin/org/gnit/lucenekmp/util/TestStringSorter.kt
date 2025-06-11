package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.comparisons.naturalOrder
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestStringSorter : LuceneTestCase() {

    private fun test(refs: Array<BytesRef>, len: Int) {
        test(refs.copyOfRange(0, len), len, BytesRefComparator.NATURAL)
        test(refs.copyOfRange(0, len), len, naturalOrder())
        testStable(refs.copyOfRange(0, len), len, BytesRefComparator.NATURAL)
        testStable(refs.copyOfRange(0, len), len, naturalOrder())
    }

    private fun test(refs: Array<BytesRef>, len: Int, comparator: Comparator<BytesRef>) {
        val expected = ArrayUtil.copyOfSubArray(refs, 0, len)
        Arrays.sort(expected)

        object : StringSorter(comparator) {
            override fun get(builder: BytesRefBuilder, result: BytesRef, i: Int) {
                val ref = refs[i]
                result.offset = ref.offset
                result.length = ref.length
                result.bytes = ref.bytes
            }

            override fun swap(i: Int, j: Int) {
                val tmp = refs[i]
                refs[i] = refs[j]
                refs[j] = tmp
            }
        }.sort(0, len)
        val actual = ArrayUtil.copyOfSubArray(refs, 0, len)
        assertContentEquals(expected, actual)
    }

    private fun testStable(refs: Array<BytesRef>, len: Int, comparator: Comparator<BytesRef>) {
        val expected = ArrayUtil.copyOfSubArray(refs, 0, len)
        Arrays.sort(expected)

        val ord = IntArray(len) { it }
        object : StableStringSorter(comparator) {
            private val tmp = IntArray(len)

            override fun save(i: Int, j: Int) {
                tmp[j] = ord[i]
            }

            override fun restore(i: Int, j: Int) {
                tmp.copyInto(ord, i, i, j)
            }

            override fun get(builder: BytesRefBuilder, result: BytesRef, i: Int) {
                val ref = refs[ord[i]]
                result.offset = ref.offset
                result.length = ref.length
                result.bytes = ref.bytes
            }

            override fun swap(i: Int, j: Int) {
                val t = ord[i]
                ord[i] = ord[j]
                ord[j] = t
            }
        }.sort(0, len)

        for (i in 0 until len) {
            assertEquals(expected[i], refs[ord[i]])
            if (i > 0 && expected[i] == expected[i - 1]) {
                assertTrue(ord[i] > ord[i - 1], "not stable: ${'$'}{ord[i]} <= ${'$'}{ord[i - 1]}")
            }
        }
    }

    @Test
    fun testEmpty() {
        test(Array(random().nextInt(5)) { BytesRef() }, 0)
    }

    private fun randomBytesRef(): BytesRef {
        val length = TestUtil.nextInt(random(), 1, 20)
        val b = ByteArray(length)
        for (i in b.indices) {
            b[i] = random().nextInt(128).toByte()
        }
        return BytesRef(b)
    }

    @Test
    fun testOneValue() {
        val bytes = randomBytesRef()
        test(arrayOf(bytes), 1)
    }

    @Test
    fun testTwoValues() {
        val bytes1 = randomBytesRef()
        val bytes2 = randomBytesRef()
        test(arrayOf(bytes1, bytes2), 2)
    }

    private fun testRandom(commonPrefixLen: Int, maxLen: Int) {
        val commonPrefix = ByteArray(commonPrefixLen)
        for (i in commonPrefix.indices) {
            commonPrefix[i] = random().nextInt(128).toByte()
        }
        val len = random().nextInt(100000)
        val bytes = arrayOfNulls<BytesRef>(len + random().nextInt(50))
        for (i in 0 until len) {
            val b = ByteArray(commonPrefixLen + random().nextInt(maxLen))
            for (j in b.indices) {
                b[j] = random().nextInt(128).toByte()
            }
            commonPrefix.copyInto(b, 0, 0, commonPrefixLen)
            bytes[i] = BytesRef(b)
        }
        @Suppress("UNCHECKED_CAST")
        test(bytes as Array<BytesRef>, len)
    }

    @Test
    @Ignore
    fun testRandom() {
        val numIters = atLeast(3)
        repeat(numIters) { testRandom(0, 10) }
    }

    @Test
    @Ignore
    fun testRandomWithLotsOfDuplicates() {
        val numIters = atLeast(3)
        repeat(numIters) { testRandom(0, 2) }
    }

    @Test
    @Ignore
    fun testRandomWithSharedPrefix() {
        val numIters = atLeast(3)
        repeat(numIters) { testRandom(TestUtil.nextInt(random(), 1, 30), 10) }
    }

    @Test
    @Ignore
    fun testRandomWithSharedPrefixAndLotsOfDuplicates() {
        val numIters = atLeast(3)
        repeat(numIters) { testRandom(TestUtil.nextInt(random(), 1, 30), 2) }
    }
}

