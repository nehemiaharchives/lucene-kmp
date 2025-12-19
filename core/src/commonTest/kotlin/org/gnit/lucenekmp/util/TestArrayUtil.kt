/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.signum
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil.Companion.copyOfSubArray
import org.gnit.lucenekmp.util.ArrayUtil.Companion.growExact
import org.gnit.lucenekmp.util.ArrayUtil.Companion.growInRange
import org.gnit.lucenekmp.util.ArrayUtil.Companion.oversize
import kotlin.jvm.JvmRecord
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestArrayUtil : LuceneTestCase() {
    // Ensure ArrayUtil.getNextSize gives linear amortized cost of realloc/copy

    @Test
    fun testGrowth() {
        var currentSize = 0
        var copyCost: Long = 0

        // Make sure ArrayUtil hits Integer.MAX_VALUE, if we insist:
        while (currentSize != ArrayUtil.MAX_ARRAY_LENGTH) {
            val nextSize: Int = ArrayUtil.oversize(
                1 + currentSize, RamUsageEstimator.NUM_BYTES_OBJECT_REF
            )
            assertTrue(nextSize > currentSize)
            if (currentSize > 0) {
                copyCost += currentSize.toLong()
                val copyCostPerElement = (copyCost.toDouble()) / currentSize
                assertTrue(copyCostPerElement < 10.0, "cost $copyCostPerElement")
            }
            currentSize = nextSize
        }
    }

    @Test
    fun testMaxSize() {
        // intentionally pass invalid elemSizes:
        for (elemSize in 0..9) {
            assertEquals(
                ArrayUtil.MAX_ARRAY_LENGTH.toLong(), ArrayUtil.oversize(ArrayUtil.MAX_ARRAY_LENGTH, elemSize).toLong()
            )
            assertEquals(
                ArrayUtil.MAX_ARRAY_LENGTH.toLong(), ArrayUtil.oversize(
                    ArrayUtil.MAX_ARRAY_LENGTH - 1, elemSize
                ).toLong()
            )
        }
    }

    @Test
    fun testTooBig() {
        expectThrows<IllegalArgumentException>(
            IllegalArgumentException::class, {
                ArrayUtil.oversize(ArrayUtil.MAX_ARRAY_LENGTH + 1, 1)
            })
    }

    @Test
    fun testExactLimit() {
        assertEquals(
            ArrayUtil.MAX_ARRAY_LENGTH.toLong(), ArrayUtil.oversize(ArrayUtil.MAX_ARRAY_LENGTH, 1).toLong()
        )
    }

    @Test
    fun testInvalidElementSizes() {
        val rnd: Random = random()
        val num: Int = atLeast(10000)
        for (iter in 0..<num) {
            val minTargetSize: Int = rnd.nextInt(ArrayUtil.MAX_ARRAY_LENGTH)
            val elemSize: Int = rnd.nextInt(11)
            val v: Int = ArrayUtil.oversize(minTargetSize, elemSize)
            assertTrue(v >= minTargetSize)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testParseInt() {
        expectThrows<NumberFormatException>(
            NumberFormatException::class, {
                parseInt("")
            })

        expectThrows<NumberFormatException>(
            NumberFormatException::class, {
                parseInt("foo")
            })

        expectThrows<NumberFormatException>(
            NumberFormatException::class, {
                parseInt(Long.Companion.MAX_VALUE.toString())
            })

        expectThrows<NumberFormatException>(
            NumberFormatException::class, {
                parseInt("0.34")
            })

        var test = parseInt("1")
        assertTrue(test == 1, "$test does not equal: 1")
        test = parseInt("-10000")
        assertTrue(test == -10000, test.toString() + " does not equal: " + -10000)
        test = parseInt("1923")
        assertTrue(test == 1923, "$test does not equal: 1923")
        test = parseInt("-1")
        assertTrue(test == -1, test.toString() + " does not equal: " + -1)
        test = ArrayUtil.parseInt("foo 1923 bar".toCharArray(), 4, 4)
        assertTrue(test == 1923, "$test does not equal: 1923")
    }

    private fun createRandomArray(maxSize: Int): Array<Int> {
        val rnd: Random = random()
        val a = Array(rnd.nextInt(maxSize) + 1) { rnd.nextInt(maxSize + 1) }
        return a
    }

    @Test
    fun testIntroSort() {
        val num: Int = atLeast(50)
        for (i in 0..<num) {
            var a1 = createRandomArray(2000)
            var a2 = a1.copyOf()
            ArrayUtil.introSort(a1)
            Arrays.sort(a2)
            assertContentEquals(a2, a1)

            a1 = createRandomArray(2000)
            a2 = a1.copyOf()
            ArrayUtil.introSort(a1, reverseOrder<Int>())
            Arrays.sort<Int>(a2, reverseOrder<Int>())
            assertContentEquals(a2, a1)
            // reverse back, so we can test that completely backwards sorted array (worst case) is
            // working:
            ArrayUtil.introSort(a1)
            Arrays.sort(a2)
            assertContentEquals(a2, a1)
        }
    }

    private fun createSparseRandomArray(maxSize: Int): Array<Int> {
        val rnd: Random = random()
        val a = Array(rnd.nextInt(maxSize) + 1) { rnd.nextInt(2) }
        return a
    }

    @Test
    // This is a test for LUCENE-3054 (which fails without the merge sort fall back with stack
    // overflow in most cases)
    fun testQuickToHeapSortFallback() {
        val num: Int = atLeast(10)
        for (i in 0..<num) {
            val a1 = createSparseRandomArray(40000)
            val a2 = a1.copyOf()
            ArrayUtil.introSort<Int>(a1)
            Arrays.sort(a2)
            assertContentEquals(a2, a1)
        }
    }

    @Test
    fun testTimSort() {
        val num: Int = atLeast(50)
        for (i in 0..<num) {
            var a1 = createRandomArray(2000)
            var a2 = a1.copyOf()
            ArrayUtil.timSort<Int>(a1)
            Arrays.sort(a2)
            assertContentEquals(a2, a1)

            a1 = createRandomArray(2000)
            a2 = a1.copyOf()
            ArrayUtil.timSort<Int>(a1, reverseOrder<Int>())
            Arrays.sort<Int>(a2, reverseOrder<Int>())
            assertContentEquals(a2, a1)
            // reverse back, so we can test that completely backwards sorted array (worst case) is
            // working:
            ArrayUtil.timSort<Int>(a1)
            Arrays.sort(a2)
            assertContentEquals(a2, a1)
        }
    }

    internal data class Item(val `val`: Int, val order: Int) : Comparable<Item> {
        override fun compareTo(other: Item): Int {
            return this.order - other.order
        }

        override fun toString(): String {
            return `val`.toString()
        }
    }

    @Test
    fun testMergeSortStability() {
        val rnd: Random = random()
        val items = Array(100) {
            // half of the items have value but same order. The value of this items is sorted,
            // so they should always be in order after sorting.
            // The other half has defined order, but no (-1) value (they should appear after
            // all above, when sorted).
            val equal = rnd.nextBoolean()
            Item(if (equal) it + 1 else -1, if (equal) 0 else rnd.nextInt(1000) + 1)
        }

        if (VERBOSE) println("Before: " + items.contentToString())
        // if you replace this with ArrayUtil.quickSort(), test should fail:
        ArrayUtil.timSort<Item>(items)
        if (VERBOSE) println("Sorted: " + items.contentToString())

        var last = items[0]
        for (i in 1..<items.size) {
            val act = items[i]
            if (act.order == 0) {
                // order of "equal" items should be not mixed up
                assertTrue(act.`val` > last.`val`)
            }
            assertTrue(act.order >= last.order)
            last = act
        }
    }

    @Test
    fun testTimSortStability() {
        val rnd: Random = random()
        val items = Array(100) {
            // half of the items have value but same order. The value of this items is sorted,
            // so they should always be in order after sorting.
            // The other half has defined order, but no (-1) value (they should appear after
            // all above, when sorted).
            val equal = rnd.nextBoolean()
            Item(if (equal) it + 1 else -1, if (equal) 0 else rnd.nextInt(1000) + 1)
        }

        if (VERBOSE) println("Before: " + items.contentToString())
        // if you replace this with ArrayUtil.quickSort(), test should fail:
        ArrayUtil.timSort<Item>(items)
        if (VERBOSE) println("Sorted: " + items.contentToString())

        var last = items[0]
        for (i in 1..<items.size) {
            val act = items[i]
            if (act.order == 0) {
                // order of "equal" items should be not mixed up
                assertTrue(act.`val` > last.`val`)
            }
            assertTrue(act.order >= last.order)
            last = act
        }
    }

    @Test
    // should produce no exceptions
    fun testEmptyArraySort() {
        val a = kotlin.arrayOfNulls<Int>(0) as Array<Int>
        ArrayUtil.introSort(a)
        ArrayUtil.timSort(a)
        ArrayUtil.introSort(a, reverseOrder<Int>())
        ArrayUtil.timSort(a, reverseOrder<Int>())
    }

    @Test
    fun testSelect() {
        for (iter in 0..99) {
            doTestSelect()
        }
    }

    private fun doTestSelect() {
        val from: Int = random().nextInt(5)
        val to: Int = from + TestUtil.nextInt(
            random(), 1, 10000
        )
        val max: Int = if (random().nextBoolean()) random().nextInt(100) else random().nextInt(100000)
        val arr = Array(from + to + random().nextInt(5)) {
            TestUtil.nextInt(random(), 0, max)
        }
        val k: Int = TestUtil.nextInt(
            random(), from, to - 1
        )

        val expected = arr.copyOf()
        Arrays.sort(expected, from, to)

        val actual = arr.copyOf()
        ArrayUtil.select<Int>(actual, from, to, k, naturalOrder<Int>())

        assertEquals(expected[k], actual[k])
        for (i in actual.indices) {
            if (i < from || i >= to) {
                assertTrue(arr[i] == actual[i])
            } else if (i <= k) {
                assertTrue(actual[i] <= actual[k])
            } else {
                assertTrue(actual[i] >= actual[k])
            }
        }
    }

    @Test
    fun testGrowExact() {
        assertContentEquals(
            shortArrayOf(1, 2, 3, 0), ArrayUtil.growExact(
                shortArrayOf(1, 2, 3), 4
            )
        )
        assertContentEquals(
            shortArrayOf(1, 2, 3, 0, 0), ArrayUtil.growExact(
                shortArrayOf(1, 2, 3), 5
            )
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                ArrayUtil.growExact(
                    shortArrayOf(1, 2, 3), random().nextInt(3)
                )
            })

        assertContentEquals(
            intArrayOf(1, 2, 3, 0), ArrayUtil.growExact(intArrayOf(1, 2, 3), 4)
        )
        assertContentEquals(
            intArrayOf(1, 2, 3, 0, 0), ArrayUtil.growExact(
                intArrayOf(1, 2, 3), 5
            )
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                ArrayUtil.growExact(
                    intArrayOf(1, 2, 3), random().nextInt(3)
                )
            })

        assertContentEquals(
            longArrayOf(1, 2, 3, 0), ArrayUtil.growExact(
                longArrayOf(1, 2, 3), 4
            )
        )
        assertContentEquals(
            longArrayOf(1, 2, 3, 0, 0), ArrayUtil.growExact(
                longArrayOf(1, 2, 3), 5
            )
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                ArrayUtil.growExact(
                    longArrayOf(1, 2, 3), random().nextInt(3)
                )
            })

        assertArrayEquals(
            floatArrayOf(0.1f, 0.2f, 0.3f, 0f), ArrayUtil.growExact(floatArrayOf(0.1f, 0.2f, 0.3f), 4), 0.001f
        )
        assertArrayEquals(
            floatArrayOf(0.1f, 0.2f, 0.3f, 0f, 0f), ArrayUtil.growExact(floatArrayOf(0.1f, 0.2f, 0.3f), 5), 0.001f
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                ArrayUtil.growExact(
                    floatArrayOf(1f, 2f, 3f), random().nextInt(3)
                )
            })

        assertArrayEquals(
            doubleArrayOf(0.1, 0.2, 0.3, 0.0), ArrayUtil.growExact(doubleArrayOf(0.1, 0.2, 0.3), 4), 0.001
        )
        assertArrayEquals(
            doubleArrayOf(0.1, 0.2, 0.3, 0.0, 0.0), ArrayUtil.growExact(doubleArrayOf(0.1, 0.2, 0.3), 5), 0.001
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                ArrayUtil.growExact(
                    doubleArrayOf(0.1, 0.2, 0.3), random().nextInt(3)
                )
            })

        assertContentEquals(
            byteArrayOf(1, 2, 3, 0), ArrayUtil.growExact(
                byteArrayOf(1, 2, 3), 4
            )
        )
        assertContentEquals(
            byteArrayOf(1, 2, 3, 0, 0), ArrayUtil.growExact(
                byteArrayOf(1, 2, 3), 5
            )
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                ArrayUtil.growExact(
                    byteArrayOf(1, 2, 3), random().nextInt(3)
                )
            })

        assertContentEquals(
            charArrayOf('a', 'b', 'c', '\u0000'), ArrayUtil.growExact(
                charArrayOf('a', 'b', 'c'), 4
            )
        )
        assertContentEquals(
            charArrayOf('a', 'b', 'c', '\u0000', '\u0000'), ArrayUtil.growExact(charArrayOf('a', 'b', 'c'), 5)
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                ArrayUtil.growExact(
                    byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte()), random().nextInt(3)
                )
            })

        assertContentEquals(
            arrayOf<String?>("a1", "b2", "c3", null),
            ArrayUtil.growExact<String?>(arrayOf<String?>("a1", "b2", "c3"), 4)
        )
        assertContentEquals(
            arrayOf<String?>("a1", "b2", "c3", null, null),
            ArrayUtil.growExact<String?>(arrayOf<String?>("a1", "b2", "c3"), 5)
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                ArrayUtil.growExact<String>(
                    arrayOf<String>("a", "b", "c"), random().nextInt(3)
                )
            })
    }

    @Test
    fun testGrowInRange() {
        val array = intArrayOf(1, 2, 3)

        // If minLength is negative, maxLength does not matter
        expectThrows<AssertionError>(
            AssertionError::class, {
                ArrayUtil.growInRange(
                    array, -1, 4
                )
            })
        expectThrows<AssertionError>(
            AssertionError::class, {
                ArrayUtil.growInRange(
                    array, -1, 0
                )
            })
        expectThrows<AssertionError>(
            AssertionError::class, {
                ArrayUtil.growInRange(
                    array, -1, -1
                )
            })

        // If minLength > maxLength, we throw an exception
        expectThrows<IllegalArgumentException>(
            IllegalArgumentException::class, {
                ArrayUtil.growInRange(
                    array, 1, 0
                )
            })
        expectThrows<IllegalArgumentException>(
            IllegalArgumentException::class, {
                ArrayUtil.growInRange(
                    array, 4, 3
                )
            })
        expectThrows<IllegalArgumentException>(
            IllegalArgumentException::class, {
                ArrayUtil.growInRange(
                    array, 5, 4
                )
            })

        // If minLength is sufficient, we return the array
        assertSame(array, ArrayUtil.growInRange(array, 1, 4))
        assertSame(array, ArrayUtil.growInRange(array, 1, 2))
        assertSame(array, ArrayUtil.growInRange(array, 1, 1))

        val minLength = 4
        val maxLength = Int.Companion.MAX_VALUE

        // The array grows normally if maxLength permits
        assertEquals(
            ArrayUtil.oversize(minLength, Int.SIZE_BYTES).toLong(),
            ArrayUtil.growInRange(intArrayOf(1, 2, 3), minLength, maxLength).size.toLong()
        )

        // The array grows to maxLength if maxLength is limiting
        assertEquals(
            minLength.toLong(), ArrayUtil.growInRange(intArrayOf(1, 2, 3), minLength, minLength).size.toLong()
        )
    }

    @Test
    fun testCopyOfSubArray() {
        val shortArray = shortArrayOf(1, 2, 3)
        assertContentEquals(
            shortArrayOf(1), copyOfSubArray(shortArray, 0, 1)
        )
        assertContentEquals(
            shortArrayOf(1, 2, 3), copyOfSubArray(shortArray, 0, 3)
        )
        assertEquals(
            0, copyOfSubArray(shortArray, 0, 0).size.toLong()
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                copyOfSubArray(
                    shortArray, 0, 4 + random().nextInt(10)
                )
            })

        val intArray = intArrayOf(1, 2, 3)
        assertContentEquals(
            intArrayOf(1, 2), copyOfSubArray(intArray, 0, 2)
        )
        assertContentEquals(
            intArrayOf(1, 2, 3), copyOfSubArray(intArray, 0, 3)
        )
        assertEquals(
            0, copyOfSubArray(intArray, 1, 1).size.toLong()
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                copyOfSubArray(
                    intArray, 1, 4 + random().nextInt(10)
                )
            })

        val longArray = longArrayOf(1, 2, 3)
        assertContentEquals(
            longArrayOf(2), copyOfSubArray(longArray, 1, 2)
        )
        assertContentEquals(
            longArrayOf(1, 2, 3), copyOfSubArray(longArray, 0, 3)
        )
        assertEquals(
            0, copyOfSubArray(longArray, 2, 2).size.toLong()
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                copyOfSubArray(
                    longArray, 2, 4 + random().nextInt(10)
                )
            })

        val floatArray = floatArrayOf(0.1f, 0.2f, 0.3f)
        assertArrayEquals(
            floatArrayOf(0.2f, 0.3f), copyOfSubArray(floatArray, 1, 3), 0.001f
        )
        assertArrayEquals(
            floatArrayOf(0.1f, 0.2f, 0.3f), copyOfSubArray(floatArray, 0, 3), 0.001f
        )
        assertEquals(
            0, copyOfSubArray(floatArray, 0, 0).size.toLong()
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                copyOfSubArray(
                    floatArray, 3, 4 + random().nextInt(10)
                )
            })

        val doubleArray = doubleArrayOf(0.1, 0.2, 0.3)
        assertArrayEquals(
            doubleArrayOf(0.3), copyOfSubArray(doubleArray, 2, 3), 0.001
        )
        assertArrayEquals(
            doubleArrayOf(0.1, 0.2, 0.3), copyOfSubArray(doubleArray, 0, 3), 0.001
        )
        assertEquals(
            0, copyOfSubArray(doubleArray, 1, 1).size.toLong()
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                copyOfSubArray(
                    doubleArray, 0, 4 + random().nextInt(10)
                )
            })

        val byteArray = byteArrayOf(1, 2, 3)
        assertContentEquals(
            byteArrayOf(1), copyOfSubArray(byteArray, 0, 1)
        )
        assertContentEquals(
            byteArrayOf(1, 2, 3), copyOfSubArray(byteArray, 0, 3)
        )
        assertEquals(
            0, copyOfSubArray(byteArray, 1, 1).size.toLong()
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                copyOfSubArray(
                    byteArray, 1, 4 + random().nextInt(10)
                )
            })

        val charArray = charArrayOf('a', 'b', 'c')
        assertContentEquals(
            charArrayOf('a', 'b'), copyOfSubArray(charArray, 0, 2)
        )
        assertContentEquals(
            charArrayOf('a', 'b', 'c'), copyOfSubArray(charArray, 0, 3)
        )
        assertEquals(
            0, copyOfSubArray(charArray, 1, 1).size.toLong()
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                copyOfSubArray(
                    charArray, 3, 4
                )
            })

        val objectArray = arrayOf<String>("a1", "b2", "c3")
        assertContentEquals(
            arrayOf<String>("a1"), copyOfSubArray<String>(objectArray, 0, 1)
        )
        assertContentEquals(
            arrayOf<String>("a1", "b2", "c3"), copyOfSubArray<String>(objectArray, 0, 3)
        )
        assertEquals(
            0, copyOfSubArray<String>(objectArray, 1, 1).size.toLong()
        )
        expectThrows<IndexOutOfBoundsException>(
            IndexOutOfBoundsException::class, {
                copyOfSubArray<String>(
                    objectArray, 2, 5
                )
            })
    }

    @Test
    fun testCompareUnsigned4() {
        val aOffset: Int = TestUtil.nextInt(random(), 0, 3)
        val a = ByteArray(Int.SIZE_BYTES + aOffset)
        val bOffset: Int = TestUtil.nextInt(random(), 0, 3)
        val b = ByteArray(Int.SIZE_BYTES + bOffset)

        for (i in 0..<Int.SIZE_BYTES) {
            a[aOffset + i] = random().nextInt(1 shl 8).toByte()
            do {
                b[bOffset + i] = random().nextInt(1 shl 8).toByte()
            } while (b[bOffset + i] == a[aOffset + i])
        }

        for (i in 0..<Int.SIZE_BYTES) {
            val expected: Int = Arrays.compareUnsigned(
                a, aOffset, aOffset + Int.SIZE_BYTES, b, bOffset, bOffset + Int.SIZE_BYTES
            )
            val actual: Int = ArrayUtil.compareUnsigned4(a, aOffset, b, bOffset)
            assertEquals(
                Int.signum(expected).toLong(), Int.signum(actual).toLong()
            )
            b[bOffset + i] = a[aOffset + i]
        }

        assertEquals(
            0, ArrayUtil.compareUnsigned4(a, aOffset, b, bOffset).toLong()
        )
    }

    @Test
    fun testCompareUnsigned8() {
        val aOffset: Int = TestUtil.nextInt(random(), 0, 7)
        val a = ByteArray(Long.SIZE_BYTES + aOffset)
        val bOffset: Int = TestUtil.nextInt(random(), 0, 7)
        val b = ByteArray(Long.SIZE_BYTES + bOffset)

        for (i in 0..<Long.SIZE_BYTES) {
            a[aOffset + i] = random().nextInt(1 shl 8).toByte()
            do {
                b[bOffset + i] = random().nextInt(1 shl 8).toByte()
            } while (b[bOffset + i] == a[aOffset + i])
        }

        for (i in 0..<Long.SIZE_BYTES) {
            val expected: Int = Arrays.compareUnsigned(
                a, aOffset, aOffset + Long.SIZE_BYTES, b, bOffset, bOffset + Long.SIZE_BYTES
            )
            val actual: Int = ArrayUtil.compareUnsigned8(a, aOffset, b, bOffset)
            assertEquals(
                Int.signum(expected).toLong(), Int.signum(actual).toLong()
            )
            b[bOffset + i] = a[aOffset + i]
        }

        assertEquals(
            0, ArrayUtil.compareUnsigned8(a, aOffset, b, bOffset).toLong()
        )
    }

    companion object {
        private fun parseInt(s: String): Int {
            val start: Int = random().nextInt(5)
            val chars = CharArray(s.length + start + random().nextInt(4))
            s.toCharArray(chars, start, 0, s.length)
            return ArrayUtil.parseInt(chars, start, s.length)
        }
    }
}
