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
package org.gnit.lucenekmp.facet.taxonomy.writercache

import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.CodingErrorAction
import org.gnit.lucenekmp.facet.FacetTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCharBlockArray : FacetTestCase() {
    @Test
    fun testArray() {
        val array = CharBlockArray()
        val builder = StringBuilder()

        val n: Int
        if (TEST_NIGHTLY) {
            n = 100 * 1000
        } else {
            n = 1000
        }

        val buffer = ByteArray(50)

        for (i in 0 until n) {
            random().nextBytes(buffer)
            val size = 1 + random().nextInt(50)
            // This test is turning random bytes into a string,
            // this is asking for trouble.
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .onMalformedInput(CodingErrorAction.REPLACE)
            val s = decoder.decode(ByteBuffer.wrap(buffer, 0, size)).toString()
            array.append(s)
            builder.append(s)
        }

        for (i in 0 until n) {
            random().nextBytes(buffer)
            val size = 1 + random().nextInt(50)
            // This test is turning random bytes into a string,
            // this is asking for trouble.
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .onMalformedInput(CodingErrorAction.REPLACE)
            val s = decoder.decode(ByteBuffer.wrap(buffer, 0, size)).toString()
            array.append(s as CharSequence)
            builder.append(s)
        }

        for (i in 0 until n) {
            random().nextBytes(buffer)
            val size = 1 + random().nextInt(50)
            // This test is turning random bytes into a string,
            // this is asking for trouble.
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .onMalformedInput(CodingErrorAction.REPLACE)
            val s = decoder.decode(ByteBuffer.wrap(buffer, 0, size)).toString()
            for (j in s.indices) {
                array.append(s[j])
            }
            builder.append(s)
        }

        assertEqualsInternal("GrowingCharArray<->StringBuilder mismatch.", builder, array)
    }

    companion object {
        private fun assertEqualsInternal(msg: String, expected: StringBuilder, actual: CharBlockArray) {
            assertEquals(expected.length, actual.length, msg)
            for (i in 0 until expected.length) {
                assertEquals(expected[i], actual[i], msg)
            }
        }
    }
}
