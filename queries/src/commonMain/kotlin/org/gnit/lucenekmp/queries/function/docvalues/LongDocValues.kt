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
package org.gnit.lucenekmp.queries.function.docvalues

import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.queries.function.FunctionValues
import org.gnit.lucenekmp.queries.function.ValueSource
import org.gnit.lucenekmp.queries.function.ValueSourceScorer
import org.gnit.lucenekmp.util.mutable.MutableValue
import org.gnit.lucenekmp.util.mutable.MutableValueLong

/**
 * Abstract [FunctionValues] implementation which supports retrieving long values.
 * Implementations can control how the long values are loaded through [.longVal]}
 */
abstract class LongDocValues(protected val vs: ValueSource) : FunctionValues() {
  override fun byteVal(doc: Int): Byte  {
    return longVal(doc).toByte()
  }

  override fun shortVal(doc: Int): Short  {
    return longVal(doc).toShort()
  }

  override fun floatVal(doc: Int): Float  {
    return longVal(doc).toFloat()
  }

  override fun intVal(doc: Int): Int  {
    return longVal(doc).toInt()
  }

  abstract override fun longVal(doc: Int): Long 

  override fun doubleVal(doc: Int): Double  {
    return longVal(doc).toDouble()
  }

  override fun boolVal(doc: Int): Boolean  {
    return longVal(doc) != 0L
  }

  override fun strVal(doc: Int): String?  {
    return longVal(doc).toString()
  }

  override fun objectVal(doc: Int): Any?  {
    return if (exists(doc)) longVal(doc) else null
  }

  override fun toString(doc: Int): String  {
    return vs.description() + '=' + strVal(doc)
  }

  protected open fun externalToLong(extVal: String): Long {
    return extVal.toLong()
  }

  override fun getRangeScorer(readerContext: LeafReaderContext, lowerVal: String?, upperVal: String?, includeLower: Boolean, includeUpper: Boolean): ValueSourceScorer {
    var lower: Long
    var upper: Long

    // instead of using separate comparison functions, adjust the endpoints.

    if (lowerVal == null) {
      lower = Long.MIN_VALUE
    } else {
      lower = externalToLong(lowerVal)
      if (!includeLower && lower < Long.MAX_VALUE) lower++
    }

    if (upperVal == null) {
      upper = Long.MAX_VALUE
    } else {
      upper = externalToLong(upperVal)
      if (!includeUpper && upper > Long.MIN_VALUE) upper--
    }

    var ll = lower
    var uu = upper

    return object : ValueSourceScorer(readerContext, this@LongDocValues) {
      override fun matches(doc: Int): Boolean  {
        if (!exists(doc)) return false
        val value = longVal(doc)
        return value >= ll && value <= uu
      }
    }
  }

  override fun getValueFiller(): ValueFiller {
    return object : ValueFiller() {
      private val mval = MutableValueLong()

      override fun getValue(): MutableValue {
        return mval
      }

      override fun fillValue(doc: Int): Unit  {
        mval.value = longVal(doc)
        mval.exists = exists(doc)
      }
    }
  }
}
