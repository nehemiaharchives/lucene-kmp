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
package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ByteBlockPool
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestByteSliceReader : LuceneTestCase() {

    @Test
    fun testReadByte() {
        ensureInitialized()
        val sliceReader = ByteSliceReader()
        sliceReader.init(BLOCK_POOL, 0, BLOCK_POOL_END)
        for (expected in RANDOM_DATA) {
            assertEquals(expected, sliceReader.readByte())
        }
    }

    @Test
    fun testSkipBytes() {
        ensureInitialized()
        val random: Random = random()
        val sliceReader = ByteSliceReader()

        val maxSkipTo = RANDOM_DATA.size - 1
        val iterations = atLeast(random, 10)
        repeat(iterations) {
            sliceReader.init(BLOCK_POOL, 0, BLOCK_POOL_END)
            var curr = 0
            while (curr < maxSkipTo) {
                val skipTo = TestUtil.nextInt(random, curr, maxSkipTo)
                val step = skipTo - curr
                sliceReader.skipBytes(step.toLong())
                assertEquals(RANDOM_DATA[skipTo], sliceReader.readByte())
                curr = skipTo + 1
            }
        }
    }

    companion object {
        private lateinit var RANDOM_DATA: ByteArray
        private lateinit var BLOCK_POOL: ByteBlockPool
        private var BLOCK_POOL_END: Int = 0
        private var initialized = false

        private fun ensureInitialized() {
            if (initialized) {
                return
            }
            val len = atLeast(100)
            RANDOM_DATA = ByteArray(len)
            random().nextBytes(RANDOM_DATA)

            BLOCK_POOL = ByteBlockPool(ByteBlockPool.DirectAllocator())
            BLOCK_POOL.nextBuffer()
            val slicePool = ByteSlicePool(BLOCK_POOL)
            var buffer = BLOCK_POOL.buffer!!
            var upto = slicePool.newSlice(ByteSlicePool.FIRST_LEVEL_SIZE)
            for (randomByte in RANDOM_DATA) {
                if ((buffer[upto].toInt() and 16) != 0) {
                    upto = slicePool.allocSlice(buffer, upto)
                    buffer = BLOCK_POOL.buffer!!
                }
                buffer[upto++] = randomByte
            }
            BLOCK_POOL_END = upto
            initialized = true
        }
    }
}
