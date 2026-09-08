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

import kotlin.test.*

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyWriter
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.util.IOUtils

class TestMultipleIndexFields : FacetTestCase() {

  private val CATEGORIES =
      arrayOf(
        FacetField("Author", "Mark Twain"),
        FacetField("Author", "Stephen King"),
        FacetField("Author", "Kurt Vonnegut"),
        FacetField("Band", "Rock & Pop", "The Beatles"),
        FacetField("Band", "Punk", "The Ramones"),
        FacetField("Band", "Rock & Pop", "U2"),
        FacetField("Band", "Rock & Pop", "REM"),
        FacetField("Band", "Rock & Pop", "Dave Matthews Band"),
        FacetField("Composer", "Bach"),
      )

  private fun getConfig(): FacetsConfig {
    val config = FacetsConfig()
    config.setHierarchical("Band", true)
    return config
  }

  @Test
  fun testDefault() {
    val indexDir = newDirectory()
    val taxoDir = newDirectory()

    // create and open an index writer
    val iw =
        RandomIndexWriter(
            random(),
            indexDir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)))
    // create and open a taxonomy writer
    val tw = DirectoryTaxonomyWriter(taxoDir, OpenMode.CREATE)
    val config = getConfig()

    seedIndex(tw, iw, config)

    val ir = iw.reader
    tw.commit()

    // prepare index reader and taxonomy.
    val tr = DirectoryTaxonomyReader(taxoDir)

    // prepare searcher to search against
    val searcher = newSearcher(ir)

    val sfc = performSearch(searcher)

    // Obtain facets results and hand-test them
    assertCorrectResults(getTaxonomyFacetCounts(tr, config, sfc))

    assertOrdinalsExist("\$facets", ir)

    iw.close()
    IOUtils.close(tr, ir, tw, indexDir, taxoDir)
  }

  @Test
  fun testCustom() {
    val indexDir = newDirectory()
    val taxoDir = newDirectory()

    // create and open an index writer
    val iw =
        RandomIndexWriter(
            random(),
            indexDir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)))
    // create and open a taxonomy writer
    val tw = DirectoryTaxonomyWriter(taxoDir, OpenMode.CREATE)

    val config = getConfig()
    config.setIndexFieldName("Author", "\$author")
    seedIndex(tw, iw, config)

    val ir = iw.reader
    tw.commit()

    // prepare index reader and taxonomy.
    val tr = DirectoryTaxonomyReader(taxoDir)

    // prepare searcher to search against
    val searcher = newSearcher(ir)

    val sfc = performSearch(searcher)

    val facetsMap = HashMap<String, Facets>()
    facetsMap.put("Author", getTaxonomyFacetCounts(tr, config, sfc, "\$author"))
    val facets = MultiFacets(facetsMap, getTaxonomyFacetCounts(tr, config, sfc))

    // Obtain facets results and hand-test them
    assertCorrectResults(facets)

    assertOrdinalsExist("\$facets", ir)
    assertOrdinalsExist("\$author", ir)

    iw.close()
    IOUtils.close(tr, ir, tw, indexDir, taxoDir)
  }

  @Test
  fun testTwoCustomsSameField() {
    val indexDir = newDirectory()
    val taxoDir = newDirectory()

    // create and open an index writer
    val iw =
        RandomIndexWriter(
            random(),
            indexDir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)))
    // create and open a taxonomy writer
    val tw = DirectoryTaxonomyWriter(taxoDir, OpenMode.CREATE)

    val config = getConfig()
    config.setIndexFieldName("Band", "\$music")
    config.setIndexFieldName("Composer", "\$music")
    seedIndex(tw, iw, config)

    val ir = iw.reader
    tw.commit()

    // prepare index reader and taxonomy.
    val tr = DirectoryTaxonomyReader(taxoDir)

    // prepare searcher to search against
    val searcher = newSearcher(ir)

    val sfc = performSearch(searcher)

    val facetsMap = HashMap<String, Facets>()
    val facets2 = getTaxonomyFacetCounts(tr, config, sfc, "\$music")
    facetsMap.put("Band", facets2)
    facetsMap.put("Composer", facets2)
    val facets = MultiFacets(facetsMap, getTaxonomyFacetCounts(tr, config, sfc))

    // Obtain facets results and hand-test them
    assertCorrectResults(facets)

    assertOrdinalsExist("\$facets", ir)
    assertOrdinalsExist("\$music", ir)
    assertOrdinalsExist("\$music", ir)

    iw.close()
    IOUtils.close(tr, ir, tw, indexDir, taxoDir)
  }

  private fun assertOrdinalsExist(field: String, ir: IndexReader) {
    for (context in ir.leaves()) {
      val r = context.reader()
      if (r.getSortedNumericDocValues(field) != null) {
        return // not all segments must have this DocValues
      }
    }
    fail("no ordinals found for " + field)
  }

  @Test
  fun testDifferentFieldsAndText() {
    val indexDir = newDirectory()
    val taxoDir = newDirectory()

    // create and open an index writer
    val iw =
        RandomIndexWriter(
            random(),
            indexDir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)))
    // create and open a taxonomy writer
    val tw = DirectoryTaxonomyWriter(taxoDir, OpenMode.CREATE)

    val config = getConfig()
    config.setIndexFieldName("Band", "\$bands")
    config.setIndexFieldName("Composer", "\$composers")
    seedIndex(tw, iw, config)

    val ir = iw.reader
    tw.commit()

    // prepare index reader and taxonomy.
    val tr = DirectoryTaxonomyReader(taxoDir)

    // prepare searcher to search against
    val searcher = newSearcher(ir)

    val sfc = performSearch(searcher)

    val facetsMap = HashMap<String, Facets>()
    facetsMap.put("Band", getTaxonomyFacetCounts(tr, config, sfc, "\$bands"))
    facetsMap.put("Composer", getTaxonomyFacetCounts(tr, config, sfc, "\$composers"))
    val facets = MultiFacets(facetsMap, getTaxonomyFacetCounts(tr, config, sfc))

    // Obtain facets results and hand-test them
    assertCorrectResults(facets)
    assertOrdinalsExist("\$facets", ir)
    assertOrdinalsExist("\$bands", ir)
    assertOrdinalsExist("\$composers", ir)

    iw.close()
    IOUtils.close(tr, ir, tw, indexDir, taxoDir)
  }

  @Test
  fun testSomeSameSomeDifferent() {
    val indexDir = newDirectory()
    val taxoDir = newDirectory()

    // create and open an index writer
    val iw =
        RandomIndexWriter(
            random(),
            indexDir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)))
    // create and open a taxonomy writer
    val tw = DirectoryTaxonomyWriter(taxoDir, OpenMode.CREATE)

    val config = getConfig()
    config.setIndexFieldName("Band", "\$music")
    config.setIndexFieldName("Composer", "\$music")
    config.setIndexFieldName("Author", "\$literature")
    seedIndex(tw, iw, config)

    val ir = iw.reader
    tw.commit()

    // prepare index reader and taxonomy.
    val tr = DirectoryTaxonomyReader(taxoDir)

    // prepare searcher to search against
    val searcher = newSearcher(ir)

    val sfc = performSearch(searcher)

    val facetsMap = HashMap<String, Facets>()
    val facets2 = getTaxonomyFacetCounts(tr, config, sfc, "\$music")
    facetsMap.put("Band", facets2)
    facetsMap.put("Composer", facets2)
    facetsMap.put("Author", getTaxonomyFacetCounts(tr, config, sfc, "\$literature"))
    val facets = MultiFacets(facetsMap, getTaxonomyFacetCounts(tr, config, sfc))

    // Obtain facets results and hand-test them
    assertCorrectResults(facets)
    assertOrdinalsExist("\$music", ir)
    assertOrdinalsExist("\$literature", ir)

    iw.close()
    IOUtils.close(tr, ir, iw, tw, indexDir, taxoDir)
  }

  private fun assertCorrectResults(facets: Facets) {
    assertEquals<Any?>(5, facets.getSpecificValue("Band"))
    assertEquals(
        "dim=Band path=[] value=5 childCount=2\n  Rock & Pop (4)\n  Punk (1)\n",
        facets.getTopChildren(10, "Band").toString())
    assertFacetResult(
        facets.getAllChildren("Band")!!,
        "Band",
        emptyArray<String>(),
        2,
        5,
        LabelAndValue("Punk", 1),
        LabelAndValue("Rock & Pop", 4))
    assertEquals(
        "dim=Band path=[Rock & Pop] value=4 childCount=4\n  The Beatles (1)\n  U2 (1)\n  REM (1)\n  Dave Matthews Band (1)\n",
        facets.getTopChildren(10, "Band", "Rock & Pop").toString())
    assertFacetResult(
        facets.getAllChildren("Band", "Rock & Pop")!!,
        "Band",
        arrayOf("Rock & Pop"),
        4,
        4,
        LabelAndValue("Dave Matthews Band", 1),
        LabelAndValue("REM", 1),
        LabelAndValue("The Beatles", 1),
        LabelAndValue("U2", 1))

    assertEquals(
        "dim=Author path=[] value=3 childCount=3\n  Mark Twain (1)\n  Stephen King (1)\n  Kurt Vonnegut (1)\n",
        facets.getTopChildren(10, "Author").toString())
    assertFacetResult(
        facets.getAllChildren("Author")!!,
        "Author",
        emptyArray<String>(),
        3,
        3,
        LabelAndValue("Kurt Vonnegut", 1),
        LabelAndValue("Mark Twain", 1),
        LabelAndValue("Stephen King", 1))
  }

  private fun performSearch(searcher: IndexSearcher): FacetsCollector {
    return FacetsCollectorManager.search(
            searcher, MatchAllDocsQuery(), 10, FacetsCollectorManager())
        .facetsCollector
  }

  private fun seedIndex(tw: TaxonomyWriter, iw: RandomIndexWriter, config: FacetsConfig) {
    for (ff in CATEGORIES) {
      val doc = Document()
      doc.add(ff)
      doc.add(TextField("content", "alpha", Field.Store.YES))
      iw.addDocument(config.build(tw, doc))
    }
  }
}
