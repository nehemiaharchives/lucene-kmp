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

import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
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
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter.MemoryOrdinalMap
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MultiDocValues
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestOrdinalMappingLeafReader : FacetTestCase() {

  companion object { private const val NUM_DOCS = 100 }
  private val facetConfig = FacetsConfig()

  @BeforeTest
  fun setUp() {
    facetConfig.setMultiValued("tag", true)
    facetConfig.setIndexFieldName("tag", "\$tags") // add custom index field name
  }

  @Test
  fun testTaxonomyMergeUtils() {
    val srcIndexDir = newDirectory()
    val srcTaxoDir = newDirectory()
    buildIndexWithFacets(srcIndexDir, srcTaxoDir, true)

    val targetIndexDir = newDirectory()
    val targetTaxoDir = newDirectory()
    buildIndexWithFacets(targetIndexDir, targetTaxoDir, false)

    val destIndexWriter = IndexWriter(targetIndexDir, newIndexWriterConfig())
    val destTaxoWriter = DirectoryTaxonomyWriter(targetTaxoDir)
    try {
      TaxonomyMergeUtils.merge(
          srcIndexDir,
          srcTaxoDir,
          MemoryOrdinalMap(),
          destIndexWriter,
          destTaxoWriter,
          facetConfig)
    } finally {
      IOUtils.close(destIndexWriter, destTaxoWriter)
    }
    verifyResults(targetIndexDir, targetTaxoDir)

    IOUtils.close(targetIndexDir, targetTaxoDir, srcIndexDir, srcTaxoDir)
  }

  private fun verifyResults(indexDir: Directory, taxoDir: Directory)  {
    val indexReader = DirectoryReader.open(indexDir)
    val taxoReader = DirectoryTaxonomyReader(taxoDir)
    val searcher = newSearcher(indexReader)

    val collector =
        FacetsCollectorManager.search(
                searcher, MatchAllDocsQuery(), 10, FacetsCollectorManager())
            .facetsCollector

    // tag facets
    val tagFacets = FastTaxonomyFacetCounts("\$tags", taxoReader, facetConfig, collector)
    val result = tagFacets.getTopChildren(10, "tag")!!
    for (lv in result.labelValues) {
      if (VERBOSE) {
        println(lv)
      }
      assertEquals(NUM_DOCS, lv.value.toInt())
    }

    // id facets
    val idFacets = FastTaxonomyFacetCounts(taxoReader, facetConfig, collector)
    val idResult = idFacets.getTopChildren(10, "id")!!
    assertEquals(NUM_DOCS, idResult.childCount)
    assertEquals(NUM_DOCS * 2, idResult.value) // each "id" appears twice

    val bdv = MultiDocValues.getBinaryValues(indexReader, "bdv")!!
    val cbdv = MultiDocValues.getBinaryValues(indexReader, "cbdv")!!
    for (i in 0 until indexReader.maxDoc()) {
      assertEquals(i, bdv.nextDoc())
      assertEquals(i, cbdv.nextDoc())
      assertEquals(
          cbdv.binaryValue()!!.utf8ToString().toInt(),
          bdv.binaryValue()!!.utf8ToString().toInt() * 2)
    }
    IOUtils.close(indexReader, taxoReader)
  }

  private fun buildIndexWithFacets(indexDir: Directory, taxoDir: Directory, asc: Boolean)
       {
    val config = newIndexWriterConfig()
    val writer = RandomIndexWriter(random(), indexDir, config)

    val taxonomyWriter = DirectoryTaxonomyWriter(taxoDir)
    for (i in 1..NUM_DOCS) {
      val doc = Document()
      for (j in i..NUM_DOCS) {
        val facetValue = if (asc) j else NUM_DOCS - j
        doc.add(FacetField("tag", (facetValue).toString()))
      }
      // add a facet under default dim config
      doc.add(FacetField("id", (i).toString()))

      // make sure OrdinalMappingLeafReader ignores non-facet BinaryDocValues fields
      doc.add(BinaryDocValuesField("bdv", BytesRef((i).toString())))
      doc.add(BinaryDocValuesField("cbdv", BytesRef((i * 2).toString())))
      writer.addDocument(facetConfig.build(taxonomyWriter, doc))
    }
    taxonomyWriter.commit()
    taxonomyWriter.close()
    writer.commit()
    writer.close()
  }
}
