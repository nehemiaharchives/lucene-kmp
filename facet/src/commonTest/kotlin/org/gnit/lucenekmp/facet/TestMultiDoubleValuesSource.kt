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
import org.gnit.lucenekmp.search.DoubleValues
import org.gnit.lucenekmp.search.DoubleValuesSource

class TestMultiDoubleValuesSource : MultiValuesSourceTestCase() {

  @Test
  fun testRandom() {
    var valuesSource: MultiDoubleValuesSource

    valuesSource = MultiDoubleValuesSource.fromIntField("single_int")
    assertNotNull(valuesSource)
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "single_int")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc())
    }

    valuesSource = MultiDoubleValuesSource.fromLongField("single_long")
    assertNotNull(valuesSource)
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "single_long")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc())
    }

    valuesSource = MultiDoubleValuesSource.fromIntField("multi_int")
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "multi_int")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc())
    }

    valuesSource = MultiDoubleValuesSource.fromLongField("multi_long")
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "multi_long")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc())
    }

    valuesSource = MultiDoubleValuesSource.fromFloatField("single_float")
    assertNotNull(valuesSource)
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "single_float")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc(), false)
    }

    valuesSource = MultiDoubleValuesSource.fromDoubleField("single_double")
    assertNotNull(valuesSource)
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "single_double")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc(), true)
    }

    valuesSource = MultiDoubleValuesSource.fromFloatField("multi_float")
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "multi_float")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc(), false)
    }

    valuesSource = MultiDoubleValuesSource.fromDoubleField("multi_double")
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "multi_double")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc(), true)
    }
  }

  @Test
  fun testFromSingleValued() {
    var valuesSource: MultiDoubleValuesSource
    var singleton: DoubleValuesSource?

    valuesSource =
        MultiDoubleValuesSource.fromSingleValued(DoubleValuesSource.fromFloatField("single_float"))
    singleton = MultiDoubleValuesSource.unwrapSingleton(valuesSource)
    assertNotNull(singleton)
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "single_float")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc(), false)

      var singletonDv = DocValues.getNumeric(ctx.reader(), "single_float")
      var singletonVals = singleton.getValues(ctx, null)
      validateFieldBasedSource(singletonDv, singletonVals, ctx.reader().maxDoc(), false)
    }

    valuesSource =
        MultiDoubleValuesSource.fromSingleValued(
            DoubleValuesSource.fromDoubleField("single_double"))
    singleton = MultiDoubleValuesSource.unwrapSingleton(valuesSource)
    assertNotNull(singleton)
    for (ctx in reader.leaves()) {
      var docValues = DocValues.getSortedNumeric(ctx.reader(), "single_double")
      var values = valuesSource.getValues(ctx)
      validateFieldBasedSource(docValues, values, ctx.reader().maxDoc(), true)

      var singletonDv = DocValues.getNumeric(ctx.reader(), "single_double")
      var singletonVals = singleton.getValues(ctx, null)
      validateFieldBasedSource(singletonDv, singletonVals, ctx.reader().maxDoc(), true)
    }
  }

  @Test
  fun testCacheable() {
    var valuesSource = MultiDoubleValuesSource.fromDoubleField("multi_double")
    for (ctx in searcher.indexReader.leaves()) {
      assertEquals(DocValues.isCacheable(ctx, "multi_double"), valuesSource.isCacheable(ctx))
    }
  }

  @Test
  fun testEqualsAndHashcode() {
    var valuesSource1 = MultiDoubleValuesSource.fromLongField("multi_long")
    var valuesSource2 = MultiDoubleValuesSource.fromLongField("multi_long")
    var valuesSource3 = MultiDoubleValuesSource.fromLongField("multi_int")
    assertEquals(valuesSource1, valuesSource2)
    assertNotEquals(valuesSource1, valuesSource3)
    assertEquals(valuesSource1.hashCode(), valuesSource2.hashCode())
    assertNotEquals(valuesSource1.hashCode(), valuesSource3.hashCode())

    valuesSource1 =
        MultiDoubleValuesSource.fromSingleValued(DoubleValuesSource.fromLongField("single_long"))
    valuesSource2 =
        MultiDoubleValuesSource.fromSingleValued(DoubleValuesSource.fromLongField("single_long"))
    valuesSource3 =
        MultiDoubleValuesSource.fromSingleValued(DoubleValuesSource.fromLongField("single_int"))
    assertEquals(valuesSource1, valuesSource2)
    assertNotEquals(valuesSource1, valuesSource3)
    assertEquals(valuesSource1.hashCode(), valuesSource2.hashCode())
    assertNotEquals(valuesSource1.hashCode(), valuesSource3.hashCode())

    var singleton1 = MultiDoubleValuesSource.unwrapSingleton(valuesSource1)
    var singleton2 = MultiDoubleValuesSource.unwrapSingleton(valuesSource2)
    var singleton3 = MultiDoubleValuesSource.unwrapSingleton(valuesSource3)
    assertEquals(singleton1, singleton2)
    assertNotEquals(singleton1, singleton3)
    assertEquals(singleton1.hashCode(), singleton2.hashCode())
    assertNotEquals(singleton1.hashCode(), singleton3.hashCode())

    valuesSource1 = MultiDoubleValuesSource.fromField("single_long", { v -> v.toDouble() })
    valuesSource2 = MultiDoubleValuesSource.fromField("single_long", { v -> (-1 * v).toDouble() })
    valuesSource3 = MultiDoubleValuesSource.fromField("single_int", { v -> v.toDouble() })
    assertNotEquals(valuesSource1, valuesSource2)
    assertNotEquals(valuesSource1, valuesSource3)
    assertNotEquals(valuesSource1.hashCode(), valuesSource2.hashCode())
    assertNotEquals(valuesSource1.hashCode(), valuesSource3.hashCode())
  }
}
