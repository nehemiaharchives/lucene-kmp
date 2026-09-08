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
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.facet.FacetField
import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.ReindexingEnrichedDirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.util.IOUtils
import okio.IOException
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals

class TestOrdinalData : FacetTestCase() {
    private lateinit var taxoDir: Directory
    private lateinit var taxoReader: DirectoryTaxonomyReader
    private lateinit var taxoIndexReader: IndexReader
    private lateinit var taxoWriter: ReindexingEnrichedDirectoryTaxonomyWriter

    private data class OrdinalDataAppender(val scores: Map<String, Long>) {
        fun accept(facetLabel: FacetLabel, doc: Document) {
            if (facetLabel.length == 0) return
            val score = scores[facetLabel.components[facetLabel.length - 1]]
            if (score != null) {
                doc.add(NumericDocValuesField("score", score))
                doc.add(StringField("hasScore?", "yes", Field.Store.NO))
            } else {
                doc.add(StringField("hasScore?", "no", Field.Store.NO))
            }
        }
    }

    @BeforeTest
    fun setUp() {
        val indexDir = newDirectory()
        taxoDir = newDirectory()
        val indexWriter = IndexWriter(indexDir, IndexWriterConfig())
        taxoWriter = ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, OrdinalDataAppender(labelToScore)::accept)
        val facetsConfig = FacetsConfig()
        facetsConfig.setHierarchical("Author", true)
        facetsConfig.setMultiValued("Author", true)
        facetsConfig.setHierarchical("Publish Date", true)
        facetsConfig.setMultiValued("Publish Date", true)
        var doc: Document
        doc = Document()
        doc.add(FacetField("Author", "Bob"))
        doc.add(FacetField("Publish Date", "2010", "10", "15"))
        indexWriter.addDocument(facetsConfig.build(taxoWriter, doc))
        doc = Document()
        doc.add(FacetField("Author", "Lisa"))
        doc.add(FacetField("Publish Date", "2010", "10", "20"))
        indexWriter.addDocument(facetsConfig.build(taxoWriter, doc))
        IOUtils.close(indexWriter, indexDir)
        taxoWriter.commit()
        taxoReader = DirectoryTaxonomyReader(taxoDir)
        taxoIndexReader = taxoReader.getInternalIndexReader()
    }

    @AfterTest
    fun tearDown() {
        IOUtils.close(taxoWriter)
        IOUtils.close(taxoReader)
        IOUtils.close(taxoDir)
    }

    @Test
    fun testDocValue() {
        // Each unique label will have been assigned a doc.
        // Additionally, we have the root node of the taxonomy.
        assertEquals(9, taxoIndexReader.maxDoc())
        for (ctx in taxoIndexReader.leaves()) {
            val leafReader = ctx.reader()
            val scores = leafReader.getNumericDocValues("score")
            if (scores == null) continue
            for (ord in 0 until leafReader.maxDoc()) {
                if (scores.advanceExact(ord) == false) continue
                val label = taxoReader.getPath(ctx.docBase + ord)
                val score = labelToScore[label.components[label.length - 1]]
                if (score == null) throw IOException("Unexpected score for ${label.components.contentToString()}")
                assertEquals(score, scores.longValue())
            }
        }
    }

    private fun validateSearchResults(searcher: IndexSearcher, queriesAndCounts: Map<Query, Int>) {
        for (queryAndCount in queriesAndCounts.entries) {
            val q = queryAndCount.key
            val count = queryAndCount.value
            val td = searcher.search(q, Int.MAX_VALUE)
            assertEquals(count.toLong(), td.totalHits.value)
        }
    }

    @Test
    fun testSearchableField() {
        val taxoSearcher = newSearcher(taxoIndexReader)
        validateSearchResults(taxoSearcher, mapOf(
            TermQuery(Term("hasScore?", "yes")) to 2,
            TermQuery(Term("hasScore?", "no")) to 6
        ))
    }

    @Test
    fun testReindex() {
        taxoWriter.reindexWithNewOrdinalData(OrdinalDataAppender(HashMap())::accept)
        taxoReader.close()
        taxoReader = DirectoryTaxonomyReader(taxoDir)
        taxoIndexReader = taxoReader.getInternalIndexReader()
        val taxoSearcher = newSearcher(taxoIndexReader)
        validateSearchResults(taxoSearcher, mapOf(
            TermQuery(Term("hasScore?", "yes")) to 0,
            TermQuery(Term("hasScore?", "no")) to 8
        ))
    }

    companion object {
        private val labelToScore = mapOf("Bob" to 42L, "Lisa" to 35L)
    }
}
