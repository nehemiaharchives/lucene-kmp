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

import org.gnit.lucenekmp.facet.FacetsCollector.MatchingDocs
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.search.ConjunctionUtils
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.LongValuesSource
import org.gnit.lucenekmp.util.InPlaceMergeSorter
import org.gnit.lucenekmp.util.PriorityQueue
import org.gnit.lucenekmp.jdkport.assert

/**
 * [Facets] implementation that computes counts for all unique long values, more efficiently
 * counting small values (0-1023) using an int array, and switching to a `HashMap` for
 * values above 1023. Retrieve all facet counts, in value order, with [getAllChildrenSortByValue], or get all children with no ordering requirements with [getAllChildren], or get the topN values sorted by count with [getTopChildren].
 *
 * @lucene.experimental
 */
class LongValueFacetCounts : Facets {

  /** Used for all values that are >= 1K. */
  private var counts: IntArray? = null

  /** Used for all values that are < 1K. */
  private var hashCounts: HashMap<Long, Int>? = null

  /** Whether-or-not counters have been initialized. */
  private var initialized = false

  /** Field being counted. */
  private val field: String

  /**
   * Total value count. For single-value cases, this is the subset of hits that had a value for this
   * field.
   */
  private var totCount = 0

  /**
   * Create `LongValueFacetCounts`, using either single-valued [NumericDocValues] or
   * multi-valued [SortedNumericDocValues] from the specified field (depending on what has
   * been indexed).
   */
  constructor(field: String, hits: FacetsCollector) : this(field, null as LongValuesSource?, hits)

  /**
   * Create `LongValueFacetCounts`, using the provided [LongValuesSource] if non-null.
   * If `valueSource` is null, doc values from the provided `field` will be used.
   */
  constructor(field: String, valueSource: LongValuesSource?, hits: FacetsCollector) : super() {
    this.field = field
    if (valueSource != null) {
      count(valueSource, hits.getMatchingDocs())
    } else {
      count(field, hits.getMatchingDocs())
    }
  }

  /**
   * Create `LongValueFacetCounts`, using the provided [MultiLongValuesSource] if
   * non-null. If `valuesSource` is null, doc values from the provided `field` will be
   * used.
   */
  constructor(field: String, valuesSource: MultiLongValuesSource?, hits: FacetsCollector) : super() {
    this.field = field
    if (valuesSource != null) {
      val singleValues = MultiLongValuesSource.unwrapSingleton(valuesSource)
      if (singleValues != null) {
        count(singleValues, hits.getMatchingDocs())
      } else {
        count(valuesSource, hits.getMatchingDocs())
      }
    } else {
      count(field, hits.getMatchingDocs())
    }
  }

  /**
   * Counts all facet values for this reader. This produces the same result as computing facets on a
   * [org.gnit.lucenekmp.search.MatchAllDocsQuery], but is more efficient.
   */
  constructor(field: String, reader: IndexReader) : this(field, null as LongValuesSource?, reader)

  /**
   * Counts all facet values for the provided [LongValuesSource] if non-null. If `* valueSource` is null, doc values from the provided `field` will be used. This produces
   * the same result as computing facets on a [org.gnit.lucenekmp.search.MatchAllDocsQuery],
   * but is more efficient.
   */
  constructor(field: String, valueSource: LongValuesSource?, reader: IndexReader) : super() {
    this.field = field
    initializeCounters()
    if (valueSource != null) {
      countAll(reader, valueSource)
    } else {
      countAll(reader, field)
    }
  }

  /**
   * Counts all facet values for the provided [MultiLongValuesSource] if non-null. If `* valueSource` is null, doc values from the provided `field` will be used. This produces
   * the same result as computing facets on a [org.gnit.lucenekmp.search.MatchAllDocsQuery],
   * but is more efficient.
   */
  constructor(field: String, valuesSource: MultiLongValuesSource?, reader: IndexReader) : super() {
    this.field = field
    initializeCounters()
    if (valuesSource != null) {
      val singleValued = MultiLongValuesSource.unwrapSingleton(valuesSource)
      if (singleValued != null) {
        countAll(reader, singleValued)
      } else {
        countAll(reader, valuesSource)
      }
    } else {
      countAll(reader, field)
    }
  }

