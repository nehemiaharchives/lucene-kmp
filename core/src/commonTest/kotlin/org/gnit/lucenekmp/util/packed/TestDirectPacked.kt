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
package org.gnit.lucenekmp.util.packed

import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.LongValues
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestDirectPacked : LuceneTestCase() {

    /** simple encode/decode */
    @Test
    fun testSimple() {
        val dir = newDirectory()
        val bitsPerValue = DirectWriter.bitsRequired(2)
        val output: IndexOutput = dir.createOutput("foo", IOContext.DEFAULT)
        val writer = DirectWriter.getInstance(output, 5, bitsPerValue)
        writer.add(1)
        writer.add(0)
        writer.add(2)
        writer.add(1)
        writer.add(2)
        writer.finish()
        output.close()
        val input: IndexInput = dir.openInput("foo", IOContext.DEFAULT)
        val reader: LongValues =
            DirectReader.getInstance(input.randomAccessSlice(0, input.length()), bitsPerValue, 0)
        assertEquals(1, reader.get(0))
        assertEquals(0, reader.get(1))
        assertEquals(2, reader.get(2))
        assertEquals(1, reader.get(3))
        assertEquals(2, reader.get(4))
        input.close()
        dir.close()
    }

    /** test exception is delivered if you add the wrong number of values */
    @Test
    fun testNotEnoughValues() {
        val dir = newDirectory()
        val bitsPerValue = DirectWriter.bitsRequired(2)
        val output: IndexOutput = dir.createOutput("foo", IOContext.DEFAULT)
        val writer = DirectWriter.getInstance(output, 5, bitsPerValue)
        writer.add(1)
        writer.add(0)
        writer.add(2)
        writer.add(1)
        val expected =
            expectThrows(IllegalStateException::class) {
                writer.finish()
            }
        assertTrue(expected!!.message!!.startsWith("Wrong number of values added"))

        output.close()
        dir.close()
    }

    @Test
    fun testRandom() {
        val dir = newDirectory()
        for (bpv in 1..64) {
            doTestBpv(dir, bpv, 0, false)
        }
        dir.close()
    }

    @Test
    fun testRandomWithOffset() {
        val dir = newDirectory()
        val offset = TestUtil.nextInt(random(), 1, 100)
        for (bpv in 1..64) {
            doTestBpv(dir, bpv, offset.toLong(), false)
        }
        dir.close()
    }

    @Test
    fun testRandomMerge() {
        val dir = newDirectory()
        for (bpv in 1..64) {
            doTestBpv(dir, bpv, 0, true)
        }
        dir.close()
    }

    @Test
    fun testRandomMergeWithOffset() {
        val dir = newDirectory()
        val offset = TestUtil.nextInt(random(), 1, 100)
        for (bpv in 1..64) {
            doTestBpv(dir, bpv, offset.toLong(), true)
        }
        dir.close()
    }

    private fun doTestBpv(directory: Directory, bpv: Int, offset: Long, merge: Boolean) {
        val random: Random = random()
        val numIters = if (LuceneTestCase.TEST_NIGHTLY) 100 else 10
        for (i in 0 until numIters) {
            val original = randomLongs(random, bpv)
            val bitsRequired = if (bpv == 64) 64 else DirectWriter.bitsRequired(1L shl (bpv - 1))
            val name = "bpv${bpv}_$i"
            val output: IndexOutput = directory.createOutput(name, IOContext.DEFAULT)
            for (j in 0 until offset) {
                output.writeByte(random.nextInt().toByte())
            }
            val writer = DirectWriter.getInstance(output, original.size.toLong(), bitsRequired)
            for (j in original.indices) {
                writer.add(original[j])
            }
            writer.finish()
            output.close()
            val input: IndexInput = directory.openInput(name, IOContext.DEFAULT)
            val reader: LongValues =
                if (merge) {
                    DirectReader.getMergeInstance(
                        input.randomAccessSlice(0, input.length()),
                        bitsRequired,
                        offset,
                        original.size.toLong()
                    )!!
                } else {
                    DirectReader.getInstance(
                        input.randomAccessSlice(0, input.length()),
                        bitsRequired,
                        offset
                    )
                }
            for (j in original.indices) {
                assertEquals(original[j], reader.get(j.toLong()), "bpv=$bpv")
            }
            input.close()
        }
    }

    private fun randomLongs(random: Random, bpv: Int): LongArray {
        val amount = random.nextInt(5000)
        val longs = LongArray(amount)
        val max = PackedInts.maxValue(bpv)
        for (i in longs.indices) {
            longs[i] = RandomNumbers.randomLongBetween(random, 0, max)
        }
        return longs
    }
}
