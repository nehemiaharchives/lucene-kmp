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
package org.gnit.lucenekmp.index

import okio.Path
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class TestReadOnlyIndex : LuceneTestCase() {

    private val longTerm =
        "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm"
    private val text = "This is the text to be indexed. $longTerm"

    private var indexPath: Path? = null

    @BeforeTest
    fun buildIndex() {
        indexPath = createTempDir("readonlyindex")

        // borrows from TestDemo, but not important to keep in sync with demo
        val analyzer = MockAnalyzer(random())
        val directory: Directory = newFSDirectory(indexPath!!)
        val iwriter = RandomIndexWriter(random(), directory, analyzer)
        val doc = Document()
        doc.add(newTextField("fieldname", text, Field.Store.YES))
        iwriter.addDocument(doc)
        iwriter.close()
        directory.close()
        analyzer.close()
    }

    @AfterTest
    fun afterClass() {
        indexPath = null
    }

    @Ignore // TODO runWithRestrictedPermissions is not ported in KMP test-framework yet
    @Test
    fun testReadOnlyIndex() {
        doTestReadOnlyIndex()
    }

    private fun doTestReadOnlyIndex() {
        val dir = FSDirectory.open(indexPath!!)
        val ireader = DirectoryReader.open(dir)
        val isearcher: IndexSearcher = newSearcher(ireader)

        // borrows from TestDemo, but not important to keep in sync with demo

        assertEquals(1, isearcher.count(TermQuery(Term("fieldname", longTerm))))
        val query: Query = TermQuery(Term("fieldname", "text"))
        val hits: TopDocs = isearcher.search(query, 1)
        assertEquals(1, hits.totalHits.value.toInt())
        // Iterate through the results:
        val storedFields: StoredFields = isearcher.storedFields()
        for (i in hits.scoreDocs.indices) {
            val hitDoc: Document = storedFields.document(hits.scoreDocs[i].doc)
            assertEquals(text, hitDoc.get("fieldname"))
        }

        // Test simple phrase query
        val phraseQuery = PhraseQuery("fieldname", "to", "be")
        assertEquals(1, isearcher.count(phraseQuery))

        ireader.close()
        dir.close()
    }
}
