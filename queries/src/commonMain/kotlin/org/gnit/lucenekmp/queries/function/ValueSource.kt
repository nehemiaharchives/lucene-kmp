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
import org.gnit.lucenekmp.search.DoubleValues
import org.gnit.lucenekmp.search.DoubleValuesSource
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.FieldComparator
import org.gnit.lucenekmp.search.FieldComparatorSource
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.LongValues
import org.gnit.lucenekmp.search.LongValuesSource
import org.gnit.lucenekmp.search.Pruning
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.search.SimpleFieldComparator
import org.gnit.lucenekmp.search.SortField

/**
 * Instantiates [FunctionValues] for a particular reader. 
 * Often used when creating a [FunctionQuery].
 */
abstract class ValueSource {
  companion object {
  /** Returns a new non-threadsafe context map. */
  fun newContext(searcher: IndexSearcher?): MutableMap<Any, Any?> {
    val context = HashMap<Any, Any?>()
    context.put("searcher", searcher)
    return context
  }
  fun fromDoubleValuesSource(`in`: DoubleValuesSource): ValueSource {
    return FromDoubleValuesSource(`in`)
  }
  }

  /**
   * Gets the values for this reader and the context that was previously passed to createWeight().
   * The values must be consumed in a forward docID manner, and you must call this method again to
   * iterate through the values again.
   */
  abstract fun getValues(context: MutableMap<Any, Any?>, readerContext: LeafReaderContext): FunctionValues 

  abstract override fun equals(o: Any?): Boolean 

  abstract override fun hashCode(): Int 

  /** description of field, used in explain() */
  abstract fun description(): String 

  override fun toString(): String {
    return description()
  }

  /**
   * Implementations should propagate createWeight to sub-ValueSources which can optionally store
   * weight info in the context. The context object will be passed to getValues() where this info
   * can be retrieved.
   */
  open fun createWeight(context: MutableMap<Any, Any?>, searcher: IndexSearcher): Unit  {}


  private class ScorableView(scores: DoubleValues?) : Scorable() {
    val scores: DoubleValues = scores ?: DoubleValues.EMPTY
    var docId = -1
    var scoresDocId = -1
    var score = 0f

    constructor(docId: Int, score: Float) : this(null) {
      this.docId = docId
      this.scoresDocId = docId
      this.score = score
    }

    override fun score(): Float  {
      // ensure we calculate the score at most once
      if (scoresDocId != docId) {
        scoresDocId = docId
        if (scores.advanceExact(docId)) {
          score = scores.doubleValue().toFloat()
        } else {
          score = 0f
        }
      }
      return score
    }
  }

  /** Expose this ValueSource as a LongValuesSource */
  open fun asLongValuesSource(): LongValuesSource {
    return WrappedLongValuesSource(this, null)
  }

  private class WrappedLongValuesSource(val `in`: ValueSource, val searcher: IndexSearcher?) : LongValuesSource() {

    override fun getValues(ctx: LeafReaderContext, scores: DoubleValues?): LongValues  {
      val context = newContext(searcher)

      var scorer = ScorableView(scores)
      context.put("scorer", scorer)

      val fv = `in`.getValues(context, ctx)
      return object : LongValues() {

        override fun longValue(): Long  {
          return fv.longVal(scorer.docId)
        }

        override fun advanceExact(doc: Int): Boolean  {
          scorer.docId = doc
          return fv.exists(doc)
        }
      }
    }

    override fun isCacheable(ctx: LeafReaderContext): Boolean {
      return false
    }

    override fun needsScores(): Boolean {
      return false
    }

    override fun equals(o: Any?): Boolean {
      if (this === o) return true
      if (o !is WrappedLongValuesSource) return false
      val that = o
      return `in` == that.`in`
    }

    override fun hashCode(): Int {
      return 31 + `in`.hashCode()
    }

    override fun toString(): String {
      return `in`.toString()
    }

    override fun rewrite(searcher: IndexSearcher): LongValuesSource  {
      return WrappedLongValuesSource(`in`, searcher)
    }
  }

  /** Expose this ValueSource as a DoubleValuesSource */
  open fun asDoubleValuesSource(): DoubleValuesSource {
    return WrappedDoubleValuesSource(this, null)
  }

  private class WrappedDoubleValuesSource(val `in`: ValueSource, val searcher: IndexSearcher?) : DoubleValuesSource() {

    override fun getValues(ctx: LeafReaderContext, scores: DoubleValues?): DoubleValues  {
      val context = newContext(searcher)

      var scorer = ScorableView(scores)
      context.put("scorer", scorer)

      val fv = `in`.getValues(context, ctx)
      return object : DoubleValues() {

        override fun doubleValue(): Double  {
          return fv.doubleVal(scorer.docId)
        }

        override fun advanceExact(doc: Int): Boolean {
          scorer.docId = doc
          // ValueSource will return values even if exists() is false, generally a default
          // of some kind.  To preserve this behaviour with the iterator, we need to always
          // return 'true' here.
          return true
        }
      }
    }

