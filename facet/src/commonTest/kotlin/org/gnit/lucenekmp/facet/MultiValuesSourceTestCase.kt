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
package org.gnit.lucenekmp.facet

import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoubleDocValuesField
import org.gnit.lucenekmp.document.FloatDocValuesField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.search.DoubleValues
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.LongValues
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase

abstract class MultiValuesSourceTestCase : LuceneTestCase() {
  protected lateinit var dir: Directory
  protected lateinit var reader: IndexReader
  protected lateinit var searcher: IndexSearcher

  @BeforeTest
  fun setUp() {

    dir = newDirectory()
    var iw = RandomIndexWriter(random(), dir)

    var numDocs = TestUtil.nextInt(random(), 100, 1000)
    for (i in 0 until numDocs) {
      var doc = Document()

      if (random().nextInt(10) < 8) {
        doc.add(NumericDocValuesField("single_int", random().nextInt().toLong()))
      }
      if (random().nextInt(10) < 8) {
        doc.add(NumericDocValuesField("single_long", random().nextLong()))
      }
      if (random().nextInt(10) < 8) {
        doc.add(FloatDocValuesField("single_float", random().nextFloat()))
      }
      if (random().nextInt(10) < 8) {
        doc.add(DoubleDocValuesField("single_double", random().nextDouble()))
      }

      var limit = TestUtil.nextInt(random(), 0, 100)
      for (j in 0 until limit) {
        doc.add(SortedNumericDocValuesField("multi_int", random().nextInt().toLong()))
      }
      limit = TestUtil.nextInt(random(), 0, 100)
      for (j in 0 until limit) {
        doc.add(SortedNumericDocValuesField("multi_long", random().nextLong()))
      }
      limit = TestUtil.nextInt(random(), 0, 100)
      for (j in 0 until limit) {
        doc.add(
            SortedNumericDocValuesField(
                "multi_float", random().nextFloat().toRawBits().toLong()))
      }
      limit = TestUtil.nextInt(random(), 0, 100)
      for (j in 0 until limit) {
        doc.add(
            SortedNumericDocValuesField(
                "multi_double", random().nextDouble().toRawBits()))
      }

      iw.addDocument(doc)

      if (i % 100 == 0 && random().nextBoolean()) {
        iw.commit()
      }
    }

    reader = iw.reader
    iw.close()
    searcher = newSearcher(reader)
  }

  @AfterTest
  fun tearDown() {
    reader.close()
    dir.close()
  }

  protected fun validateFieldBasedSource(docValues: NumericDocValues, values: LongValues, maxDoc: Int) {
    for (doc in 0 until maxDoc) {
      var hasValues = docValues.advanceExact(doc)
      assertEquals(hasValues, values.advanceExact(doc))
      if (hasValues) {
        assertEquals(docValues.longValue(), values.longValue())
      }
    }
  }

  protected fun validateFieldBasedSource(docValues: SortedNumericDocValues, values: MultiLongValues, maxDoc: Int) {
    for (doc in 0 until maxDoc) {
      var hasValues = docValues.advanceExact(doc)
      assertEquals(hasValues, values.advanceExact(doc))
      if (hasValues) {
        var valueCount = docValues.docValueCount()
        assertEquals(valueCount.toLong(), values.getValueCount())
        for (i in 0 until valueCount) {
          assertEquals(docValues.nextValue(), values.nextValue())
        }
      }
    }
  }

  protected fun validateFieldBasedSource(docValues: NumericDocValues, values: DoubleValues, maxDoc: Int) {
    for (doc in 0 until maxDoc) {
      var hasValues = docValues.advanceExact(doc)
      assertEquals(hasValues, values.advanceExact(doc))
      if (hasValues) {
        assertEquals(docValues.longValue().toDouble(), values.doubleValue(), 0.00001)
      }
    }
  }

  protected fun validateFieldBasedSource(docValues: SortedNumericDocValues, values: MultiDoubleValues, maxDoc: Int) {
    for (doc in 0 until maxDoc) {
      var hasValues = docValues.advanceExact(doc)
      assertEquals(hasValues, values.advanceExact(doc))
      if (hasValues) {
        var valueCount = docValues.docValueCount()
        assertEquals(valueCount.toLong(), values.getValueCount())
        for (i in 0 until valueCount) {
          assertEquals(docValues.nextValue().toDouble(), values.nextValue(), 0.00001)
        }
      }
    }
  }

  protected fun validateFieldBasedSource(docValues: NumericDocValues, values: DoubleValues, maxDoc: Int, useDoublePrecision: Boolean) {
    for (doc in 0 until maxDoc) {
      var hasValues = docValues.advanceExact(doc)
      assertEquals(hasValues, values.advanceExact(doc))
      if (hasValues) {
        if (useDoublePrecision) {
          var asLong = values.doubleValue().toRawBits()
          assertEquals(docValues.longValue(), asLong)
        } else {
          var asInt = values.doubleValue().toFloat().toRawBits()
          assertEquals(docValues.longValue().toInt(), asInt)
        }
      }
    }
  }

  protected fun validateFieldBasedSource(docValues: SortedNumericDocValues, values: MultiDoubleValues, maxDoc: Int, useDoublePrecision: Boolean) {
    for (doc in 0 until maxDoc) {
      var hasValues = docValues.advanceExact(doc)
      assertEquals(hasValues, values.advanceExact(doc))
      if (hasValues) {
        var valueCount = docValues.docValueCount()
        assertEquals(valueCount.toLong(), values.getValueCount())
        for (i in 0 until valueCount) {
          if (useDoublePrecision) {
            var asLong = values.nextValue().toRawBits()
            assertEquals(docValues.nextValue(), asLong)
          } else {
            var asInt = values.nextValue().toFloat().toRawBits()
            assertEquals(docValues.nextValue(), asInt.toLong())
          }
        }
      }
    }
  }
}
