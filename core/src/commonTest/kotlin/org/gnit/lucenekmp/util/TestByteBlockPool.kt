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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil

class TestByteBlockPool : LuceneTestCase() {

    @Test
    fun testAppendFromOtherPool() {
        val random: Random = random()
        val pool = ByteBlockPool(ByteBlockPool.DirectAllocator())
        val numBytes = atLeast(2 shl 16)
        val bytes = ByteArray(numBytes)
        random.nextBytes(bytes)
        pool.append(bytes)

        val anotherPool = ByteBlockPool(ByteBlockPool.DirectAllocator())
        val existingBytes = ByteArray(atLeast(500))
        anotherPool.append(existingBytes)

        var offset = TestUtil.nextInt(random, 1, 2 shl 15)
        var length = bytes.size - offset
        if (random.nextBoolean()) {
            length = TestUtil.nextInt(random, 1, length)
        }
        anotherPool.append(pool, offset.toLong(), length)

        assertEquals(existingBytes.size + length.toLong(), anotherPool.position)

        val results = ByteArray(length)
        anotherPool.readBytes(existingBytes.size.toLong(), results, 0, results.size)
        for (i in 0 until length) {
            assertEquals(bytes[offset + i], results[i], "byte @ index=$i")
        }
    }

    @Test
    fun testReadAndWrite() {
        val bytesUsed = Counter.newCounter()
        val pool = ByteBlockPool(ByteBlockPool.DirectTrackingAllocator(bytesUsed))
        pool.nextBuffer()
        val reuseFirst = random().nextBoolean()
        for (j in 0..<2) {
            val list = mutableListOf<BytesRef>()
            val maxLength = atLeast(500)
            val numValues = atLeast(100)
            val ref = BytesRefBuilder()
            for (i in 0 until numValues) {
                val length = TestUtil.nextInt(random(), 1, maxLength)
                val valueBytes = ByteArray(length)
                random().nextBytes(valueBytes)
                val br = BytesRef(valueBytes)
                list.add(br)
                pool.append(br)
                // keep ref in sync for reuse
                ref.copyBytes(br)
            }
            var position = 0L
            val builder = BytesRefBuilder()
            for (expected in list) {
                ref.grow(expected.length)
                ref.setLength(expected.length)
                when (random().nextInt(2)) {
                    0 -> pool.readBytes(position, ref.bytes(), 0, ref.length())
                    1 -> {
                        val scratch = BytesRef()
                        pool.setBytesRef(builder, scratch, position, ref.length())
                        scratch.bytes.copyInto(ref.bytes(), 0, scratch.offset, scratch.offset + ref.length())
                    }
                    else -> fail()
                }
                assertEquals(expected, ref.get())
                position += ref.length()
            }
            pool.reset(random().nextBoolean(), reuseFirst)
            if (reuseFirst) {
                assertEquals(ByteBlockPool.BYTE_BLOCK_SIZE.toLong(), bytesUsed.get())
            } else {
                assertEquals(0L, bytesUsed.get())
                pool.nextBuffer()
            }
        }
    }

    @Test
    fun testLargeRandomBlocks() {
        val bytesUsed = Counter.newCounter()
        val pool = ByteBlockPool(ByteBlockPool.DirectTrackingAllocator(bytesUsed))
        pool.nextBuffer()

        var totalBytes = 0L
        val items = mutableListOf<ByteArray>()
        for (i in 0 until 100) {
            val size = if (random().nextBoolean()) {
                TestUtil.nextInt(random(), 100, 1000)
            } else {
                TestUtil.nextInt(random(), 50000, 100000)
            }
            val b = ByteArray(size)
            random().nextBytes(b)
            items.add(b)
            pool.append(BytesRef(b))
            totalBytes += size
            assertEquals(totalBytes, pool.position)
        }

        var position = 0L
        for (expected in items) {
            val actual = ByteArray(expected.size)
            pool.readBytes(position, actual, 0, actual.size)
            assertTrue(expected.contentEquals(actual))
            position += expected.size
        }
    }

    @Test
    fun testTooManyAllocs() {
        val pool = ByteBlockPool(object : ByteBlockPool.Allocator(0) {
            private val buffer = ByteArray(0)
            override fun recycleByteBlocks(blocks: Array<ByteArray?>, start: Int, end: Int) {}
            override val byteBlock: ByteArray
                get() = buffer
        })
        pool.nextBuffer()

        var throwsException = false
        val limit = Int.MAX_VALUE / ByteBlockPool.BYTE_BLOCK_SIZE + 1
        for (i in 0 until limit) {
            try {
                pool.nextBuffer()
            } catch (e: ArithmeticException) {
                throwsException = true
                break
            }
        }
        assertTrue(throwsException)
        assertTrue(pool.byteOffset + ByteBlockPool.BYTE_BLOCK_SIZE < pool.byteOffset)
    }
}
