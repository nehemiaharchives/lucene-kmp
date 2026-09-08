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

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

// @Monster("uses lots of space and takes a few minutes")
class Test2GBCharBlockArray : LuceneTestCase() {
    @Test
    fun test2GBChars() {
        // Kotlin common has no JUnit Monster filter; preserve its opt-in behavior explicitly.
        if (!TEST_MONSTER) return
        val blockSize = 32768
        val array = CharBlockArray(blockSize)

        val size = TestUtil.nextInt(random(), 20000, 40000)

        val chars = CharArray(size)
        var count = 0
        while (true) {
            count++
            try {
                array.append(chars, 0, size)
            } catch (ise: IllegalStateException) {
                assertTrue(count.toLong() * size + blockSize > Int.MAX_VALUE)
                break
            }
            assertFalse(
                count.toLong() * size > Int.MAX_VALUE,
                "appended ${count.toLong() * size - Int.MAX_VALUE} characters beyond Integer.MAX_VALUE!"
            )
        }
    }
}
