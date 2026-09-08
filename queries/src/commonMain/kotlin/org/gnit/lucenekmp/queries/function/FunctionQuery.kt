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

import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Weight

/**
 * Returns a score for each document based on a ValueSource, often some function of the value of a
 * field.
 *
 * @see ValueSourceScorer
 * @lucene.experimental
 */
open class FunctionQuery
  /**
   * @param func defines the function to be used for scoring
   */
constructor(val func: ValueSource) : Query() {


  /**
   * @return The associated ValueSource
   */
  open fun getValueSource(): ValueSource {
    return func
  }

  /**
   * Creates FunctionQuery scorer instances
   *
   * @lucene.internal
   */
  protected inner class FunctionWeight(val searcher: IndexSearcher, val boost: Float) : Weight(this@FunctionQuery) {
    val context: MutableMap<Any, Any?> = ValueSource.newContext(searcher)
    init {
      func.createWeight(context, searcher)
    }

    override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier  {
      val scorer = AllScorer(context, this, boost)
      return DefaultScorerSupplier(scorer)
    }

    override fun isCacheable(ctx: LeafReaderContext): Boolean {
      return false
    }

    override fun explain(context: LeafReaderContext, doc: Int): Explanation  {
      return (scorer(context) as AllScorer).explain(doc)
    }
  }

  override fun visit(visitor: QueryVisitor): Unit {
    visitor.visitLeaf(this)
  }

  /**
   * Scores all documents, applying the function to each document
   *
   * @lucene.internal
   */
  protected inner class AllScorer(context: LeafReaderContext, val weight: FunctionWeight, val boost: Float) : Scorer() {
    val reader: IndexReader = context.reader()
    val maxDoc: Int = reader.maxDoc()
    val iterator: DocIdSetIterator = DocIdSetIterator.all(context.reader().maxDoc())
    val vals: FunctionValues = func.getValues(weight.context, context)

    override fun iterator(): DocIdSetIterator {
      return iterator
    }

    override fun docID(): Int {
      return iterator.docID()
    }

    override fun score(): Float  {
      val value = vals.floatVal(docID())
      if ((value >= 0) == false) { // this covers NaN as well since comparisons with NaN return false
        return 0f
      } else {
        return boost * value
      }
    }

    override fun getMaxScore(upTo: Int): Float  {
      return Float.POSITIVE_INFINITY
    }

    open fun explain(doc: Int): Explanation  {
      var expl = vals.explain(doc)
      if (expl.value.toFloat() < 0) {
        expl =
            Explanation.match(
                0, "truncated score, max of:", Explanation.match(0f, "minimum score"), expl)
      } else if (expl.value.toFloat().isNaN()) {
        expl =
            Explanation.match(
                0,
                "score, computed as (score == NaN ? 0 : score) since NaN is an illegal score from:",
                expl)
      }

      return Explanation.match(
          boost * expl.value.toFloat(),
          "FunctionQuery(" + func + "), product of:",
          vals.explain(doc),
          Explanation.match(weight.boost, "boost"))
    }
  }

  override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight  {
    return FunctionWeight(searcher, boost)
  }

  /** Prints a user-readable version of this query. */
  override fun toString(field: String?): String {
    return func.toString()
  }

  /** Returns true if `o` is equal to this. */
  override fun equals(other: Any?): Boolean {
    return sameClassAs(other) && func == (other as FunctionQuery).func
  }

  override fun hashCode(): Int {
    return classHash() xor func.hashCode()
  }
}
