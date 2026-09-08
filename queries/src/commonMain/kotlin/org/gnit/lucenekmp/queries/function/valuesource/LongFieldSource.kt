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
package org.gnit.lucenekmp.queries.function.valuesource

import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.queries.function.FunctionValues
import org.gnit.lucenekmp.queries.function.docvalues.LongDocValues
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.SortField.Type

/**
 * Obtains long field values from [org.gnit.lucenekmp.index.LeafReader.getNumericDocValues] and
 * makes those values available as other numeric types, casting as needed.
 */
open class LongFieldSource(field: String) : FieldCacheSource(field) {
  override fun description(): String {
    return "long(" + field + ')'
  }

  open fun externalToLong(extVal: String): Long {
    return extVal.toLong()
  }

  open fun longToObject(value: Long): Any? {
    return value
  }

  open fun longToString(value: Long): String {
    return longToObject(value).toString()
  }

  override fun getSortField(reverse: Boolean): SortField {
    return SortField(field, Type.LONG, reverse)
  }

  override fun getValues(context: MutableMap<Any, Any?>, readerContext: LeafReaderContext): FunctionValues  {

    var arr = getNumericDocValues(context, readerContext)

    return object : LongDocValues(this@LongFieldSource) {
      var lastDocID = 0

      override fun longVal(doc: Int): Long  {
        if (exists(doc)) {
          return arr.longValue()
        } else {
          return 0L
        }
      }

      override fun exists(doc: Int): Boolean  {
        if (doc < lastDocID) {
          throw IllegalArgumentException(
              "docs were sent out-of-order: lastDocID=" + lastDocID + " vs docID=" + doc)
        }
        lastDocID = doc
        var curDocID = arr.docID()
        if (doc > curDocID) {
          curDocID = arr.advance(doc)
        }
        return doc == curDocID
      }

      override fun objectVal(doc: Int): Any?  {
        if (exists(doc)) {
          var value = longVal(doc)
          return longToObject(value)
        } else {
          return null
        }
      }

      override fun strVal(doc: Int): String?  {
        if (exists(doc)) {
          var value = longVal(doc)
          return longToString(value)
        } else {
          return null
        }
      }

      protected override fun externalToLong(extVal: String): Long {
        return this@LongFieldSource.externalToLong(extVal)
      }
    }
  }

  protected open fun getNumericDocValues(context: MutableMap<Any, Any?>, readerContext: LeafReaderContext): NumericDocValues  {
    return DocValues.getNumeric(readerContext.reader(), field)
  }

  override fun equals(o: Any?): Boolean {
    if (o == null || o::class != this::class) return false
    val other = o as LongFieldSource
    return super.equals(other)
  }

  override fun hashCode(): Int {
    var h = this::class.hashCode()
    h += super.hashCode()
    return h
  }
}
