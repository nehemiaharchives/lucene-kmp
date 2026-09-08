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
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.TwoPhaseIterator

/**
 * [Scorer] which returns the result of [FunctionValues.floatVal] as the score for
 * a document, and which filters out documents that don't match [.matches]. This Scorer
 * has a [TwoPhaseIterator]. This is similar to [FunctionQuery], with an added filter.
 *
 * Note: If the scores are needed, then the underlying value will probably be fetched/computed
 * twice -- once to filter and next to return the score. If that's non-trivial then consider
 * wrapping it in an implementation that will cache the current value.
 *
 * @see FunctionQuery
 * @lucene.experimental
 */
abstract class ValueSourceScorer protected constructor(readerContext: LeafReaderContext, protected val values: FunctionValues) : Scorer() {
  companion object {
    // Fixed cost for a single iteration of the TwoPhaseIterator instance
    private const val DEF_COST = 5
  }
  private val twoPhaseIterator: TwoPhaseIterator
  private val disi: DocIdSetIterator

  init {
    val approximation =
        DocIdSetIterator.all(readerContext.reader().maxDoc()) // no approximation!
    this.twoPhaseIterator =
        object : TwoPhaseIterator(approximation) {
          override fun matches(): Boolean  {
            return this@ValueSourceScorer.matches(approximation.docID())
          }

          override fun matchCost(): Float {
            return this@ValueSourceScorer.matchCost()
          }
        }
    this.disi = TwoPhaseIterator.asDocIdSetIterator(twoPhaseIterator)
  }

  /**
   * Override to decide if this document matches. It's called by [TwoPhaseIterator.matches].
   */
  abstract fun matches(doc: Int): Boolean 

  override fun iterator(): DocIdSetIterator {
    return disi
  }

  override fun twoPhaseIterator(): TwoPhaseIterator {
    return twoPhaseIterator
  }

  override fun docID(): Int {
    return disi.docID()
  }

  override fun score(): Float  {
    // (same as FunctionQuery, but no qWeight)  TODO consider adding configurable qWeight
    val score = values.floatVal(disi.docID())
    // Current Lucene priority queues can't handle NaN and -Infinity, so
    // map to -Float.MAX_VALUE. This conditional handles both -infinity
    // and NaN since comparisons with NaN are always false.
    return if (score > Float.NEGATIVE_INFINITY) score else -Float.MAX_VALUE
  }

  override fun getMaxScore(upTo: Int): Float  {
    return Float.POSITIVE_INFINITY
  }

  /**
   * Cost evaluation function which defines the cost of access for the TwoPhaseIterator for this
   * class This method should be overridden for specifying custom cost methods. Used by [TwoPhaseIterator.matchCost] for the instance owned by this class
   *
   * @return cost of access
   * @lucene.experimental
   */
  protected open fun matchCost(): Float {
    // Cost of iteration is fixed cost + cost exposed by delegated FunctionValues instance
    return DEF_COST + values.cost()
  }
}
