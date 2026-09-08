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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.facet.taxonomy.IntAssociationFacetField
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.jdkport.assert
import kotlin.test.*

class TestDrillDownQuery : FacetTestCase() {

  private lateinit var reader: IndexReader
  private lateinit var taxo: DirectoryTaxonomyReader
  private lateinit var dir: Directory
  private lateinit var taxoDir: Directory
  private lateinit var config: FacetsConfig

  @AfterTest
  fun afterClassDrillDownQueryTest() {
    IOUtils.close(reader, taxo, dir, taxoDir)
  }

  // Common tests provide per-test lifecycle hooks rather than JUnit class hooks.
  @BeforeTest
  fun beforeClassDrillDownQueryTest() {
    dir = newDirectory()
    var r = random()
    var writer =
        RandomIndexWriter(
            r, dir, newIndexWriterConfig(MockAnalyzer(r, MockTokenizer.KEYWORD, false)))

    taxoDir = newDirectory()
    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    config = FacetsConfig()

    // Randomize the per-dim config:
    config.setHierarchical("a", random().nextBoolean())
    config.setMultiValued("a", random().nextBoolean())
    if (random().nextBoolean()) {
      config.setIndexFieldName("a", "\$a")
    }
    config.setRequireDimCount("a", true)

    config.setHierarchical("b", random().nextBoolean())
    config.setMultiValued("b", random().nextBoolean())
    if (random().nextBoolean()) {
      config.setIndexFieldName("b", "\$b")
    }
    config.setRequireDimCount("b", true)

    for (i in 0 until 100) {
      var doc = Document()
      if (i % 2 == 0) { // 50
        doc.add(TextField("content", "foo", Field.Store.NO))
      }
      if (i % 3 == 0) { // 33
        doc.add(TextField("content", "bar", Field.Store.NO))
      }
      if (i % 4 == 0) { // 25
        if (r.nextBoolean()) {
          doc.add(FacetField("a", "1"))
        } else {
          doc.add(FacetField("a", "2"))
        }
      }
      if (i % 5 == 0) { // 20
        doc.add(FacetField("b", "1"))
      }
      writer.addDocument(config.build(taxoWriter, doc))
    }

    taxoWriter.close()
    reader = writer.reader
    writer.close()

    taxo = DirectoryTaxonomyReader(taxoDir)
  }

  @Test
  fun testAndOrs() {
    var searcher = newSearcher(reader)

    // test (a/1 OR a/2) AND b/1
    var q = DrillDownQuery(config)
    q.add("a", "1")
    q.add("a", "2")
    q.add("b", "1")
    var docs = searcher.search(q, 100)
    assertEquals(5L, docs.totalHits.value)
  }

  @Test
  fun testQuery() {
    var searcher = newSearcher(reader)

    // Making sure the query yields 25 documents with the facet "a"
    var q = DrillDownQuery(config)
    q.add("a")
    QueryUtils.check(q)
    var docs = searcher.search(q, 100)
    assertEquals(25L, docs.totalHits.value)

    // Making sure the query yields 5 documents with the facet "b" and the
    // previous (facet "a") query as a base query
    var q2 = DrillDownQuery(config, q)
    q2.add("b")
    docs = searcher.search(q2, 100)
    assertEquals(5L, docs.totalHits.value)

    // Making sure that a query of both facet "a" and facet "b" yields 5 results
    var q3 = DrillDownQuery(config)
    q3.add("a")
    q3.add("b")
    docs = searcher.search(q3, 100)

    assertEquals(5L, docs.totalHits.value)
    // Check that content:foo (which yields 50% results) and facet/b (which yields 20%)
    // would gather together 10 results (10%..)
    var fooQuery = TermQuery(Term("content", "foo"))
    var q4 = DrillDownQuery(config, fooQuery)
    q4.add("b")
    docs = searcher.search(q4, 100)
    assertEquals(10L, docs.totalHits.value)
  }

  @Test
  fun testQueryImplicitDefaultParams() {
    var searcher = newSearcher(reader)

    // Create the base query to start with
    var q = DrillDownQuery(config)
    q.add("a")

    // Making sure the query yields 5 documents with the facet "b" and the
    // previous (facet "a") query as a base query
    var q2 = DrillDownQuery(config, q)
    q2.add("b")
    var docs = searcher.search(q2, 100)
    assertEquals(5L, docs.totalHits.value)

    // Check that content:foo (which yields 50% results) and facet/b (which yields 20%)
    // would gather together 10 results (10%..)
    var fooQuery = TermQuery(Term("content", "foo"))
    var q4 = DrillDownQuery(config, fooQuery)
    q4.add("b")
    docs = searcher.search(q4, 100)
    assertEquals(10L, docs.totalHits.value)
  }

