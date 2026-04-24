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

import org.gnit.lucenekmp.jdkport.TreeSet
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestPrefixCodedTerms : LuceneTestCase() {

    @Test
    fun testEmpty() {
        val b = PrefixCodedTerms.Builder()
        val pb = b.finish()
        val iter = pb.iterator()
        assertNull(iter.next())
    }

    @Test
    fun testOne() {
        val term = Term("foo", "bogus")
        val b = PrefixCodedTerms.Builder()
        b.add(term)
        val pb = b.finish()
        val iter = pb.iterator()
        assertNotNull(iter.next())
        assertEquals("foo", iter.field())
        assertEquals("bogus", iter.bytes.utf8ToString())
        assertNull(iter.next())
    }

    @Test
    fun testRandom() {
        val terms = TreeSet<Term>()
        val nterms = atLeast(10000)
        for (i in 0..<nterms) {
            val term =
                Term(
                    TestUtil.randomUnicodeString(random(), 2),
                    TestUtil.randomUnicodeString(random())
                )
            terms.add(term)
        }

        val b = PrefixCodedTerms.Builder()
        for (ref in terms) {
            b.add(ref)
        }
        val pb = b.finish()

        val iter = pb.iterator()
        val expected = terms.iterator()
        assertEquals(expected = terms.size.toLong(), actual = pb.size())
        // System.out.println("TEST: now iter");
        while (iter.next() != null) {
            assertTrue(expected.hasNext())
            assertEquals(expected.next(), Term(iter.field()!!, iter.bytes))
        }

        assertFalse(expected.hasNext())
    }
}
