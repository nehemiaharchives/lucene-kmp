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

import kotlin.random.Random
import kotlin.test.*

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.facet.FacetField
import org.gnit.lucenekmp.facet.FacetResult
import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.Facets
import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.FacetsCollectorManager
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.facet.LabelAndValue
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.util.IOUtils

class TestTaxonomyFacetCounts2 : FacetTestCase() {

  private val A = Term("f", "a")
  private val CP_A = "A"
  private val CP_B = "B"
  private val CP_C = "C"
  private val CP_D = "D" // indexed w/ NO_PARENTS
  private val NUM_CHILDREN_CP_A = 5
  private val NUM_CHILDREN_CP_B = 3
  private val NUM_CHILDREN_CP_C = 5
  private val NUM_CHILDREN_CP_D = 5
  private val CATEGORIES_A = Array(NUM_CHILDREN_CP_A) { i -> FacetField(CP_A, i.toString()) }
  private val CATEGORIES_B = Array(NUM_CHILDREN_CP_B) { i -> FacetField(CP_B, i.toString()) }
  // NO_PARENTS categories
  private val CATEGORIES_C = Array(NUM_CHILDREN_CP_C) { i -> FacetField(CP_C, i.toString()) }
  // Multi-level categories
  private val CATEGORIES_D = Array(NUM_CHILDREN_CP_D) { i ->
    val value = i.toString()
    FacetField(CP_D, value, value + value) // e.g. D/1/11, D/2/22...
  }

  private lateinit var indexDir: Directory
  private lateinit var taxoDir: Directory
  private lateinit var allExpectedCounts: MutableMap<String, Int>
  private lateinit var termExpectedCounts: MutableMap<String, Int>

  @AfterTest
  fun afterClassCountingFacetsAggregatorTest() {
    IOUtils.close(indexDir, taxoDir)
  }

  private fun randomCategories(random: Random): List<FacetField> {
    // add random categories from the two dimensions, ensuring that the same
    // category is not added twice.
    var numFacetsA = random.nextInt(3) + 1 // 1-3
    var numFacetsB = random.nextInt(2) + 1 // 1-2
    var categories_a = ArrayList<FacetField>()
    categories_a.addAll(CATEGORIES_A.asList())
    var categories_b = ArrayList<FacetField>()
    categories_b.addAll(CATEGORIES_B.asList())
    categories_a.shuffle(random)
    categories_b.shuffle(random)

    var categories = ArrayList<FacetField>()
    categories.addAll(categories_a.subList(0, numFacetsA))
    categories.addAll(categories_b.subList(0, numFacetsB))

    // add the NO_PARENT categories
    categories.add(CATEGORIES_C[random().nextInt(NUM_CHILDREN_CP_C)])
    categories.add(CATEGORIES_D[random().nextInt(NUM_CHILDREN_CP_D)])

    return categories
  }

  private fun addField(doc: Document) {
    doc.add(StringField(A.field(), A.text(), Store.NO))
  }

  private fun addFacets(doc: Document, updateTermExpectedCounts: Boolean) {
    var docCategories = randomCategories(random())
    for (ff in docCategories) {
      doc.add(ff)
      val cp = ff.dim + "/" + ff.path[0]
      allExpectedCounts.put(cp, allExpectedCounts.getValue(cp) + 1)
      if (updateTermExpectedCounts) {
        termExpectedCounts.put(cp, termExpectedCounts.getValue(cp) + 1)
      }
    }
    // add 1 to each NO_PARENTS dimension
    allExpectedCounts.put(CP_B, allExpectedCounts.getValue(CP_B) + 1)
    allExpectedCounts.put(CP_C, allExpectedCounts.getValue(CP_C) + 1)
    allExpectedCounts.put(CP_D, allExpectedCounts.getValue(CP_D) + 1)
    if (updateTermExpectedCounts) {
      termExpectedCounts.put(CP_B, termExpectedCounts.getValue(CP_B) + 1)
      termExpectedCounts.put(CP_C, termExpectedCounts.getValue(CP_C) + 1)
      termExpectedCounts.put(CP_D, termExpectedCounts.getValue(CP_D) + 1)
    }
  }

  private fun getConfig(): FacetsConfig {
    var config = FacetsConfig()
    config.setMultiValued("A", true)
    config.setMultiValued("B", true)
    config.setRequireDimCount("B", true)
    config.setHierarchical("D", true)
    return config
  }