  @Test
  fun testZeroLimit() {
    var searcher = newSearcher(reader)
    var q = DrillDownQuery(config)
    q.add("b", "1")
    val limit = 0

    var facetCollector =
        FacetsCollectorManager.search(searcher, q, limit, FacetsCollectorManager())
            .facetsCollector
    var facets =
        getTaxonomyFacetCounts(
            taxo, config, facetCollector, config.getDimConfig("b").indexFieldName)
    assertNotNull(facets.getTopChildren(10, "b"))
  }

  @Test
  fun testScoring() {
    // verify that drill-down queries do not modify scores
    var searcher = newSearcher(reader)

    val scores = FloatArray(reader.maxDoc())

    var q = TermQuery(Term("content", "foo"))
    var docs = searcher.search(q, reader.maxDoc()) // fetch all available docs to this query
    for (sd in docs.scoreDocs) {
      scores[sd.doc] = sd.score
    }

    // create a drill-down query with category "a", scores should not change
    var q2 = DrillDownQuery(config, q)
    q2.add("a")
    docs = searcher.search(q2, reader.maxDoc()) // fetch all available docs to this query
    for (sd in docs.scoreDocs) {
      assertEquals(scores[sd.doc], sd.score, 0f, "score of doc=" + sd.doc + " modified")
    }
  }

  @Test
  fun testScoringNoBaseQuery() {
    // verify that drill-down queries (with no base query) returns 0.0 score
    var searcher = newSearcher(reader)

    var q = DrillDownQuery(config)
    q.add("a")
    var docs = searcher.search(q, reader.maxDoc()) // fetch all available docs to this query
    for (sd in docs.scoreDocs) {
      assertEquals(0f, sd.score, 0f)
    }
  }

  @Test
  fun testTermNonDefault() {
    var aField = config.getDimConfig("a").indexFieldName
    var termA = DrillDownQuery.term(aField, "a")
    assertEquals(Term(aField, "a"), termA)

    var bField = config.getDimConfig("b").indexFieldName
    var termB = DrillDownQuery.term(bField, "b")
    assertEquals(Term(bField, "b"), termB)
  }

  @Test
  fun testClone() {
    var q = DrillDownQuery(config, MatchAllDocsQuery())
    q.add("a")

    var clone = q.clone()
    clone.add("b")

    assertFalse(q.toString().equals(clone.toString()), "query wasn't cloned: source=" + q + " clone=" + clone)
  }

  @Test
  fun testNoDrillDown() {
    var base = MatchAllDocsQuery()
    var q = DrillDownQuery(config, base)
    var searcher = newSearcher(reader)
    var rewrite = q.rewrite(searcher).rewrite(searcher)
    assertEquals(base, rewrite)
  }

