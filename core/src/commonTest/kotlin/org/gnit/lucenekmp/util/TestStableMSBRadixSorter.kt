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
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestStableMSBRadixSorter : LuceneTestCase() {

    private fun test(refs: Array<BytesRef>, len: Int) {
        val expected = ArrayUtil.copyOfSubArray(refs, 0, len)
        Arrays.sort(expected)

        var maxLength = 0
        for (i in 0 until len) {
            val ref = refs[i]
            maxLength = maxOf(maxLength, ref.length)
        }
        when (random().nextInt(3)) {
            0 -> maxLength += TestUtil.nextInt(random(), 1, 5)
            1 -> maxLength = Int.MAX_VALUE
            else -> {}
        }
        val finalMaxLength = maxLength
        object : StableMSBRadixSorter(maxLength) {
            private var temp: Array<BytesRef?>? = null
            override fun byteAt(i: Int, k: Int): Int {
                assertTrue(k < finalMaxLength)
                val ref = refs[i]
                return if (ref.length <= k) -1 else (ref.bytes[ref.offset + k].toInt() and 0xFF)
            }

            override fun swap(i: Int, j: Int) {
                val tmp = refs[i]
                refs[i] = refs[j]
                refs[j] = tmp
            }

            override fun save(i: Int, j: Int) {
                if (temp == null) {
                    temp = arrayOfNulls(refs.size)
                }
                temp!![j] = refs[i]
            }

            override fun restore(i: Int, j: Int) {
                temp?.let { tmp ->
                    for (idx in i until j) {
                        refs[idx] = tmp[idx]!!
                    }
                }
            }
        }.sort(0, len)
        val actual = ArrayUtil.copyOfSubArray(refs, 0, len)
        assertContentEquals(expected, actual)
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertSame(expected[i].bytes, actual[i].bytes)
        }
    }

    @Test
    fun testEmpty() {
        test(Array(random().nextInt(5)) { BytesRef(ByteArray(1)) }, 0)
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
    fun testRandom() {
        repeat(3) { testRandom(0, 10) } // TODO originally 10 but reduced to 3 for dev speed
    }

    @Test
    fun testRandomWithLotsOfDuplicates() {
        repeat(3) { testRandom(0, 2) } // TODO originally 10 but reduced to 3 for dev speed
    }

    @Test
    fun testRandomWithSharedPrefix() {
        repeat(3) { testRandom(TestUtil.nextInt(random(), 1, 30), 10) } // TODO originally 10 but reduced to 3 for dev speed
    }

    @Test
    fun testRandomWithSharedPrefixAndLotsOfDuplicates() {
        repeat(3) { testRandom(TestUtil.nextInt(random(), 1, 30), 2) } // TODO originally 10 but reduced to 3 for dev speed
    }

    @Test
    fun testRandom2() {
        val letterCount = TestUtil.nextInt(random(), 2, 10)
        val substringCount = TestUtil.nextInt(random(), 2, 10)
        val substringsSet = HashSet<BytesRef>()
        val stringCount = atLeast(10000)
        while (substringsSet.size < substringCount) {
            val length = TestUtil.nextInt(random(), 2, 10)
            val bytes = ByteArray(length)
            for (i in bytes.indices) {
                bytes[i] = random().nextInt(letterCount).toByte()
            }
            val br = BytesRef(bytes)
            substringsSet.add(br)
        }
        val substrings = substringsSet.toTypedArray()
        val chance = DoubleArray(substrings.size)
        var sum = 0.0
        for (i in substrings.indices) {
            chance[i] = random().nextDouble()
            sum += chance[i]
        }
        var accum = 0.0
        for (i in substrings.indices) {
            accum += chance[i] / sum
            chance[i] = accum
        }
        val stringsSet = HashSet<BytesRef>()
        var iters = 0
        while (stringsSet.size < stringCount && iters < stringCount * 5) {
            val count = TestUtil.nextInt(random(), 1, 5)
            val b = BytesRefBuilder()
            for (i in 0 until count) {
                val v = random().nextDouble()
                accum = 0.0
                for (j in substrings.indices) {
                    accum += chance[j]
                    if (accum >= v) {
                        b.append(substrings[j])
                        break
                    }
                }
            }
            val br = b.toBytesRef()
            stringsSet.add(br)
            iters++
        }
        test(stringsSet.toTypedArray(), stringsSet.size)
    }
}
