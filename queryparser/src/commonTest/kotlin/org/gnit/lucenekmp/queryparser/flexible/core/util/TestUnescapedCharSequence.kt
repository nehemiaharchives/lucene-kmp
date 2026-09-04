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
package org.gnit.lucenekmp.queryparser.flexible.core.util

import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestUnescapedCharSequence : LuceneTestCase() {

    @Test
    fun testToStringEscaped() {
        val chars = charArrayOf('a', 'b', 'c', '\\', 'e')
        val wasEscaped = booleanArrayOf(false, true, true, false, false)
        val sequence = UnescapedCharSequence(chars, wasEscaped, 0, chars.size)
        assertEquals("a\\b\\c\\\\e", sequence.toStringEscaped())
        assertFalse(sequence.wasEscaped(0))
        assertTrue(sequence.wasEscaped(1))
    }

    @Test
    fun testToStringEscapedWithEnabledChars() {
        val chars = charArrayOf('a', 'b', 'c', '?', '*')
        val wasEscaped = booleanArrayOf(true, true, true, true, true)
        val sequence = UnescapedCharSequence(chars, wasEscaped, 0, chars.size)
        assertEquals("abc\\?\\*", sequence.toStringEscaped(wildcardChars))
    }

    @Test
    fun testSubSequence() {
        val sequence = UnescapedCharSequence("abcdef")
        assertEquals("bc", sequence.subSequence(1, 3).toString())
    }

    @Test
    fun testToLowerCase() {
        val sequence = UnescapedCharSequence("ABC")
        assertEquals(
            "abc", UnescapedCharSequence.toLowerCase(sequence, Locale.getDefault()).toString()
        )
        assertFalse(sequence.wasEscaped(0))
        assertFalse(sequence.wasEscaped(1))
        assertFalse(sequence.wasEscaped(2))
    }

    companion object {
        private val wildcardChars = charArrayOf('*', '?')
    }
}
