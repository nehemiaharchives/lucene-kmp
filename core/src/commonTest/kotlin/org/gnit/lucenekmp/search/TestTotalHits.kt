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
package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomizedTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class TestTotalHits : LuceneTestCase() {

  @Suppress("SENSELESS_COMPARISON")
  @Test
  fun testEqualsAndHashcode() {
    val totalHits1 = randomTotalHits()
    assertFalse(totalHits1.equals(null))
    assertFalse(totalHits1.equals(totalHits1.value))
    assertEquals(totalHits1, totalHits1)
    assertEquals(totalHits1.hashCode(), totalHits1.hashCode())

    val totalHits2 = TotalHits(totalHits1.value, totalHits1.relation)
    assertEquals(totalHits1, totalHits2)
    assertEquals(totalHits2, totalHits1)
    assertEquals(totalHits1.hashCode(), totalHits2.hashCode())

    val totalHits4 = randomTotalHits()
    if (totalHits4.value == totalHits1.value
      && totalHits4.relation == totalHits1.relation) {
      assertEquals(totalHits1, totalHits4)
      assertEquals(totalHits2, totalHits4)
      assertEquals(totalHits1.hashCode(), totalHits4.hashCode())
      assertEquals(totalHits2.hashCode(), totalHits4.hashCode())
    } else {
      assertNotEquals(totalHits1, totalHits4)
      assertNotEquals(totalHits2, totalHits4)
      assertNotEquals(totalHits1.hashCode(), totalHits4.hashCode())
      assertNotEquals(totalHits2.hashCode(), totalHits4.hashCode())
    }
  }

  companion object {
    private fun randomTotalHits(): TotalHits {
      val value = RandomizedTest.randomLongBetween(0, Long.MAX_VALUE)
      val relation = RandomizedTest.randomFrom(TotalHits.Relation.entries.toTypedArray())
      return TotalHits(value, relation)
    }
  }
}
