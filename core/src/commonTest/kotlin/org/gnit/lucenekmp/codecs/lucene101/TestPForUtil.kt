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
package org.gnit.lucenekmp.codecs.lucene101

import org.gnit.lucenekmp.internal.vectorization.PostingDecodingUtil
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.jdkport.Arrays
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TestPForUtil : LuceneTestCase() {

    @Test
    fun testEncodeDecode() {
        val iterations = RandomNumbers.randomIntBetween(LuceneTestCase.random(), 50, 1000)
        val values = createTestData(iterations, 31)

        val d: Directory = ByteBuffersDirectory()
        val endPointer = encodeTestData(iterations, values, d)

        val input: IndexInput = d.openInput("test.bin", IOContext.READONCE)
        val pdu: PostingDecodingUtil =
            Lucene101PostingsReader.VECTORIZATION_PROVIDER.newPostingDecodingUtil(input)
        val pforUtil = PForUtil()
        for (i in 0 until iterations) {
            if (LuceneTestCase.random().nextInt(5) == 0) {
                PForUtil.skip(input)
                continue
            }
            val restored = IntArray(ForUtil.BLOCK_SIZE)
            pforUtil.decode(pdu, restored)
            val ints = IntArray(ForUtil.BLOCK_SIZE)
            for (j in 0 until ForUtil.BLOCK_SIZE) {
                ints[j] = restored[j]
            }
            assertContentEquals(
                ArrayUtil.copyOfSubArray(values, i * ForUtil.BLOCK_SIZE, (i + 1) * ForUtil.BLOCK_SIZE),
                ints,
                Arrays.toString(ints)
            )
        }
        assertEquals(endPointer, input.filePointer)
        input.close()

        d.close()
    }

    private fun createTestData(iterations: Int, maxBpv: Int): IntArray {
        val values = IntArray(iterations * ForUtil.BLOCK_SIZE)

        for (i in 0 until iterations) {
            val bpv = TestUtil.nextInt(LuceneTestCase.random(), 0, maxBpv)
            for (j in 0 until ForUtil.BLOCK_SIZE) {
                values[i * ForUtil.BLOCK_SIZE + j] = RandomNumbers.randomIntBetween(
                    LuceneTestCase.random(), 0, PackedInts.maxValue(bpv).toInt()
                )
                if (LuceneTestCase.random().nextInt(100) == 0) {
                    val exceptionBpv = if (LuceneTestCase.random().nextInt(10) == 0) {
                        minOf(bpv + TestUtil.nextInt(LuceneTestCase.random(), 9, 16), maxBpv)
                    } else {
                        minOf(bpv + TestUtil.nextInt(LuceneTestCase.random(), 1, 8), maxBpv)
                    }
                    values[i * ForUtil.BLOCK_SIZE + j] =
                        values[i * ForUtil.BLOCK_SIZE + j] or (
                            LuceneTestCase.random().nextInt(1 shl (exceptionBpv - bpv)) shl bpv
                        )
                }
            }
        }

        return values
    }

    private fun encodeTestData(iterations: Int, values: IntArray, d: Directory): Long {
        val out: IndexOutput = d.createOutput("test.bin", IOContext.DEFAULT)
        val pforUtil = PForUtil()

        for (i in 0 until iterations) {
            val source = IntArray(ForUtil.BLOCK_SIZE)
            for (j in 0 until ForUtil.BLOCK_SIZE) {
                source[j] = values[i * ForUtil.BLOCK_SIZE + j]
            }
            pforUtil.encode(source, out)
        }
        val endPointer = out.filePointer
        out.close()

        return endPointer
    }
}
