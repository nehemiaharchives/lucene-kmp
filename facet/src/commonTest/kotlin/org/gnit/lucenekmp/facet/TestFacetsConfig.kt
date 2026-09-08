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
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class TestFacetsConfig : FacetTestCase() {
    @Test
    fun testPathToStringAndBack() {
        val iters = atLeast(1000)
        for (i in 0 until iters) {
            val numParts = TestUtil.nextInt(random(), 1, 6)
            val parts = Array(numParts) { "" }
            for (j in 0 until numParts) {
                var s: String
                while (true) {
                    s = TestUtil.randomUnicodeString(random())
                    if (s.length > 0) break
                }
                parts[j] = s
            }
            val s = FacetsConfig.pathToString(parts)
            val parts2 = FacetsConfig.stringToPath(s)
            assertTrue(parts.contentEquals(parts2))
        }
    }

    @Test
    fun testAddSameDocTwice() {
        // LUCENE-5367: this was a problem with the previous code, making sure it
        // works with the new code.
        val indexDir = newDirectory()
        val taxoDir = newDirectory()
        val indexWriter = IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))
        val taxoWriter = DirectoryTaxonomyWriter(taxoDir)
        val facetsConfig = FacetsConfig()
        var doc = Document()
        doc.add(FacetField("a", "b"))
        doc = facetsConfig.build(taxoWriter, doc)
        // these two addDocument() used to fail
        indexWriter.addDocument(doc)
        indexWriter.addDocument(doc)
        indexWriter.close()
        IOUtils.close(taxoWriter)

        val indexReader = DirectoryReader.open(indexDir)
        val taxoReader = DirectoryTaxonomyReader(taxoDir)
        val searcher = newSearcher(indexReader)
        val fc = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

        val facets = getTaxonomyFacetCounts(taxoReader, facetsConfig, fc)
        val res = facets.getTopChildren(10, "a")!!
        assertEquals(1, res.labelValues.size)
        assertEquals(2, res.labelValues[0].value)
        IOUtils.close(indexReader, taxoReader)
        IOUtils.close(indexDir, taxoDir)
    }

    @Test
    fun testIsDimConfigured() {
        val config = FacetsConfig()
        config.setIndexFieldName("configured1", "name")
        config.setMultiValued("configured2", true)
        config.setRequireDimCount("configured3", true)
        config.setHierarchical("configured4", true)
        config.setDrillDownTermsIndexing("configured5", FacetsConfig.DrillDownTermsIndexing.ALL)
        assertTrue(config.isDimConfigured("configured1"))
        assertTrue(config.isDimConfigured("configured2"))
        assertTrue(config.isDimConfigured("configured3"))
        assertTrue(config.isDimConfigured("configured4"))
        assertTrue(config.isDimConfigured("configured5"))
        assertFalse(config.isDimConfigured("unconfigured"))
    }

    /** LUCENE-5479 */
    @Test
    fun testCustomDefault() {
        val config = object : FacetsConfig() {
            override fun getDefaultDimConfig(): DimConfig {
                val config = DimConfig()
                config.hierarchical = true
                return config
            }
        }
        assertTrue(config.getDimConfig("foobar").hierarchical)
    }
}
