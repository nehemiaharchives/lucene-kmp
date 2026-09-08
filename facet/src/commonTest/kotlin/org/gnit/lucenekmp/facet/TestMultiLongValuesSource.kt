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

import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.search.LongValues
import org.gnit.lucenekmp.search.LongValuesSource

class TestMultiLongValuesSource : MultiValuesSourceTestCase() {

  @Test
  fun testRandom() {
    var valuesSource: MultiLongValuesSource

    valuesSource = MultiLongValuesSource.fromIntField("single_int")
    assertNotNull(valuesSource)
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "single_int")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc())
    }

    valuesSource = MultiLongValuesSource.fromLongField("single_long")
    assertNotNull(valuesSource)
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "single_long")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc())
    }

    valuesSource = MultiLongValuesSource.fromIntField("multi_int")
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "multi_int")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc())
    }

    valuesSource = MultiLongValuesSource.fromLongField("multi_long")
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "multi_long")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc())
    }
  }

  @Test
  fun testFromSingleValued() {
    var valuesSource: MultiLongValuesSource
    var singleton: LongValuesSource?

    valuesSource =
        MultiLongValuesSource.fromSingleValued(LongValuesSource.fromIntField("single_int"))
    singleton = MultiLongValuesSource.unwrapSingleton(valuesSource)
    assertNotNull(singleton)
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "single_int")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc())

      var singletonDv = DocValues.getNumeric(ctx.reader(), "single_int")
      var singletonVals = singleton.getValues(ctx, null)
      validateFieldBasedSource(singletonDv, singletonVals, ctx.reader().maxDoc())
    }

    valuesSource =
        MultiLongValuesSource.fromSingleValued(LongValuesSource.fromLongField("single_long"))
    singleton = MultiLongValuesSource.unwrapSingleton(valuesSource)
    assertNotNull(singleton)
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "single_long")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc())

      var singletonDv = DocValues.getNumeric(ctx.reader(), "single_long")
      var singletonVals = singleton.getValues(ctx, null)
      validateFieldBasedSource(singletonDv, singletonVals, ctx.reader().maxDoc())
    }
  }

  @Test
  fun testToDouble() {
    var valuesSource =
        MultiLongValuesSource.fromLongField("multi_long").toMultiDoubleValuesSource()
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "multi_long")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc())
    }
  }

  @Test
  fun testCacheable() {
    var valuesSource = MultiLongValuesSource.fromLongField("multi_long")
    for (ctx in searcher.indexReader.leaves()) {
      assertEquals(DocValues.isCacheable(ctx, "multi_long"), valuesSource.isCacheable(ctx))
    }
  }

  @Test
  fun testEqualsAndHashcode() {
    var valuesSource1 = MultiLongValuesSource.fromLongField("multi_long")
    var valuesSource2 = MultiLongValuesSource.fromLongField("multi_long")
    var valuesSource3 = MultiLongValuesSource.fromLongField("multi_int")
    assertEquals(valuesSource1, valuesSource2)
    assertNotEquals(valuesSource1, valuesSource3)
    assertEquals(valuesSource1.hashCode(), valuesSource2.hashCode())
    assertNotEquals(valuesSource1.hashCode(), valuesSource3.hashCode())

    valuesSource1 =
        MultiLongValuesSource.fromSingleValued(LongValuesSource.fromLongField("single_long"))
    valuesSource2 =
        MultiLongValuesSource.fromSingleValued(LongValuesSource.fromLongField("single_long"))
    valuesSource3 =
        MultiLongValuesSource.fromSingleValued(LongValuesSource.fromLongField("single_int"))
    assertEquals(valuesSource1, valuesSource2)
    assertNotEquals(valuesSource1, valuesSource3)
    assertEquals(valuesSource1.hashCode(), valuesSource2.hashCode())
    assertNotEquals(valuesSource1.hashCode(), valuesSource3.hashCode())

    var singleton1 = MultiLongValuesSource.unwrapSingleton(valuesSource1)
    var singleton2 = MultiLongValuesSource.unwrapSingleton(valuesSource2)
    var singleton3 = MultiLongValuesSource.unwrapSingleton(valuesSource3)
    assertEquals(singleton1, singleton2)
    assertNotEquals(singleton1, singleton3)
    assertEquals(singleton1.hashCode(), singleton2.hashCode())
    assertNotEquals(singleton1.hashCode(), singleton3.hashCode())

    var doubleValuesSource1 = valuesSource1.toMultiDoubleValuesSource()
    var doubleValuesSource2 = valuesSource2.toMultiDoubleValuesSource()
    var doubleValuesSource3 = valuesSource3.toMultiDoubleValuesSource()
    assertEquals(doubleValuesSource1, doubleValuesSource2)
    assertNotEquals(doubleValuesSource1, doubleValuesSource3)
    assertEquals(doubleValuesSource1.hashCode(), doubleValuesSource2.hashCode())
    assertNotEquals(doubleValuesSource1.hashCode(), doubleValuesSource3.hashCode())
  }
}
