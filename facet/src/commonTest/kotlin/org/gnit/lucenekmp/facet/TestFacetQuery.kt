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
import org.gnit.lucenekmp.facet.sortedset.SortedSetDocValuesFacetField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestFacetQuery : FacetTestCase() {
    private var indexDirectory: Directory? = null
    private var indexWriter: RandomIndexWriter? = null
    private var indexReader: IndexReader? = null
    private var searcher: IndexSearcher? = null
    private var config: FacetsConfig? = null

    private val DOC_SINGLEVALUED = arrayOf<IndexableField>(SortedSetDocValuesFacetField("Author", "Mark Twain"))
    private val DOC_MULTIVALUED = arrayOf<IndexableField>(SortedSetDocValuesFacetField("Author", "Kurt Vonnegut"))
    private val DOC_NOFACET = arrayOf<IndexableField>(TextField("Hello", "World", Field.Store.YES))

    // kotlin.test has no common BeforeClass/AfterClass; use the same fixture per test.
    @BeforeTest
    fun createTestIndex() {
        indexDirectory = newDirectory()
        // create and open an index writer
        indexWriter = RandomIndexWriter(random(), indexDirectory!!,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)))
        config = FacetsConfig()
        indexDocuments(DOC_SINGLEVALUED, DOC_MULTIVALUED, DOC_NOFACET)
        indexReader = indexWriter!!.reader
        // prepare searcher to search against
        searcher = newSearcher(indexReader!!)
    }

    private fun indexDocuments(vararg docs: Array<IndexableField>) {
        for (fields in docs) {
            for (field in fields) {
                val doc = Document()
                doc.add(field)
                indexWriter!!.addDocument(config!!.build(doc))
            }
        }
    }

    @AfterTest
    fun closeTestIndex() {
        IOUtils.close(indexReader, indexWriter, indexDirectory)
        indexReader = null
        indexWriter = null
        indexDirectory = null
        searcher = null
        config = null
    }

    @Test
    fun testSingleValued() {
        val topDocs = searcher!!.search(FacetQuery("Author", "Mark Twain"), 10)
        assertEquals(1L, topDocs.totalHits.value)
    }

    @Test
    fun testMultiValued() {
        val topDocs = searcher!!.search(MultiFacetQuery("Author", arrayOf("Mark Twain"), arrayOf("Kurt Vonnegut")), 10)
        assertEquals(2L, topDocs.totalHits.value)
    }
}
