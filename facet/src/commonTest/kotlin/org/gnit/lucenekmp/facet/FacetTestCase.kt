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

import org.gnit.lucenekmp.facet.taxonomy.*
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyFacetLabels.FacetLabelReader
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.jdkport.assert
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

abstract class FacetTestCase : LuceneTestCase() {
    fun getTaxonomyFacetCounts(taxoReader: TaxonomyReader, config: FacetsConfig, c: FacetsCollector): Facets =
        getTaxonomyFacetCounts(taxoReader, config, c, FacetsConfig.DEFAULT_INDEX_FIELD_NAME)

    fun getTaxonomyFacetCounts(taxoReader: TaxonomyReader, config: FacetsConfig, c: FacetsCollector, indexFieldName: String): Facets =
        FastTaxonomyFacetCounts(indexFieldName, taxoReader, config, c)

    /**
   * Utility method that uses [FacetLabelReader] to get facet labels for each hit in [MatchingDocs]. The method returns `List<List<FacetLabel>>` where outer list has one entry
   * per document and inner list has all [FacetLabel] entries that belong to a document. The
   * inner list may be empty if no [FacetLabel] are found for a hit.
   *
   * @param taxoReader [TaxonomyReader] used to read taxonomy during search. This instance is
   *     expected to be open for reading.
   * @param fc [FacetsCollector] A collector with matching hits.
   * @param dimension facet dimension for which labels are requested. A null value fetches labels
   *     for all dimensions.
   * @return `List<List<FacetLabel>` where outer list has one non-null entry per document. and
   *     inner list contain all [FacetLabel] entries that belong to a document.
   * @throws IOException when a low-level IO issue occurs.
   */
    fun getAllTaxonomyFacetLabels(dimension: String?, taxoReader: TaxonomyReader, fc: FacetsCollector): List<List<FacetLabel>> {
        val actualLabels = mutableListOf<List<FacetLabel>>()
        val taxoLabels = TaxonomyFacetLabels(taxoReader, FacetsConfig.DEFAULT_INDEX_FIELD_NAME)
        for (m in fc.getMatchingDocs()) {
            val facetLabelReader = taxoLabels.getFacetLabelReader(m.context)
            val disi = m.bits.iterator()!!
            while (disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                actualLabels.add(allFacetLabels(disi.docID(), dimension, facetLabelReader))
            }
        }
        return actualLabels
    }

    /**
   * Utility method to get all facet labels for an input docId and dimension using the supplied
   * [FacetLabelReader].
   *
   * @param docId docId for which facet labels are needed.
   * @param dimension Retain facet labels for supplied dimension only. A null value fetches all
   *     facet labels.
   * @param facetLabelReader [FacetLabelReader] instance use to get facet labels for input
   *     docId.
   * @return `List<FacetLabel>` containing matching facet labels.
   * @throws IOException when a low-level IO issue occurs while reading facet labels.
   */
    internal fun allFacetLabels(docId: Int, dimension: String?, facetLabelReader: FacetLabelReader): List<FacetLabel> {
        val facetLabels = mutableListOf<FacetLabel>()
        var facetLabel: FacetLabel?
        if (dimension != null) {
            facetLabel = facetLabelReader.nextFacetLabel(docId, dimension)
            while (facetLabel != null) {
                facetLabels.add(facetLabel)
                facetLabel = facetLabelReader.nextFacetLabel(docId, dimension)
            }
        } else {
            facetLabel = facetLabelReader.nextFacetLabel(docId)
            while (facetLabel != null) {
                facetLabels.add(facetLabel)
                facetLabel = facetLabelReader.nextFacetLabel(docId)
            }
        }
        return facetLabels
    }

    protected fun getRandomTokens(count: Int): Array<String> {
        val tokens = Array(count) { "" }
        for (i in tokens.indices) {
            tokens[i] = TestUtil.randomRealisticUnicodeString(random(), 1, 10)
            // tokens[i] = _TestUtil.randomSimpleString(random(), 1, 10);
        }
        return tokens
    }

    protected fun pickToken(tokens: Array<String>): String {
        for (i in tokens.indices) {
            if (random().nextBoolean()) {
                return tokens[i]
            }
        }
        // Move long tail onto first token:
        return tokens[0]
    }

    protected class TestDoc {
        lateinit var content: String
        lateinit var dims: Array<String?>
        var value: Float = 0f
    }

    protected fun getRandomDocs(tokens: Array<String>, count: Int, numDims: Int): List<TestDoc> {
        val docs = mutableListOf<TestDoc>()
        for (i in 0 until count) {
            val doc = TestDoc()
            docs.add(doc)
            doc.content = pickToken(tokens)
            doc.dims = arrayOfNulls(numDims)
            for (j in 0 until numDims) {
                doc.dims[j] = pickToken(tokens)
                if (random().nextInt(10) < 3) {
                    break
                }
            }
            if (VERBOSE) {
                println("  doc $i: content=${doc.content}")
                for (j in 0 until numDims) {
                    if (doc.dims[j] != null) {
                        println("    dim[$j]=${doc.dims[j]}")
                    }
                }
            }
        }
        return docs
    }

