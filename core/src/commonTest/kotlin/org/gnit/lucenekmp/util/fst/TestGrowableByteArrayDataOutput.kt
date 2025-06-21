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
 */
package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.OutputStreamDataOutput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

import kotlin.test.assertContentEquals
class TestGrowableByteArrayDataOutput : LuceneTestCase() {

    @Test
    fun testRandom() {
        val iters = atLeast(10)
        val maxBytes = if (TEST_NIGHTLY) 200_000 else 20_000
        for (iter in 0 until iters) {
            val numBytes = TestUtil.nextInt(random(), 1, maxBytes)
            val expected = ByteArray(numBytes)
            val bytes = GrowableByteArrayDataOutput()
            if (VERBOSE) {
                println("TEST: iter=$iter numBytes=$numBytes")
            }
            var pos = 0
            while (pos < numBytes) {
                val op = random().nextInt(2)
                if (VERBOSE) {
                    println("  cycle pos=$pos")
                }
                when (op) {
                    0 -> {
                        val b = random().nextInt(256).toByte()
                        if (VERBOSE) {
                            println("    writeByte b=$b")
                        }
                        expected[pos++] = b
                        bytes.writeByte(b)
                    }
                    1 -> {
                        val len = random().nextInt(min(numBytes - pos, 100))
                        val temp = ByteArray(len)
                        random().nextBytes(temp)
                        if (VERBOSE) {
                            println("    writeBytes len=$len bytes=" + temp.contentToString())
                        }
                        temp.copyInto(expected, destinationOffset = pos, startIndex = 0, endIndex = temp.size)
                        bytes.writeBytes(temp, 0, temp.size)
                        pos += len
                    }
                }
                assertEquals(pos, bytes.position)

                if (pos > 0 && random().nextInt(50) == 17) {
                    val len = TestUtil.nextInt(random(), 1, min(pos, 100))
                    bytes.position = pos - len
                    pos -= len
                    Arrays.fill(expected, pos, pos + len, 0.toByte())
                    if (VERBOSE) {
                        println("    truncate len=$len newPos=$pos")
                    }
                }

                if (pos > 0 && random().nextInt(200) == 17) {
                    verify(bytes, expected, pos)
                }
            }

            val bytesToVerify = if (random().nextBoolean()) {
                if (VERBOSE) {
                    println("TEST: save/load final bytes")
                }
                val dir: Directory = ByteBuffersDirectory()
                val out = dir.createOutput("bytes", IOContext.DEFAULT)
                bytes.writeTo(out)
                out.close()
                val input = dir.openInput("bytes", IOContext.DEFAULT)
                val copy = GrowableByteArrayDataOutput()
                copy.copyBytes(input, numBytes.toLong())
                input.close()
                dir.close()
                copy
            } else {
                bytes
            }

            verify(bytesToVerify, expected, numBytes)
        }
    }

    @Test
    fun testCopyBytesOnByteStore() {
        val bytes = ByteArray(1024 * 8 + 10)
        val bytesOut = ByteArray(bytes.size)
        random().nextBytes(bytes)
        val offset = TestUtil.nextInt(random(), 0, 100)
        val len = bytes.size - offset
        val input = ByteArrayDataInput(bytes, offset, len)
        val o = GrowableByteArrayDataOutput()
        o.copyBytes(input, len.toLong())
        o.writeTo(0, bytesOut, 0, len)
        assertContentEquals(
            ArrayUtil.copyOfSubArray(bytesOut, 0, len),
            ArrayUtil.copyOfSubArray(bytes, offset, offset + len)
        )
    }

    private fun verify(bytes: GrowableByteArrayDataOutput, expected: ByteArray, totalLength: Int) {
        assertEquals(totalLength, bytes.position)
        if (totalLength == 0) return
        if (VERBOSE) {
            println("  verify...")
        }
        val baos = ByteArrayOutputStream()
        bytes.writeTo(OutputStreamDataOutput(baos))
        val actual = baos.toByteArray()
        assertEquals(totalLength, actual.size)
        for (i in 0 until totalLength) {
            assertEquals(expected[i], actual[i], "byte @ index=$i")
        }
    }
}