  private fun indexDocsNoFacets(indexWriter: IndexWriter) {
    var numDocs = atLeast(2)
    for (i in 0 until numDocs) {
      var doc = Document()
      addField(doc)
      indexWriter.addDocument(doc)
    }
    indexWriter.commit() // flush a segment
  }

  private fun indexDocsWithFacetsNoTerms(indexWriter: IndexWriter, taxoWriter: TaxonomyWriter) {
    var random = random()
    var numDocs = atLeast(random, 2)
    var config = getConfig()
    for (i in 0 until numDocs) {
      var doc = Document()
      addFacets(doc, false)
      indexWriter.addDocument(config.build(taxoWriter, doc))
    }
    indexWriter.commit() // flush a segment
  }

  private fun indexDocsWithFacetsAndTerms(indexWriter: 
      IndexWriter, taxoWriter: TaxonomyWriter) {
    var random = random()
    var numDocs = atLeast(random, 2)
    var config = getConfig()
    for (i in 0 until numDocs) {
      var doc = Document()
      addFacets(doc, true)
      addField(doc)
      indexWriter.addDocument(config.build(taxoWriter, doc))
    }
    indexWriter.commit() // flush a segment
  }

  private fun indexDocsWithFacetsAndSomeTerms(indexWriter: 
      IndexWriter, taxoWriter: TaxonomyWriter) {
    var random = random()
    var numDocs = atLeast(random, 2)
    var config = getConfig()
    for (i in 0 until numDocs) {
      var doc = Document()
      var hasContent = random.nextBoolean()
      if (hasContent) {
        addField(doc)
      }
      addFacets(doc, hasContent)
      indexWriter.addDocument(config.build(taxoWriter, doc))
    }
    indexWriter.commit() // flush a segment
  }

  // initialize expectedCounts w/ 0 for all categories
  private fun newCounts(): MutableMap<String, Int> {
    var counts = HashMap<String, Int>()
    counts.put(CP_A, 0)
    counts.put(CP_B, 0)
    counts.put(CP_C, 0)
    counts.put(CP_D, 0)
    for (ff in CATEGORIES_A) {
      counts.put(ff.dim + "/" + ff.path[0], 0)
    }
    for (ff in CATEGORIES_B) {
      counts.put(ff.dim + "/" + ff.path[0], 0)
    }
    for (ff in CATEGORIES_C) {
      counts.put(ff.dim + "/" + ff.path[0], 0)
    }
    for (ff in CATEGORIES_D) {
      counts.put(ff.dim + "/" + ff.path[0], 0)
    }
    return counts
  }

  @BeforeTest
  fun beforeClassCountingFacetsAggregatorTest() {
    indexDir = newDirectory()
    taxoDir = newDirectory()

    // create an index which has:
    // 1. Segment with no categories, but matching results
    // 2. Segment w/ categories, but no results
    // 3. Segment w/ categories and results
    // 4. Segment w/ categories, but only some results

    var conf = newIndexWriterConfig(MockAnalyzer(random()))
    conf.setMergePolicy(
        NoMergePolicy.INSTANCE) // prevent merges, so we can control the index segments
    var indexWriter = IndexWriter(indexDir, conf)
    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)

    allExpectedCounts = newCounts()
    termExpectedCounts = newCounts()

    // segment w/ no categories
    indexDocsNoFacets(indexWriter)

    // segment w/ categories, no content
    indexDocsWithFacetsNoTerms(indexWriter, taxoWriter)

    // segment w/ categories and content
    indexDocsWithFacetsAndTerms(indexWriter, taxoWriter)

    // segment w/ categories and some content
    indexDocsWithFacetsAndSomeTerms(indexWriter, taxoWriter)