    protected fun sortTies(results: List<FacetResult>) {
        for (result in results) {
            sortTies(result.labelValues)
        }
    }

    protected fun sortTies(labelValues: Array<LabelAndValue>) {
        var lastValue = -1.0
        var numInRow = 0
        var i = 0
        while (i <= labelValues.size) {
            if (i < labelValues.size && labelValues[i].value.toDouble() == lastValue) {
                numInRow++
            } else {
                if (numInRow > 1) {
                    labelValues.sortWith(Comparator { a, b ->
                        assert(a.value.toDouble() == b.value.toDouble())
                        BytesRef(a.label).compareTo(BytesRef(b.label))
                    }, i - numInRow, i)
                }
                numInRow = 1
                if (i < labelValues.size) {
                    lastValue = labelValues[i].value.toDouble()
                }
            }
            i++
        }
    }

    protected fun sortLabelValues(labelValues: MutableList<LabelAndValue>) {
        labelValues.sortWith(Comparator { a, b ->
            if (a.value.toDouble() > b.value.toDouble()) {
                -1
            } else if (a.value.toDouble() < b.value.toDouble()) {
                1
            } else {
                BytesRef(a.label).compareTo(BytesRef(b.label))
            }
        })
    }

    protected fun sortFacetResults(results: MutableList<FacetResult>) {
        results.sortWith(Comparator { a, b ->
            if (a.value.toDouble() > b.value.toDouble()) {
                -1
            } else if (b.value.toDouble() > a.value.toDouble()) {
                1
            } else {
                a.dim.compareTo(b.dim)
            }
        })
    }

    protected fun assertFloatValuesEquals(a: List<FacetResult>, b: List<FacetResult>) {
        assertEquals(a.size, b.size)
        var lastValue = Float.POSITIVE_INFINITY
        val aByDim = HashMap<String, FacetResult>()
        for (i in a.indices) {
            assertTrue(a[i].value.toFloat() <= lastValue)
            lastValue = a[i].value.toFloat()
            aByDim[a[i].dim] = a[i]
        }
        lastValue = Float.POSITIVE_INFINITY
        val bByDim = HashMap<String, FacetResult>()
        for (i in b.indices) {
            bByDim[b[i].dim] = b[i]
            assertTrue(b[i].value.toFloat() <= lastValue)
            lastValue = b[i].value.toFloat()
        }
        for (dim in aByDim.keys) {
            assertFloatValuesEquals(aByDim[dim]!!, bByDim[dim]!!)
        }
    }

    protected fun assertFloatValuesEquals(a: FacetResult, b: FacetResult) {
        assertEquals(a.dim, b.dim)
        assertTrue(a.path.contentEquals(b.path))
        assertEquals(a.childCount, b.childCount)
        assertNumericValuesEquals(a.value, b.value)
        assertEquals(a.labelValues.size, b.labelValues.size)
        for (i in a.labelValues.indices) {
            assertEquals(a.labelValues[i].label, b.labelValues[i].label)
            assertNumericValuesEquals(a.labelValues[i].value, b.labelValues[i].value)
        }
    }

    protected fun assertNumericValuesEquals(a: Number, b: Number) {
        assertTrue(a::class == b::class)
        // JUnit accepts a negative delta for exact equality; Kotlin requires a nonnegative tolerance.
        if (a is Float) {
            assertEquals(a.toDouble(), b.toFloat().toDouble(), (a.toDouble() / 1e5).coerceAtLeast(0.0))
        } else if (a is Double) {
            assertEquals(a, b.toDouble(), (a / 1e5).coerceAtLeast(0.0))
        } else {
            assertEquals(a, b)
        }
    }

    protected fun assertFacetResult(result: FacetResult, expectedDim: String, expectedPath: Array<String>, expectedChildCount: Int, expectedValue: Number, vararg expectedChildren: LabelAndValue) {
        assertEquals(expectedDim, result.dim)
        assertContentEquals(expectedPath, result.path)
        assertEquals(expectedChildCount, result.childCount)
        assertNumericValuesEquals(expectedValue, result.value)
        assertEquals(expectedChildren.size, result.labelValues.size)
        // assert children equal with no assumption of the children ordering
        assertTrue(result.labelValues.asList().containsAll(expectedChildren.asList()))
    }

    protected fun assertFacetResult(result: FacetResult, expectedDim: String, expectedPath: Array<String>, expectedChildCount: Int, expectedValue: Number, countPerLabel: Map<String, Int>, vararg expectedChildren: LabelAndValue) {
        assertFacetResult(result, expectedDim, expectedPath, expectedChildCount, expectedValue, *expectedChildren)
        assertEquals(result.labelValues.size, countPerLabel.size)
        for (lv in result.labelValues) {
            assertEquals(lv.count, countPerLabel[lv.label])
        }
    }
}
