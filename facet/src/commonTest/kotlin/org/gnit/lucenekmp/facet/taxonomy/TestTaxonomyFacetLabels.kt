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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.facet.*
import org.gnit.lucenekmp.facet.FacetsCollector.MatchingDocs
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.*

class TestTaxonomyFacetLabels : FacetTestCase() {

  private fun prepareDocuments(): List<Document> {
    val docs = ArrayList<Document>()

    var doc = Document()
    doc.add(FacetField("Author", "Bob"))
    doc.add(FacetField("Publish Date", "2010", "10", "15"))
    docs.add(doc)

    doc = Document()
    doc.add(FacetField("Author", "Lisa"))
    doc.add(FacetField("Publish Date", "2010", "10", "20"))
    docs.add(doc)

    doc = Document()
    doc.add(FacetField("Author", "Tom"))
    doc.add(FacetField("Publish Date", "2012", "1", "1"))
    docs.add(doc)

    doc = Document()
    doc.add(FacetField("Author", "Susan"))
    doc.add(FacetField("Publish Date", "2012", "1", "7"))
    docs.add(doc)

    doc = Document()
    doc.add(FacetField("Author", "Frank"))
    doc.add(FacetField("Publish Date", "1999", "5", "5"))
    docs.add(doc)

    return docs
  }

  private fun allDocIds(m: MatchingDocs, decreasingDocIds: Boolean): List<Int> {
    val disi = m.bits.iterator()!!
    val docIds = ArrayList<Int>()
    while (disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
      docIds.add(disi.docID())
    }

    if (decreasingDocIds == true) {
      docIds.reverse()
    }
    return docIds
  }

  private fun lookupFacetLabels(taxoLabels: TaxonomyFacetLabels, matchingDocs: List<MatchingDocs>): List<FacetLabel> {
    return lookupFacetLabels(taxoLabels, matchingDocs, null, false)
  }

  private fun lookupFacetLabels(taxoLabels: TaxonomyFacetLabels, matchingDocs: List<MatchingDocs>, dimension: String?): List<FacetLabel> {
    return lookupFacetLabels(taxoLabels, matchingDocs, dimension, false)
  }

  private fun lookupFacetLabels(taxoLabels: TaxonomyFacetLabels, matchingDocs: List<MatchingDocs>, dimension: String?, decreasingDocIds: Boolean): List<FacetLabel> {
    val facetLabels = ArrayList<FacetLabel>()

    for (m in matchingDocs) {
      val facetLabelReader =
          taxoLabels.getFacetLabelReader(m.context)
      val docIds = allDocIds(m, decreasingDocIds)
      var facetLabel: FacetLabel?
      for (docId in docIds) {
        while (true) {
          if (dimension != null) {
            facetLabel = facetLabelReader.nextFacetLabel(docId, dimension)
          } else {
            facetLabel = facetLabelReader.nextFacetLabel(docId)
          }

          if (facetLabel == null) {
            break
          }
          facetLabels.add(facetLabel)
        }
      }
    }

    return facetLabels
  }

  @Test
  fun testBasic() {
    val dir = newDirectory()
    val taxoDir = newDirectory()

    // Writes facet ords to a separate directory from the main index:
    val taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)
    val writer = RandomIndexWriter(random(), dir)

    val config = FacetsConfig()
    config.setHierarchical("Publish Date", true)

    for (doc in prepareDocuments()) {
      writer.addDocument(config.build(taxoWriter, doc))
    }

    // NRT open
    val searcher = newSearcher(writer.reader)
    // NRT open
    val taxoReader = DirectoryTaxonomyReader(taxoWriter)

    val fc = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    val taxoLabels =
        TaxonomyFacetLabels(taxoReader, FacetsConfig.DEFAULT_INDEX_FIELD_NAME)

    // Check labels for all dimensions
    var facetLabels = lookupFacetLabels(taxoLabels, fc.getMatchingDocs())
    assertEquals(10, facetLabels.size, "Incorrect number of facet labels received")

    // Check labels for all dimensions
    assertTrue(
        facetLabels
            .filter { l -> "Author" == l.components[0] }
            .map { l -> l.components[1] }
            .toSet()
            .equals(setOf("Bob", "Lisa", "Susan", "Frank", "Tom")))

    assertTrue(
        facetLabels
            .filter { l -> "Publish Date" == l.components[0] }
            .map { l -> listOf(l.components[1], l.components[2], l.components[3]).joinToString("/") }
            .toSet()
            .equals(setOf("2010/10/15", "2010/10/20", "2012/1/1", "2012/1/7", "1999/5/5")))

    // Check labels for a specific dimension
    facetLabels = lookupFacetLabels(taxoLabels, fc.getMatchingDocs(), "Publish Date")
    assertEquals(5, facetLabels.size, "Incorrect number of facet labels received for 'Publish Date'")

    assertTrue(
        facetLabels
            .map { l -> listOf(l.components[1], l.components[2], l.components[3]).joinToString("/") }
            .toSet()
            .equals(setOf("2010/10/15", "2010/10/20", "2012/1/1", "2012/1/7", "1999/5/5")))

    try {
      facetLabels = lookupFacetLabels(taxoLabels, fc.getMatchingDocs(), null, true)
      fail("IllegalArgumentException was not thrown for using docIds supplied in decreasing order")
    } catch (ae: IllegalArgumentException) {
      assertTrue(ae.message!!.contains("docs out of order"))
    }

    try {
      facetLabels = lookupFacetLabels(taxoLabels, fc.getMatchingDocs(), "Publish Date", true)
      fail("Assertion error was not thrown for using docIds supplied in decreasing order")
    } catch (ae: IllegalArgumentException) {
      assertTrue(ae.message!!.contains("docs out of order"))
    }

    writer.close()
    IOUtils.close(taxoWriter, searcher.indexReader, taxoReader, taxoDir, dir)
  }
}
