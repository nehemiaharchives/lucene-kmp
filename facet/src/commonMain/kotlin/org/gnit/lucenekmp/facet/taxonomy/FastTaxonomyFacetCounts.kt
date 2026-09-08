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

import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.FacetsCollector.MatchingDocs
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.search.ConjunctionUtils
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.jdkport.assert

/**
 * Computes facets counts, assuming the default encoding into DocValues was used.
 *
 * @lucene.experimental
 */
open class FastTaxonomyFacetCounts : TaxonomyFacets {
    /** Create `FastTaxonomyFacetCounts`, which also counts all facet labels. */
    constructor(taxoReader: TaxonomyReader, config: FacetsConfig, fc: FacetsCollector) :
        this(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, taxoReader, config, fc)

    /**
   * Create `FastTaxonomyFacetCounts`, using the specified `indexFieldName` for
   * ordinals. Use this if you had set [FacetsConfig.setIndexFieldName] to change the index
   * field name for certain dimensions.
   */
    constructor(indexFieldName: String, taxoReader: TaxonomyReader, config: FacetsConfig, fc: FacetsCollector) :
        super(indexFieldName, taxoReader, config, fc) {
        count(fc.getMatchingDocs())
    }

    /**
   * Create `FastTaxonomyFacetCounts`, using the specified `indexFieldName` for
   * ordinals, and counting all non-deleted documents in the index. This is the same result as
   * searching on [MatchAllDocsQuery], but faster
   */
    constructor(indexFieldName: String, reader: IndexReader, taxoReader: TaxonomyReader, config: FacetsConfig) :
        super(indexFieldName, taxoReader, config, null) {
        countAll(reader)
    }

    private fun count(matchingDocs: List<MatchingDocs>) {
        for (hits in matchingDocs) {
            if (hits.totalHits == 0) {
                continue
            }
            val multiValued = hits.context.reader().getSortedNumericDocValues(indexFieldName)
            if (multiValued == null) {
                continue
            }
            initializeValueCounters()
            val singleValued = DocValues.unwrapSingleton(multiValued)
            val valuesIt = singleValued ?: multiValued
            val it = ConjunctionUtils.intersectIterators(mutableListOf(hits.bits.iterator()!!, valuesIt))
            if (singleValued != null) {
                if (counts != null) {
                    while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        counts!![singleValued.longValue().toInt()]++
                    }
                } else {
                    while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        sparseCounts!!.addTo(singleValued.longValue().toInt(), 1)
                    }
                }
            } else {
                if (counts != null) {
                    while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        for (i in 0 until multiValued.docValueCount()) {
                            counts!![multiValued.nextValue().toInt()]++
                        }
                    }
                } else {
                    while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        for (i in 0 until multiValued.docValueCount()) {
                            sparseCounts!!.addTo(multiValued.nextValue().toInt(), 1)
                        }
                    }
                }
            }
        }
        rollup()
    }

    private fun countAll(reader: IndexReader) {
        for (context in reader.leaves()) {
            val multiValued = context.reader().getSortedNumericDocValues(indexFieldName)
            if (multiValued == null) {
                continue
            }
            initializeValueCounters()
            assert(counts != null)
            val liveDocs = context.reader().liveDocs
            val singleValued = DocValues.unwrapSingleton(multiValued)
            if (singleValued != null) {
                if (liveDocs == null) {
                    var doc = singleValued.nextDoc()
                    while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        counts!![singleValued.longValue().toInt()]++
                        doc = singleValued.nextDoc()
                    }
                } else {
                    var doc = singleValued.nextDoc()
                    while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        if (!liveDocs.get(doc)) {
                            doc = singleValued.nextDoc()
                            continue
                        }
                        counts!![singleValued.longValue().toInt()]++
                        doc = singleValued.nextDoc()
                    }
                }
            } else {
                if (liveDocs == null) {
                    var doc = multiValued.nextDoc()
                    while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        for (i in 0 until multiValued.docValueCount()) {
                            counts!![multiValued.nextValue().toInt()]++
                        }
                        doc = multiValued.nextDoc()
                    }
                } else {
                    var doc = multiValued.nextDoc()
                    while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        if (!liveDocs.get(doc)) {
                            doc = multiValued.nextDoc()
                            continue
                        }
                        for (i in 0 until multiValued.docValueCount()) {
                            counts!![multiValued.nextValue().toInt()]++
                        }
                        doc = multiValued.nextDoc()
                    }
                }
            }
        }
        rollup()
    }
}
