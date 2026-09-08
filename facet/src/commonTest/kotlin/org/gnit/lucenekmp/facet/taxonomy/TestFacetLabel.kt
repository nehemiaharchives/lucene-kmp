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
package org.gnit.lucenekmp.facet.taxonomy

import org.gnit.lucenekmp.facet.FacetField
import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.sortedset.SortedSetDocValuesFacetField
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class TestFacetLabel : FacetTestCase() {
  @Test
  fun testBasic() {
    assertEquals(0, FacetLabel().length)
    assertEquals(1, FacetLabel("hello").length)
    assertEquals(2, FacetLabel("hello", "world").length)
  }

  @Test
  fun testToString() {
    // When the category is empty, we expect an empty string
    assertEquals("FacetLabel: []", FacetLabel().toString())
    // one category
    assertEquals("FacetLabel: [hello]", FacetLabel("hello").toString())
    // more than one category
    assertEquals("FacetLabel: [hello, world]", FacetLabel("hello", "world").toString())
  }

  @Test
  fun testGetComponent() {
    val components = Array(atLeast(10)) { "" }
    for (i in components.indices) {
      components[i] = i.toString()
    }
    var cp = FacetLabel(*components)
    for (i in components.indices) {
      assertEquals(i, cp.components[i].toInt())
    }
  }

  @Test
  fun testDefaultConstructor() {
    // test that the default constructor (no parameters) currently
    // defaults to creating an object with a 0 initial capacity.
    // If we change this default later, we also need to change this
    // test.
    var p = FacetLabel()
    assertEquals(0, p.length)
    assertEquals("FacetLabel: []", p.toString())
  }

  @Test
  fun testSubPath() {
    var p = FacetLabel("hi", "there", "man")
    assertEquals(p.length, 3)

    var p1 = p.subpath(2)
    assertEquals(2, p1.length)
    assertEquals("FacetLabel: [hi, there]", p1.toString())

    p1 = p.subpath(1)
    assertEquals(1, p1.length)
    assertEquals("FacetLabel: [hi]", p1.toString())

    p1 = p.subpath(0)
    assertEquals(0, p1.length)
    assertEquals("FacetLabel: []", p1.toString())

    // with all the following lengths, the prefix should be the whole path
    val lengths = intArrayOf(3, -1, 4)
    for (i in lengths.indices) {
      p1 = p.subpath(lengths[i])
      assertEquals(3, p1.length)
      assertEquals("FacetLabel: [hi, there, man]", p1.toString())
      assertEquals(p, p1)
    }
  }

  
  @Test
  fun testEquals() {
    assertEquals(FacetLabel(), FacetLabel())
    assertFalse(FacetLabel().equals(FacetLabel("hi")))
    assertFalse(FacetLabel().equals(3))
    assertEquals(FacetLabel("hello", "world"), FacetLabel("hello", "world"))
  }

  @Test
  fun testHashCode() {
    assertEquals(FacetLabel().hashCode(), FacetLabel().hashCode())
    assertFalse(FacetLabel().hashCode() == FacetLabel("hi").hashCode())
    assertEquals(
        FacetLabel("hello", "world").hashCode(), FacetLabel("hello", "world").hashCode())
  }

  @Test
  fun testLongHashCode() {
    assertEquals(FacetLabel().longHashCode(), FacetLabel().longHashCode())
    assertFalse(FacetLabel().longHashCode() == FacetLabel("hi").longHashCode())
    assertEquals(
        FacetLabel("hello", "world").longHashCode(),
        FacetLabel("hello", "world").longHashCode())
  }

  @Test
  fun testArrayConstructor() {
    var p = FacetLabel("hello", "world", "yo")
    assertEquals(3, p.length)
    assertEquals("FacetLabel: [hello, world, yo]", p.toString())
  }

  @Test
  fun testCompareTo() {
    var p = FacetLabel("a", "b", "c", "d")
    var pother = FacetLabel("a", "b", "c", "d")
    assertEquals(0, pother.compareTo(p))
    assertEquals(0, p.compareTo(pother))
    pother = FacetLabel()
    assertTrue(pother.compareTo(p) < 0)
    assertTrue(p.compareTo(pother) > 0)
    pother = FacetLabel("a", "b_", "c", "d")
    assertTrue(pother.compareTo(p) > 0)
    assertTrue(p.compareTo(pother) < 0)
    pother = FacetLabel("a", "b", "c")
    assertTrue(pother.compareTo(p) < 0)
    assertTrue(p.compareTo(pother) > 0)
    pother = FacetLabel("a", "b", "c", "e")
    assertTrue(pother.compareTo(p) > 0)
    assertTrue(p.compareTo(pother) < 0)
  }

    @Test
    fun testEmptyNullComponents() {
        // LUCENE-4724: CategoryPath should not allow empty or null components
        val components_tests = arrayOf(
            arrayOf("", "test"), // empty in the beginning
            arrayOf("test", ""), // empty in the end
            arrayOf("test", "", "foo"), // empty in the middle
            arrayOf(null, "test"), // null at the beginning
            arrayOf("test", null), // null in the end
            arrayOf("test", null, "foo"), // null in the middle
        )
        // empty or null components should not be allowed.
        for (components in components_tests) {
            assertFailsWith<IllegalArgumentException> { FacetLabel(*components) }
            assertFailsWith<IllegalArgumentException> { FacetField("dim", *components) }
            assertFailsWith<IllegalArgumentException> { AssociationFacetField(BytesRef(), "dim", *components) }
            assertFailsWith<IllegalArgumentException> { IntAssociationFacetField(17, "dim", *components) }
            assertFailsWith<IllegalArgumentException> { FloatAssociationFacetField(17.0f, "dim", *components) }
        }
        assertFailsWith<IllegalArgumentException> { FacetField(null, "abc") }
        assertFailsWith<IllegalArgumentException> { FacetField("", "abc") }
        assertFailsWith<IllegalArgumentException> { IntAssociationFacetField(17, null, "abc") }
        assertFailsWith<IllegalArgumentException> { IntAssociationFacetField(17, "", "abc") }
        assertFailsWith<IllegalArgumentException> { FloatAssociationFacetField(17.0f, null, "abc") }
        assertFailsWith<IllegalArgumentException> { FloatAssociationFacetField(17.0f, "", "abc") }
        assertFailsWith<IllegalArgumentException> { AssociationFacetField(BytesRef(), null, "abc") }
        assertFailsWith<IllegalArgumentException> { AssociationFacetField(BytesRef(), "", "abc") }
        assertFailsWith<IllegalArgumentException> { SortedSetDocValuesFacetField(null, "abc") }
        assertFailsWith<IllegalArgumentException> { SortedSetDocValuesFacetField("", "abc") }
        assertFailsWith<IllegalArgumentException> { SortedSetDocValuesFacetField("dim", "") }
    }

    @Test
    fun testLongPath() {
        var bigComp: String? = null
        while (true) {
            val len = FacetLabel.MAX_CATEGORY_PATH_LENGTH
            bigComp = TestUtil.randomSimpleString(random(), len, len)
            if (bigComp.indexOf('\u001f') != -1) {
                continue
            }
            break
        }
        // long paths should not be allowed
        val longPath = bigComp
        assertFailsWith<IllegalArgumentException> { FacetLabel("dim", longPath) }
    }
}