  private fun initializeCounters(): Unit {
    if (initialized) {
      return
    }
    assert(counts == null && hashCounts == null)
    initialized = true
    counts = IntArray(1024)
    hashCounts = HashMap()
  }

  /** Counts from the provided valueSource. */
  private fun count(valueSource: LongValuesSource, matchingDocs: List<MatchingDocs>): Unit {

    for (hits in matchingDocs) {
      if (hits.totalHits == 0) {
        continue
      }
      initializeCounters()

      val fv = valueSource.getValues(hits.context, null)

      // NOTE: this is not as efficient as working directly with the doc values APIs in the sparse
      // case
      // because we are doing a linear scan across all hits, but this API is more flexible since a
      // LongValuesSource can compute interesting values at query time

      val docs = hits.bits.iterator()!!
      var doc = docs.nextDoc()
      while (doc != DocIdSetIterator.NO_MORE_DOCS) {
        // Skip missing docs:
        if (fv.advanceExact(doc)) {
          increment(fv.longValue())
          totCount++
        }

        doc = docs.nextDoc()
      }
    }
  }

  /** Counts from the provided valuesSource. */
  private fun count(valuesSource: MultiLongValuesSource, matchingDocs: List<MatchingDocs>): Unit {
    for (hits in matchingDocs) {
      if (hits.totalHits == 0) {
        continue
      }
      initializeCounters()

      val multiValues = valuesSource.getValues(hits.context)

      val docs = hits.bits.iterator()!!
      var doc = docs.nextDoc()
      while (doc != DocIdSetIterator.NO_MORE_DOCS) {
        // Skip missing docs:
        if (multiValues.advanceExact(doc)) {
          val limit = multiValues.getValueCount()
          if (limit > 0) {
            totCount++
          }
          var previousValue = 0L
          for (i in 0 until limit) {
            val value = multiValues.nextValue()
            // do not increment the count for duplicate values
            if (i.toLong() == 0L || value != previousValue) {
              increment(value)
              previousValue = value
            }
          }
        }

        doc = docs.nextDoc()
      }
    }
  }

