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
package org.gnit.lucenekmp.util.bkd

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.System
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestBKDUtil : LuceneTestCase() {
    @Test
    fun testEquals4() {
        val aOffset = TestUtil.nextInt(random(), 0, 3)
        val a = ByteArray(Int.SIZE_BYTES + aOffset)
        val bOffset = TestUtil.nextInt(random(), 0, 3)
        val b = ByteArray(Int.SIZE_BYTES + bOffset)

        for (i in 0 until Int.SIZE_BYTES) {
            a[aOffset + i] = random().nextInt(1 shl 8).toByte()
        }
        System.arraycopy(a, aOffset, b, bOffset, 4)

        assertTrue(BKDUtil.equals4(a, aOffset, b, bOffset))

        for (i in 0 until Int.SIZE_BYTES) {
            do {
                b[bOffset + i] = random().nextInt(1 shl 8).toByte()
            } while (b[bOffset + i] == a[aOffset + i])

            assertFalse(BKDUtil.equals4(a, aOffset, b, bOffset))

            b[bOffset + i] = a[aOffset + i]
        }
    }

    @Test
    fun testEquals8() {
        val aOffset = TestUtil.nextInt(random(), 0, 7)
        val a = ByteArray(Long.SIZE_BYTES + aOffset)
        val bOffset = TestUtil.nextInt(random(), 0, 7)
        val b = ByteArray(Long.SIZE_BYTES + bOffset)

        for (i in 0 until Long.SIZE_BYTES) {
            a[aOffset + i] = random().nextInt(1 shl 8).toByte()
        }
        System.arraycopy(a, aOffset, b, bOffset, 8)

        assertTrue(BKDUtil.equals8(a, aOffset, b, bOffset))

        for (i in 0 until Long.SIZE_BYTES) {
            do {
                b[bOffset + i] = random().nextInt(1 shl 8).toByte()
            } while (b[bOffset + i] == a[aOffset + i])

            assertFalse(BKDUtil.equals8(a, aOffset, b, bOffset))

            b[bOffset + i] = a[aOffset + i]
        }
    }

    @Test
    fun testCommonPrefixLength4() {
        val aOffset = TestUtil.nextInt(random(), 0, 3)
        val a = ByteArray(Int.SIZE_BYTES + aOffset)
        val bOffset = TestUtil.nextInt(random(), 0, 3)
        val b = ByteArray(Int.SIZE_BYTES + bOffset)

        for (i in 0 until Int.SIZE_BYTES) {
            a[aOffset + i] = random().nextInt(1 shl 8).toByte()
            do {
                b[bOffset + i] = random().nextInt(1 shl 8).toByte()
            } while (b[bOffset + i] == a[aOffset + i])
        }

        for (i in 0 until Int.SIZE_BYTES) {
            assertEquals(i, BKDUtil.commonPrefixLength4(a, aOffset, b, bOffset))
            b[bOffset + i] = a[aOffset + i]
        }

        assertEquals(4, BKDUtil.commonPrefixLength4(a, aOffset, b, bOffset))
    }

    @Test
    fun testCommonPrefixLength8() {
        val aOffset = TestUtil.nextInt(random(), 0, 7)
        val a = ByteArray(Long.SIZE_BYTES + aOffset)
        val bOffset = TestUtil.nextInt(random(), 0, 7)
        val b = ByteArray(Long.SIZE_BYTES + bOffset)

        for (i in 0 until Long.SIZE_BYTES) {
            a[aOffset + i] = random().nextInt(1 shl 8).toByte()
            do {
                b[bOffset + i] = random().nextInt(1 shl 8).toByte()
            } while (b[bOffset + i] == a[aOffset + i])
        }

        for (i in 0 until Long.SIZE_BYTES) {
            assertEquals(i, BKDUtil.commonPrefixLength8(a, aOffset, b, bOffset))
            b[bOffset + i] = a[aOffset + i]
        }

        assertEquals(8, BKDUtil.commonPrefixLength8(a, aOffset, b, bOffset))
    }

    @Test
    fun testCommonPrefixLengthN() {
        val numBytes = TestUtil.nextInt(random(), 2, 16)

        val aOffset = TestUtil.nextInt(random(), 0, numBytes - 1)
        val a = ByteArray(numBytes + aOffset)
        val bOffset = TestUtil.nextInt(random(), 0, numBytes - 1)
        val b = ByteArray(numBytes + bOffset)

        for (i in 0 until numBytes) {
            a[aOffset + i] = random().nextInt(1 shl 8).toByte()
            do {
                b[bOffset + i] = random().nextInt(1 shl 8).toByte()
            } while (b[bOffset + i] == a[aOffset + i])
        }

        for (i in 0 until numBytes) {
            assertEquals(
                i,
                BKDUtil.commonPrefixLengthN(a, aOffset, b, bOffset, numBytes)
            )
            b[bOffset + i] = a[aOffset + i]
        }

        assertEquals(
            numBytes,
            BKDUtil.commonPrefixLengthN(a, aOffset, b, bOffset, numBytes)
        )
    }
}