    override fun needsScores(): Boolean {
      return true // be on the safe side
    }

    override fun isCacheable(ctx: LeafReaderContext): Boolean {
      return false
    }

    override fun explain(ctx: LeafReaderContext, docId: Int, scoreExplanation: Explanation): Explanation  {
      val context = newContext(searcher)
      context.put("scorer", ScorableView(docId, scoreExplanation.value.toFloat()))
      val fv = `in`.getValues(context, ctx)
      return fv.explain(docId)
    }

    override fun rewrite(searcher: IndexSearcher): DoubleValuesSource  {
      return WrappedDoubleValuesSource(`in`, searcher)
    }

    override fun equals(o: Any?): Boolean {
      if (this === o) return true
      if (o !is WrappedDoubleValuesSource) return false
      val that = o
      return `in` == that.`in`
    }

    override fun hashCode(): Int {
      return 31 + `in`.hashCode()
    }

    override fun toString(): String {
      return `in`.toString()
    }
  }



  private class FromDoubleValuesSource(val `in`: DoubleValuesSource) : ValueSource() {

    override fun getValues(context: MutableMap<Any, Any?>, readerContext: LeafReaderContext): FunctionValues  {
      val scorer = context["scorer"] as Scorable?
      val scores = if (scorer == null) null else DoubleValuesSource.fromScorer(scorer)

      val searcher = context["searcher"] as IndexSearcher?
      val inner: DoubleValues
      if (searcher != null) {
        inner = `in`.rewrite(searcher).getValues(readerContext, scores)
      } else {
        inner = `in`.getValues(readerContext, scores)
      }

      return object : FunctionValues() {
        override fun toString(doc: Int): String  {
          return `in`.toString()
        }

        override fun floatVal(doc: Int): Float  {
          if (inner.advanceExact(doc) == false) {
            return 0f
          }
          return inner.doubleValue().toFloat()
        }

        override fun doubleVal(doc: Int): Double  {
          if (inner.advanceExact(doc) == false) {
            return 0.0
          }
          return inner.doubleValue()
        }

        override fun exists(doc: Int): Boolean  {
          return inner.advanceExact(doc)
        }
      }
    }

    override fun equals(o: Any?): Boolean {
      if (this === o) return true
      if (o !is FromDoubleValuesSource) return false
      val that = o
      return `in` == that.`in`
    }

    override fun hashCode(): Int {
      return 31 + `in`.hashCode()
    }

    override fun description(): String {
      return `in`.toString()
    }
  }

  //
  // Sorting by function
  //

  /**
   * EXPERIMENTAL: This method is subject to change.
   *
   * Get the SortField for this ValueSource. Uses the [.getValues] to populate the SortField.
   *
   * @param reverse true if this is a reverse sort.
   * @return The [org.gnit.lucenekmp.search.SortField] for the ValueSource
   */
  open fun getSortField(reverse: Boolean): SortField {
    return ValueSourceSortField(reverse)
  }

  inner class ValueSourceSortField(reverse: Boolean) : SortField(description(), SortField.Type.REWRITEABLE, reverse) {

    override fun rewrite(searcher: IndexSearcher): SortField  {
      val context = newContext(searcher)
      createWeight(context, searcher)
      return SortField(field, ValueSourceComparatorSource(context), reverse)
    }
  }

  inner class ValueSourceComparatorSource(private val context: MutableMap<Any, Any?>) : FieldComparatorSource() {

    override fun newComparator(fieldname: String, numHits: Int, pruning: Pruning, reversed: Boolean): FieldComparator<Double> {
      return ValueSourceComparator(context, numHits)
    }
  }

  /**
   * Implement a [org.gnit.lucenekmp.search.FieldComparator] that works off of the [FunctionValues] for a ValueSource instead of the normal Lucene FieldComparator that works off
   * of a FieldCache.
   */
  inner class ValueSourceComparator(private val fcontext: MutableMap<Any, Any?>, numHits: Int) : SimpleFieldComparator<Double>() {
    private val values = DoubleArray(numHits)
    private lateinit var docVals: FunctionValues
    private var bottom = 0.0
    private var topValue = 0.0

    override fun compare(slot1: Int, slot2: Int): Int {
      return values[slot1].compareTo(values[slot2])
    }

    override fun compareBottom(doc: Int): Int  {
      return bottom.compareTo(docVals.doubleVal(doc))
    }

    override fun copy(slot: Int, doc: Int): Unit  {
      values[slot] = docVals.doubleVal(doc)
    }

    override fun doSetNextReader(context: LeafReaderContext): Unit  {
      docVals = getValues(fcontext, context)
    }

    override fun setBottom(bottom: Int): Unit {
      this.bottom = values[bottom]
    }

    override fun setTopValue(value: Double?): Unit {
      this.topValue = value!!
    }

    override fun value(slot: Int): Double {
      return values[slot]
    }

    override fun compareTop(doc: Int): Int  {
      val docValue = docVals.doubleVal(doc)
      return topValue.compareTo(docValue)
    }
  }
}
