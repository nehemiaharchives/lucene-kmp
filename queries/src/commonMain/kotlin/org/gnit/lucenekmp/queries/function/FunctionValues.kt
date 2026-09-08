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
package org.gnit.lucenekmp.queries.function

import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.mutable.MutableValue
import org.gnit.lucenekmp.util.mutable.MutableValueFloat

/**
 * Represents field values as different types. Normally created via a [ValueSource] for a
 * particular field and reader.
 */

// FunctionValues is distinct from ValueSource because
// there needs to be an object created at query evaluation time that
// is not referenced by the query itself because:
// - Query objects should be MT safe
// - For caching, Query objects are often used as keys... you don't
//   want the Query carrying around big objects
abstract class FunctionValues {

  open fun byteVal(doc: Int): Byte  {
    throw UnsupportedOperationException()
  }

  open fun shortVal(doc: Int): Short  {
    throw UnsupportedOperationException()
  }

  open fun floatVal(doc: Int): Float  {
    throw UnsupportedOperationException()
  }

  open fun intVal(doc: Int): Int  {
    throw UnsupportedOperationException()
  }

  open fun longVal(doc: Int): Long  {
    throw UnsupportedOperationException()
  }

  open fun doubleVal(doc: Int): Double  {
    throw UnsupportedOperationException()
  }

  // TODO: should we make a termVal, returns BytesRef?
  open fun strVal(doc: Int): String?  {
    throw UnsupportedOperationException()
  }

  open fun boolVal(doc: Int): Boolean  {
    return intVal(doc) != 0
  }

  open fun floatVectorVal(doc: Int): FloatArray?  {
    throw UnsupportedOperationException()
  }

  open fun byteVectorVal(doc: Int): ByteArray?  {
    throw UnsupportedOperationException()
  }

  /**
   * returns the bytes representation of the string val - TODO: should this return the indexed raw
   * bytes not?
   */
  open fun bytesVal(doc: Int, target: BytesRefBuilder): Boolean  {
    val s = strVal(doc)
    if (s == null) {
      target.clear()
      return false
    }
    target.copyChars(s)
    return true
  }

  /** Native Java Object representation of the value */
  open fun objectVal(doc: Int): Any?  {
    // most FunctionValues are functions, so by default return a Float()
    return floatVal(doc)
  }

  /** Returns true if there is a value for this document */
  open fun exists(doc: Int): Boolean  {
    return true
  }

  /**
   * @param doc The doc to retrieve to sort ordinal for
   * @return the sort ordinal for the specified doc TODO: Maybe we can just use intVal for this...
   */
  open fun ordVal(doc: Int): Int  {
    throw UnsupportedOperationException()
  }

  /**
   * @return the number of unique sort ordinals this instance has
   */
  open fun numOrd(): Int {
    throw UnsupportedOperationException()
  }

  /**
   * An estimate of the expected cost to return a value for a document. It's intended to be used by
   * TwoPhaseIterator.matchCost implementations. Returns an expected cost in number of simple
   * operations like addition, multiplication, comparing two numbers and indexing an array. The
   * returned value must be positive.
   */
  open fun cost(): Float {
    return 100f
  }

  abstract fun toString(doc: Int): String 

  /**
   * Abstraction of the logic required to fill the value of a specified doc into a reusable [MutableValue]. Implementations of [FunctionValues] are encouraged to define their own
   * implementations of ValueFiller if their value is not a float.
   *
   * @lucene.experimental
   */
  abstract class ValueFiller {
    /** MutableValue will be reused across calls */
    abstract fun getValue(): MutableValue 

    /** MutableValue will be reused across calls. Returns true if the value exists. */
    abstract fun fillValue(doc: Int): Unit 
  }

  /**
   * @lucene.experimental
   */
  open fun getValueFiller(): ValueFiller {
    return object : ValueFiller() {
      private val mval = MutableValueFloat()

      override fun getValue(): MutableValue {
        return mval
      }

      override fun fillValue(doc: Int): Unit  {
        mval.value = floatVal(doc)
      }
    }
  }

  // For Functions that can work with multiple values from the same document.  This does not apply
  // to all functions
  open fun byteVal(doc: Int, vals: ByteArray): Unit  {
    throw UnsupportedOperationException()
  }

  open fun shortVal(doc: Int, vals: ShortArray): Unit  {
    throw UnsupportedOperationException()
  }

  open fun floatVal(doc: Int, vals: FloatArray): Unit  {
    throw UnsupportedOperationException()
  }

  open fun intVal(doc: Int, vals: IntArray): Unit  {
    throw UnsupportedOperationException()
  }

  open fun longVal(doc: Int, vals: LongArray): Unit  {
    throw UnsupportedOperationException()
  }

  open fun doubleVal(doc: Int, vals: DoubleArray): Unit  {
    throw UnsupportedOperationException()
  }

  // TODO: should we make a termVal, fills BytesRef[]?
  open fun strVal(doc: Int, vals: Array<String?>): Unit  {
    throw UnsupportedOperationException()
  }

  open fun explain(doc: Int): Explanation  {
    return Explanation.match(floatVal(doc), toString(doc))
  }

  /**
   * Yields a [Scorer] that matches all documents, and that which produces scores equal to
   * [.floatVal].
   */
  open fun getScorer(readerContext: LeafReaderContext): ValueSourceScorer {
    return object : ValueSourceScorer(readerContext, this@FunctionValues) {
      override fun matches(doc: Int): Boolean {
        return true
      }

      override fun matchCost(): Float {
        return 0f
      }
    }
  }

  /**
   * Yields a [Scorer] that matches documents with values between the specified range, and
   * that which produces scores equal to [.floatVal].
   */
  // A RangeValueSource can't easily be a ValueSource that takes another ValueSource
  // because it needs different behavior depending on the type of fields.  There is also
  // a setup cost - parsing and normalizing params, and doing a binary search on the StringIndex.
  // TODO: change "reader" to LeafReaderContext
  open fun getRangeScorer(readerContext: LeafReaderContext, lowerVal: String?, upperVal: String?, includeLower: Boolean, includeUpper: Boolean): ValueSourceScorer  {
    val lower: Float
    val upper: Float

    if (lowerVal == null) {
      lower = Float.NEGATIVE_INFINITY
    } else {
      lower = lowerVal.toFloat()
    }
    if (upperVal == null) {
      upper = Float.POSITIVE_INFINITY
    } else {
      upper = upperVal.toFloat()
    }

    val l = lower
    val u = upper

    if (includeLower && includeUpper) {
      return object : ValueSourceScorer(readerContext, this@FunctionValues) {
        override fun matches(doc: Int): Boolean  {
          if (!exists(doc)) return false
          val docVal = floatVal(doc)
          return docVal >= l && docVal <= u
        }
      }
    } else if (includeLower && !includeUpper) {
      return object : ValueSourceScorer(readerContext, this@FunctionValues) {
        override fun matches(doc: Int): Boolean  {
          if (!exists(doc)) return false
          val docVal = floatVal(doc)
          return docVal >= l && docVal < u
        }
      }
    } else if (!includeLower && includeUpper) {
      return object : ValueSourceScorer(readerContext, this@FunctionValues) {
        override fun matches(doc: Int): Boolean  {
          if (!exists(doc)) return false
          val docVal = floatVal(doc)
          return docVal > l && docVal <= u
        }
      }
    } else {
      return object : ValueSourceScorer(readerContext, this@FunctionValues) {
        override fun matches(doc: Int): Boolean  {
          if (!exists(doc)) return false
          val docVal = floatVal(doc)
          return docVal > l && docVal < u
        }
      }
    }
  }
}