    indexWriter.close()
    IOUtils.close(taxoWriter)
  }

  @Test
  fun testDifferentNumResults() {
    // test the collector w/ FacetRequests and different numResults
    var indexReader = DirectoryReader.open(indexDir)
    var taxoReader = DirectoryTaxonomyReader(taxoDir)
    var searcher = newSearcher(indexReader)

    var q = TermQuery(A)
    var sfc = searcher.search(q, FacetsCollectorManager())
    var facets = getTaxonomyFacetCounts(taxoReader, getConfig(), sfc!!)
    var result = facets.getTopChildren(NUM_CHILDREN_CP_A, CP_A)!!
    assertEquals(-1, result.value.toInt())
    for (labelValue in result.labelValues) {
      assertEquals<Any?>(termExpectedCounts.get(CP_A + "/" + labelValue.label), labelValue.value)
    }
    result = facets.getTopChildren(NUM_CHILDREN_CP_B, CP_B)!!
    assertEquals<Any?>(termExpectedCounts.get(CP_B), result.value)
    for (labelValue in result.labelValues) {
      assertEquals<Any?>(termExpectedCounts.get(CP_B + "/" + labelValue.label), labelValue.value)
    }

    IOUtils.close(indexReader, taxoReader)
  }

  @Test
  fun testAllCounts() {
    var indexReader = DirectoryReader.open(indexDir)
    var taxoReader = DirectoryTaxonomyReader(taxoDir)
    var searcher = newSearcher(indexReader)

    var sfc = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())

    var facets = getTaxonomyFacetCounts(taxoReader, getConfig(), sfc!!)

    var result = facets.getTopChildren(NUM_CHILDREN_CP_A, CP_A)!!
    assertEquals(-1, result.value.toInt())
    var prevValue = Int.MAX_VALUE
    for (labelValue in result.labelValues) {
      assertEquals<Any?>(allExpectedCounts.get(CP_A + "/" + labelValue.label), labelValue.value)
      assertTrue(labelValue.value.toInt() <= prevValue, "wrong sort order of sub results: labelValue.value="
              + labelValue.value
              + " prevValue="
              + prevValue)
      prevValue = labelValue.value.toInt()
    }

    result = facets.getTopChildren(NUM_CHILDREN_CP_B, CP_B)!!
    assertEquals<Any?>(allExpectedCounts.get(CP_B), result.value)
    prevValue = Int.MAX_VALUE
    for (labelValue in result.labelValues) {
      assertEquals<Any?>(allExpectedCounts.get(CP_B + "/" + labelValue.label), labelValue.value)
      assertTrue(labelValue.value.toInt() <= prevValue, "wrong sort order of sub results: labelValue.value="
              + labelValue.value
              + " prevValue="
              + prevValue)
      prevValue = labelValue.value.toInt()
    }

    IOUtils.close(indexReader, taxoReader)
  }

  @Test
  fun testBigNumResults() {
    var indexReader = DirectoryReader.open(indexDir)
    var taxoReader = DirectoryTaxonomyReader(taxoDir)
    var searcher = newSearcher(indexReader)

    var sfc = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())

    var facets = getTaxonomyFacetCounts(taxoReader, getConfig(), sfc!!)

    var result = facets.getTopChildren(Int.MAX_VALUE, CP_A)!!
    assertEquals(-1, result.value.toInt())
    for (labelValue in result.labelValues) {
      assertEquals<Any?>(allExpectedCounts.get(CP_A + "/" + labelValue.label), labelValue.value)
    }
    result = facets.getTopChildren(Int.MAX_VALUE, CP_B)!!
    assertEquals<Any?>(allExpectedCounts.get(CP_B), result.value)
    for (labelValue in result.labelValues) {
      assertEquals<Any?>(allExpectedCounts.get(CP_B + "/" + labelValue.label), labelValue.value)
    }

    IOUtils.close(indexReader, taxoReader)
  }

  @Test
  fun testNoParents() {
    var indexReader = DirectoryReader.open(indexDir)
    var taxoReader = DirectoryTaxonomyReader(taxoDir)
    var searcher = newSearcher(indexReader)

    var sfc = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())

    var facets = getTaxonomyFacetCounts(taxoReader, getConfig(), sfc!!)

    var result = facets.getTopChildren(NUM_CHILDREN_CP_C, CP_C)!!
    assertEquals<Any?>(allExpectedCounts.get(CP_C), result.value)
    for (labelValue in result.labelValues) {
      assertEquals<Any?>(allExpectedCounts.get(CP_C + "/" + labelValue.label), labelValue.value)
    }
    result = facets.getTopChildren(NUM_CHILDREN_CP_D, CP_D)!!
    assertEquals<Any?>(allExpectedCounts.get(CP_C), result.value)
    for (labelValue in result.labelValues) {
      assertEquals<Any?>(allExpectedCounts.get(CP_D + "/" + labelValue.label), labelValue.value)
    }

    IOUtils.close(indexReader, taxoReader)
  }
}