  /** Counts from the field's indexed doc values. */
  private fun count(field: String, matchingDocs: List<MatchingDocs>): Unit {
    for (hits in matchingDocs) {
      if (hits.totalHits == 0) {
        continue
      }
      initializeCounters()

      val multiValues =
          DocValues.getSortedNumeric(hits.context.reader(), field)
      val singleValues = DocValues.unwrapSingleton(multiValues)

      if (singleValues != null) {

        val it =
            ConjunctionUtils.intersectIterators(
                mutableListOf(hits.bits.iterator()!!, singleValues))

        while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          increment(singleValues.longValue())
          totCount++
        }
      } else {

        val it =
            ConjunctionUtils.intersectIterators(mutableListOf(hits.bits.iterator()!!, multiValues))

        while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          var limit = multiValues.docValueCount()
          if (limit > 0) {
            totCount++
          }
          var previousValue = 0L
          for (i in 0 until limit) {
            val value = multiValues.nextValue()
            // do not increment the count for duplicate values
            if (i.toLong() == 0L || value != previousValue) {
              increment(value)
              previousValue = value
            }
          }
        }
      }
    }
  }

  /** Count everything in the provided valueSource. */
  private fun countAll(reader: IndexReader, valueSource: LongValuesSource): Unit {

    for (context in reader.leaves()) {
      val fv = valueSource.getValues(context, null)
      var maxDoc = context.reader().maxDoc()

      for (doc in 0 until maxDoc) {
        // Skip missing docs:
        if (fv.advanceExact(doc)) {
          increment(fv.longValue())
          totCount++
        }
      }
    }
  }

  /** Count everything in the provided valueSource. */
  private fun countAll(reader: IndexReader, valueSource: MultiLongValuesSource): Unit {

    for (context in reader.leaves()) {
      val multiValues = valueSource.getValues(context)
      var maxDoc = context.reader().maxDoc()

      for (doc in 0 until maxDoc) {
        // Skip missing docs:
        if (multiValues.advanceExact(doc)) {
          val limit = multiValues.getValueCount()
          if (limit > 0) {
            totCount++
          }
          var previousValue = 0L
          for (i in 0 until limit) {
            val value = multiValues.nextValue()
            // do not increment the count for duplicate values
            if (i.toLong() == 0L || value != previousValue) {
              increment(value)
              previousValue = value
            }
          }
        }
      }
    }
  }

  /** Count everything in the specified field. */
  private fun countAll(reader: IndexReader, field: String): Unit {

    for (context in reader.leaves()) {

      val multiValues = DocValues.getSortedNumeric(context.reader(), field)
      val singleValues = DocValues.unwrapSingleton(multiValues)

      val liveDocs = context.reader().liveDocs

      var valuesIt: DocIdSetIterator = singleValues ?: multiValues
      valuesIt = if (liveDocs != null) FacetUtils.liveDocsDISI(valuesIt, liveDocs) else valuesIt

      if (singleValues != null) {

        while (valuesIt.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          totCount++
          increment(singleValues.longValue())
        }
      } else {

        while (valuesIt.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          var limit = multiValues.docValueCount()
          if (limit > 0) {
            totCount++
          }
          var previousValue = 0L
          for (i in 0 until limit) {
            val value = multiValues.nextValue()
            // do not increment the count for duplicate values
            if (i.toLong() == 0L || value != previousValue) {
              increment(value)
              previousValue = value
            }
          }
        }
      }
    }
  }

  private fun increment(value: Long): Unit {
    if (value >= 0 && value < counts!!.size) {
      counts!![value.toInt()]++
    } else {
      hashCounts!![value] = (hashCounts!![value] ?: 0) + 1
    }
  }

  override fun getAllChildren(dim: String, vararg path: String): FacetResult {
    validateDimAndPathForGetChildren(dim, *path)

    if (initialized == false) {
      // nothing was counted (either no hits or no values for all hits):
      assert(totCount == 0)
      return FacetResult(field, emptyArray(), totCount, emptyArray(), 0)
    }

    val labelValues = ArrayList<LabelAndValue>()
    for (i in 0 until counts!!.size) {
      if (counts!![i] != 0) {
        labelValues.add(LabelAndValue(i.toString(), counts!![i]))
      }
    }
    if (hashCounts!!.size != 0) {
      for (c in hashCounts!!) {
        var count = c.value
        if (count != 0) {
          labelValues.add(LabelAndValue(c.key.toString(), c.value))
        }
      }
    }

    return FacetResult(
        field,
        emptyArray(),
        totCount,
        labelValues.toTypedArray(),
        labelValues.size)
  }

  override fun getTopChildren(topN: Int, dim: String, vararg path: String): FacetResult {
    validateTopN(topN)
    validateDimAndPathForGetChildren(dim, *path)

    if (initialized == false) {
      // nothing was counted (either no hits or no values for all hits):
      assert(totCount == 0)
      return FacetResult(field, emptyArray(), totCount, emptyArray(), 0)
    }

    val pq = object : PriorityQueue<Entry>(minOf(topN, counts!!.size + hashCounts!!.size)) {
      override fun lessThan(a: Entry, b: Entry): Boolean {
        // sort by count descending, breaking ties by value ascending:
        return a.count < b.count || (a.count == b.count && a.value > b.value)
      }
    }

    var childCount = 0
    var e: Entry? = null
    for (i in 0 until counts!!.size) {
      if (counts!![i] != 0) {
        childCount++
        if (e == null) {
          e = Entry()
        }
        e.value = i.toLong()
        e.count = counts!![i]
        e = pq.insertWithOverflow(e)
      }
    }

    if (hashCounts!!.size != 0) {
      childCount += hashCounts!!.size
      for (c in hashCounts!!) {
        var count = c.value
        if (count != 0) {
          if (e == null) {
            e = Entry()
          }
          e.value = c.key
          e.count = count
          e = pq.insertWithOverflow(e)
        }
      }
    }

    val results = arrayOfNulls<LabelAndValue>(pq.size())
    while (pq.size() != 0) {
      val entry = pq.pop()!!
      results[pq.size()] = LabelAndValue(entry.value.toString(), entry.count)
    }

    return FacetResult(field, emptyArray(), totCount, results.requireNoNulls(), childCount)
  }

  /** Reusable hash entry to hold long facet value and int count. */
  private class Entry {
    var count = 0
    var value = 0L
  }

  /**
   * Returns all unique values seen, sorted by value. This functionality is very similar to [getAllChildren], but it guarantees the returned values will be sorted by
   * value (while `#getAllChildren` doesn't guarantee any sort order).
   *
   * Note: If you don't care about the order of children returned, it may be slightly more
   * efficient to use [getAllChildren].
   */
  fun getAllChildrenSortByValue(): FacetResult {
    if (initialized == false) {
      // nothing was counted (either no hits or no values for all hits):
      assert(totCount == 0)
      return FacetResult(field, emptyArray(), totCount, emptyArray(), 0)
    }

    val labelValues = ArrayList<LabelAndValue>()

    // compact & sort hash table's arrays by value
    val hashCounts = IntArray(this.hashCounts!!.size)
    val hashValues = LongArray(this.hashCounts!!.size)

    var upto = 0
    for (c in this.hashCounts!!) {
      if (c.value != 0) {
        hashCounts[upto] = c.value
        hashValues[upto] = c.key
        upto++
      }
    }

    assert(upto == this.hashCounts!!.size) { "upto=" + upto + " hashCounts.size=" + this.hashCounts!!.size }

    object : InPlaceMergeSorter() {
          override fun compare(i: Int, j: Int): Int {
        return hashValues[i].compareTo(hashValues[j])
      }

          override fun swap(i: Int, j: Int): Unit {
        var x = hashCounts[i]
        hashCounts[i] = hashCounts[j]
        hashCounts[j] = x

        val y = hashValues[j]
        hashValues[j] = hashValues[i]
        hashValues[i] = y
      }
    }.sort(0, upto)

    var countsAdded = false
    for (i in 0 until upto) {
      if (countsAdded == false && hashValues[i] >= counts!!.size) {
        countsAdded = true
        appendCounts(labelValues)
      }

      labelValues.add(LabelAndValue(hashValues[i].toString(), hashCounts[i]))
    }

    if (countsAdded == false) {
      appendCounts(labelValues)
    }

    return FacetResult(
        field,
        emptyArray(),
        totCount,
        labelValues.toTypedArray(),
        labelValues.size)
  }

  private fun appendCounts(labelValues: MutableList<LabelAndValue>): Unit {
    for (i in 0 until counts!!.size) {
      if (counts!![i] != 0) {
        labelValues.add(LabelAndValue(i.toString(), counts!![i]))
      }
    }
  }

  private fun validateDimAndPathForGetChildren(dim: String, vararg path: String): Unit {
    if (dim.equals(field) == false) {
      throw IllegalArgumentException(
          "invalid dim \"" + dim + "\"; should be \"" + field + "\"")
    }
    if (path.size != 0) {
      throw IllegalArgumentException("path.size should be 0")
    }
  }

  override fun getSpecificValue(dim: String, vararg path: String): Number {
    // TODO: should we impl this?
    throw UnsupportedOperationException()
  }

  override fun getAllDims(topN: Int): List<FacetResult> {
    validateTopN(topN)
    return listOf(getTopChildren(topN, field))
  }

  override fun toString(): String {
    val b = StringBuilder()
    b.append("LongValueFacetCounts totCount=")
    b.append(totCount)
    if (initialized) {
      b.append(":\n")
      for (i in 0 until counts!!.size) {
        if (counts!![i] != 0) {
          b.append("  ")
          b.append(i)
          b.append(" -> count=")
          b.append(counts!![i])
          b.append('\n')
        }
      }

      if (hashCounts!!.size != 0) {
        for (c in hashCounts!!) {
          if (c.value != 0) {
            b.append("  ")
            b.append(c.key)
            b.append(" -> count=")
            b.append(c.value)
            b.append('\n')
          }
        }
      }
    }

    return b.toString()
  }
}
