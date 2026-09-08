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
package org.gnit.lucenekmp.facet.taxonomy

import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader.Companion.INVALID_ORDINAL
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader.Companion.ROOT_ORDINAL

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.SortedNumericDocValues

/**
 * Utility class to easily retrieve previously indexed facet labels, allowing you to skip also
 * adding stored fields for these values, reducing your index size.
 *
 * @lucene.experimental
 */
open class TaxonomyFacetLabels {

  /** `TaxonomyReader` provided to the constructor */
  private val taxoReader: TaxonomyReader

  /** field storing the taxonomy ordinals */
  private val indexFieldName: String

  /**
   * Sole constructor. Do not close the provided [TaxonomyReader] while still using this
   * instance!
   */
  constructor(taxoReader: TaxonomyReader, indexFieldName: String) {
    this.taxoReader = taxoReader
    this.indexFieldName = indexFieldName
  }

  /**
   * Create and return an instance of [FacetLabelReader] to retrieve facet labels for multiple
   * documents and (optionally) for a specific dimension. You must create this per-segment, and then
   * step through all hits, in order, for that segment.
   *
   * **NOTE**: This class is not thread-safe, so you must use a new instance of this class for
   * each thread.
   *
   * @param readerContext LeafReaderContext used to access the `BinaryDocValues` facet field
   * @return an instance of [FacetLabelReader]
   * @throws IOException when a low-level IO issue occurs
   */
  fun getFacetLabelReader(readerContext: LeafReaderContext): FacetLabelReader {
    var ordinalValues =
        DocValues.getSortedNumeric(readerContext.reader(), indexFieldName)
    if (ordinalValues == null) {
      ordinalValues = DocValues.emptySortedNumeric()
    }

    return FacetLabelReader(ordinalValues)
  }

  /**
   * Utility class to retrieve facet labels for multiple documents.
   *
   * @lucene.experimental
   */
  inner class FacetLabelReader {
    /** By default, we store taxonomy ordinals in SortedNumericDocValues field */
    private val ordinalValues: SortedNumericDocValues

    private var currentDocId = -1
    private var currentDocHasValues = false
    private var currentPos = 0
    private var currentDocOrdinalCount = 0

    // Lazily set when nextFacetLabel(int docId, String facetDimension) is first called
    private var parents: ParallelTaxonomyArrays.IntArray? = null

    /** Construct from a specified [SortedNumericDocValues] field. */
    constructor(ordinalValues: SortedNumericDocValues) {
      this.ordinalValues = ordinalValues
    }

    /**
     * Retrieves the next [FacetLabel] for the specified `docId`, or `null` if
     * there are no more. This method has state: if the provided `docId` is the same as the
     * previous invocation, it returns the next [FacetLabel] for that document. Otherwise, it
     * advances to the new `docId` and provides the first [FacetLabel] for that
     * document, or `null` if that document has no indexed facets. Each new `docId` must
     * be in strictly monotonic (increasing) order.
     *
     * **NOTE**: The returned FacetLabels may not be in the same order in which they were
     * indexed
     *
     * @param docId input docId provided in monotonic (non-decreasing) order
     * @return the first or next [FacetLabel], or `null` if there are no more
     * @throws IOException when a low-level IO issue occurs
     * @throws IllegalArgumentException if docId provided is less than docId supplied in an earlier
     *     invocation
     */
    fun nextFacetLabel(docId: Int): FacetLabel? {
      if (currentDocId != docId) {
        if (docId < currentDocId) {
          throw IllegalArgumentException(
              "docs out of order: previous docId=$currentDocId current docId=$docId")
        }

        currentDocId = docId

        currentDocHasValues = ordinalValues.advanceExact(docId)
        if (currentDocHasValues) {
          currentDocOrdinalCount = ordinalValues.docValueCount()
          currentPos = 0
        }
      }

      if (currentDocHasValues == false) {
        return null
      }

      assert(currentPos <= currentDocOrdinalCount)
      if (currentPos == currentDocOrdinalCount) {
        return null
      }

      val ord = ordinalValues.nextValue().toInt()
      currentPos++

      return taxoReader.getPath(ord)
    }

    private fun isDescendant(ord: Int, ancestorOrd: Int): Boolean {
      var ord = ord
      while (ord != INVALID_ORDINAL && ord != ROOT_ORDINAL) {
        if (parents!!.get(ord) == ancestorOrd) {
          return true
        }
        ord = parents!!.get(ord)
      }
      return false
    }

    /**
     * Retrieves the next [FacetLabel] for the specified `docId` under the requested
     * `facetDimension`, or `null` if there are no more. This method has state: if the
     * provided `docId` is the same as the previous invocation, it returns the next [FacetLabel] for that document. Otherwise, it advances to the new `docId` and provides
     * the first [FacetLabel] for that document, or `null` if that document has no
     * indexed facets. Each new `docId` must be in strictly monotonic (increasing) order.
     *
     * **NOTE**: This method loads the `int[] parents` array from the taxonomy index.
     * The returned FacetLabels may not be in the same order in which they were indexed.
     *
     * @param docId input docId provided in non-decreasing order
     * @return the first or next [FacetLabel], or `null` if there are no more
     * @throws IOException if [TaxonomyReader] has problems getting path for an ordinal
     * @throws IllegalArgumentException if docId provided is less than docId supplied in an earlier
     *     invocation
     * @throws IllegalArgumentException if facetDimension is null
     */
    fun nextFacetLabel(docId: Int, facetDimension: String?): FacetLabel? {
      if (facetDimension == null) {
        throw IllegalArgumentException("Input facet dimension cannot be null")
      }
      val parentOrd = taxoReader.getOrdinal(FacetLabel(facetDimension))
      if (parentOrd == INVALID_ORDINAL) {
        throw IllegalArgumentException(
            "Category ordinal not found for facet dimension: $facetDimension")
      }

      if (currentDocId != docId) {
        if (docId < currentDocId) {
          throw IllegalArgumentException(
              "docs out of order: previous docId=$currentDocId current docId=$docId")
        }
        currentDocId = docId

        currentDocHasValues = ordinalValues.advanceExact(docId)
        if (currentDocHasValues) {
          currentDocOrdinalCount = ordinalValues.docValueCount()
          currentPos = 0
        }
      }

      if (currentDocHasValues == false) {
        return null
      }

      assert(currentPos <= currentDocOrdinalCount)
      if (currentPos == currentDocOrdinalCount) {
        return null
      }

      if (parents == null) {
        parents = taxoReader.getParallelTaxonomyArrays().parents()
      }

      do {
        val ord = ordinalValues.nextValue().toInt()
        currentPos++
        if (isDescendant(ord, parentOrd) == true) {
          return taxoReader.getPath(ord)
        }
      } while (currentPos < currentDocOrdinalCount)

      return null
    }
  }
}
