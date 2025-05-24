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

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestBitUtil : LuceneTestCase() {

    @Test
    fun testIsZeroOrPowerOfTwo() {
        assertTrue(BitUtil.isZeroOrPowerOfTwo(0))
        for (shift in 0..31) {
            assertTrue(BitUtil.isZeroOrPowerOfTwo(1 shl shift))
        }
        assertFalse(BitUtil.isZeroOrPowerOfTwo(3))
        assertFalse(BitUtil.isZeroOrPowerOfTwo(5))
        assertFalse(BitUtil.isZeroOrPowerOfTwo(6))
        assertFalse(BitUtil.isZeroOrPowerOfTwo(7))
        assertFalse(BitUtil.isZeroOrPowerOfTwo(9))
        assertFalse(BitUtil.isZeroOrPowerOfTwo(Int.MAX_VALUE))
        assertFalse(BitUtil.isZeroOrPowerOfTwo(Int.MAX_VALUE + 2))
        assertFalse(BitUtil.isZeroOrPowerOfTwo(-1))
    }
}
