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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertFailsWith

class TestLongsRef : LuceneTestCase() {

    @Test
    fun testEmpty() {
        val i = LongsRef()
        assertSame(LongsRef.EMPTY_LONGS, i.longs)
        assertEquals(0, i.offset)
        assertEquals(0, i.length)
    }

    @Test
    fun testFromLongs() {
        val longs = longArrayOf(1L, 2L, 3L, 4L)
        val i = LongsRef(longs, 0, 4)
        assertSame(longs, i.longs)
        assertEquals(0, i.offset)
        assertEquals(4, i.length)

        val i2 = LongsRef(longs, 1, 3)
        assertEquals(LongsRef(longArrayOf(2L, 3L, 4L), 0, 3), i2)

        assertFalse(i == i2)
    }

    @Test
    fun testInvalidDeepCopy() {
        val from = LongsRef(longArrayOf(1L, 2L), 0, 2)
        from.offset += 1 // now invalid
        assertFailsWith<IndexOutOfBoundsException> { LongsRef.deepCopyOf(from) }
    }
}