  @Test
  fun testSkipDrillDownTermsIndexing() {
    var dir = newDirectory()
    var writer =
        RandomIndexWriter(
            random(),
            dir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.KEYWORD, false)))
    var taxoDir = newDirectory()
    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var config = FacetsConfig()
    config.setDrillDownTermsIndexing("a", FacetsConfig.DrillDownTermsIndexing.FULL_PATH_ONLY)
    config.setDrillDownTermsIndexing("b", FacetsConfig.DrillDownTermsIndexing.FULL_PATH_ONLY)

    var doc = Document()
    doc.add(FacetField("a", "1"))
    doc.add(FacetField("b", "2"))
    writer.addDocument(config.build(taxoWriter, doc))
    taxoWriter.close()

    var reader = writer.reader
    var taxoReader = DirectoryTaxonomyReader(taxoDir)
    var searcher = newSearcher(reader)

    var q = DrillDownQuery(config)
    q.add("a")
    // no hits because we disabled dimension drill down completely
    assertEquals(0, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("a", "1")
    assertEquals(1, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("b")
    // no hits because we disabled dimension drill down completely
    assertEquals(0, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("b", "2")
    assertEquals(1, searcher.count(q))

    IOUtils.close(taxoReader, reader, writer, dir, taxoDir)
  }

  @Test
  fun testDrillDownTermsDifferentOptions() {
    var dir = newDirectory()
    var writer =
        RandomIndexWriter(
            random(),
            dir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.KEYWORD, false)))
    var taxoDir = newDirectory()
    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var config = FacetsConfig()
    config.setHierarchical("a", true)
    config.setHierarchical("b", true)
    config.setHierarchical("c", true)
    config.setHierarchical("d", true)
    config.setHierarchical("e", true)
    config.setDrillDownTermsIndexing("a", FacetsConfig.DrillDownTermsIndexing.NONE)
    config.setDrillDownTermsIndexing("b", FacetsConfig.DrillDownTermsIndexing.FULL_PATH_ONLY)
    config.setDrillDownTermsIndexing("c", FacetsConfig.DrillDownTermsIndexing.ALL_PATHS_NO_DIM)
    config.setDrillDownTermsIndexing(
        "d", FacetsConfig.DrillDownTermsIndexing.DIMENSION_AND_FULL_PATH)
    config.setDrillDownTermsIndexing("e", FacetsConfig.DrillDownTermsIndexing.ALL)
    config.setDrillDownTermsIndexing("f", FacetsConfig.DrillDownTermsIndexing.NONE)
    config.setIndexFieldName("f", "facet-for-f")

    var doc = Document()
    doc.add(FacetField("a", "a1", "a2", "a3"))
    doc.add(FacetField("b", "b1", "b2", "b3"))
    doc.add(FacetField("c", "c1", "c2", "c3"))
    doc.add(FacetField("d", "d1", "d2", "d3"))
    doc.add(FacetField("e", "e1", "e2", "e3"))
    doc.add(IntAssociationFacetField(5, "f", "f1"))
    writer.addDocument(config.build(taxoWriter, doc))
    taxoWriter.close()

    var reader = writer.reader
    var taxoReader = DirectoryTaxonomyReader(taxoDir)
    var searcher = newSearcher(reader)

    // Verifies for FacetsConfig.DrillDownTermsIndexing.NONE option
    var q = DrillDownQuery(config)
    q.add("a")
    assertEquals(0, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("a", "a1")
    assertEquals(0, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("a", "a1", "a2")
    assertEquals(0, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("a", "a1", "a2", "a3")
    assertEquals(0, searcher.count(q))

    // Verifies for FacetsConfig.DrillDownTermsIndexing.FULL_PATH_ONLY option
    q = DrillDownQuery(config)
    q.add("b")
    assertEquals(0, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("b", "b1")
    assertEquals(0, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("b", "b1", "b2")
    assertEquals(0, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("b", "b1", "b2", "b3")
    assertEquals(1, searcher.count(q))

    // Verifies for FacetsConfig.DrillDownTermsIndexing.ALL_PATHS_NO_DIM option
    q = DrillDownQuery(config)
    q.add("c")
    assertEquals(0, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("c", "c1")
    assertEquals(1, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("c", "c1", "c2")
    assertEquals(1, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("c", "c1", "c2", "c3")
    assertEquals(1, searcher.count(q))

    // Verifies for FacetsConfig.DrillDownTermsIndexing.DIMENSION_AND_FULL_PATH option
    q = DrillDownQuery(config)
    q.add("d")
    assertEquals(1, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("d", "d1")
    assertEquals(0, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("d", "d1", "d2")
    assertEquals(0, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("d", "d1", "d2", "d3")
    assertEquals(1, searcher.count(q))

    // Verifies for FacetsConfig.DrillDownTermsIndexing.ALL option
    q = DrillDownQuery(config)
    q.add("e")
    assertEquals(1, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("e", "e1")
    assertEquals(1, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("e", "e1", "e2")
    assertEquals(1, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("e", "e1", "e2", "e3")
    assertEquals(1, searcher.count(q))

    // Verifies for FacetsConfig.DrillDownTermsIndexing.DIMENSION_AND_FULL_PATH option with
    // IntAssociationFacetField
    q = DrillDownQuery(config)
    q.add("f")
    assertEquals(0, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("f", "f1")
    assertEquals(0, searcher.count(q))

    IOUtils.close(taxoReader, reader, writer, dir, taxoDir)
  }

  @Test
  fun testDrillDownTermsDefaultWithHierarchicalSetting() {
    var dir = newDirectory()
    var writer =
        RandomIndexWriter(
            random(),
            dir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.KEYWORD, false)))
    var taxoDir = newDirectory()
    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var config = FacetsConfig()
    config.setHierarchical("a", true)

    var doc = Document()
    doc.add(FacetField("a", "1", "2", "3"))
    doc.add(FacetField("b", "4"))
    writer.addDocument(config.build(taxoWriter, doc))
    taxoWriter.close()

    var reader = writer.reader
    var taxoReader = DirectoryTaxonomyReader(taxoDir)
    var searcher = newSearcher(reader)

    var q = DrillDownQuery(config)
    q.add("a")
    assertEquals(1, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("a", "1")
    assertEquals(1, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("a", "1", "2")
    assertEquals(1, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("a", "1", "2", "3")
    assertEquals(1, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("b")
    assertEquals(1, searcher.count(q))

    q = DrillDownQuery(config)
    q.add("b", "4")
    assertEquals(1, searcher.count(q))

    IOUtils.close(taxoReader, reader, writer, dir, taxoDir)
  }

  @Test
  fun testGetDrillDownQueries() {
    var q = DrillDownQuery(config)
    q.add("a", "1")
    q.add("b", "1")

    val drillDownQueries = q.getDrillDownQueries()
    val drillDownQueriesCopy = q.getDrillDownQueries()

    assert(drillDownQueries.contentEquals(drillDownQueriesCopy))

    q.add("c", "1")
    q.add("a", "2")
    q.add("a", "3")
    val drillDownQueriesModified = q.getDrillDownQueries()
    val drillDownQueriesModifiedCopy = q.getDrillDownQueries()

    // the cached builtDimQueries object is now stale
    assert(drillDownQueriesModified.contentEquals(drillDownQueriesCopy) == false)
    assert(drillDownQueriesModified.contentEquals(drillDownQueriesModifiedCopy))
  }
}
