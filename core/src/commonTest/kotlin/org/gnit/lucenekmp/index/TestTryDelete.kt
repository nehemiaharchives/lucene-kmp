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

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.ReferenceManager
import org.gnit.lucenekmp.search.SearcherFactory
import org.gnit.lucenekmp.search.SearcherManager
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestTryDelete : LuceneTestCase() {
    companion object {
        @Throws(IOException::class)
        private fun getWriter(directory: Directory): IndexWriter {
            val policy: MergePolicy = LogByteSizeMergePolicy()
            val conf = IndexWriterConfig(MockAnalyzer(random()))
            conf.setMergePolicy(policy)
            conf.setOpenMode(OpenMode.CREATE_OR_APPEND)

            return IndexWriter(directory, conf)
        }

        @Throws(IOException::class)
        private fun createIndex(): Directory {
            val directory: Directory = ByteBuffersDirectory()

            val writer = getWriter(directory)

            for (i in 0..<10) {
                val doc = Document()
                doc.add(StringField("foo", i.toString(), Field.Store.YES))
                writer.addDocument(doc)
            }

            writer.commit()
            writer.close()

            return directory
        }
    }

    @Test
    @Throws(IOException::class)
    fun testTryDeleteDocument() {
        val directory = createIndex()

        val writer = getWriter(directory)

        val mgr: ReferenceManager<IndexSearcher> = SearcherManager(writer, SearcherFactory())

        var searcher = mgr.acquire()

        var topDocs: TopDocs = searcher.search(TermQuery(Term("foo", "0")), 100)
        assertEquals(1, topDocs.totalHits.value)

        val result: Long =
            if (random().nextBoolean()) {
                val r = DirectoryReader.open(writer)
                try {
                    writer.tryDeleteDocument(r, 0)
                } finally {
                    r.close()
                }
            } else {
                writer.tryDeleteDocument(searcher.indexReader, 0)
            }

        assertTrue(result != -1L)

        assertTrue(writer.hasDeletions())

        if (random().nextBoolean()) {
            writer.commit()
        }

        assertTrue(writer.hasDeletions())

        mgr.release(searcher)
        mgr.maybeRefresh()

        searcher = mgr.acquire()

        topDocs = searcher.search(TermQuery(Term("foo", "0")), 100)

        assertEquals(0, topDocs.totalHits.value)
        mgr.release(searcher)
        mgr.close()
        writer.close()
        directory.close()
    }

    @Test
    @Throws(IOException::class)
    fun testTryDeleteDocumentCloseAndReopen() {
        val directory = createIndex()

        val writer = getWriter(directory)

        val mgr: ReferenceManager<IndexSearcher> = SearcherManager(writer, SearcherFactory())

        var searcher = mgr.acquire()

        var topDocs: TopDocs = searcher.search(TermQuery(Term("foo", "0")), 100)
        assertEquals(1, topDocs.totalHits.value)

        val result = DirectoryReader.open(writer).use { reader ->
            writer.tryDeleteDocument(reader, 0)
        }

        assertTrue(result != -1L)

        writer.commit()

        assertTrue(writer.hasDeletions())

        mgr.release(searcher)
        mgr.maybeRefresh()

        searcher = mgr.acquire()

        topDocs = searcher.search(TermQuery(Term("foo", "0")), 100)

        assertEquals(0, topDocs.totalHits.value)
        mgr.release(searcher)
        mgr.close()

        writer.close()

        searcher = IndexSearcher(DirectoryReader.open(directory))

        topDocs = searcher.search(TermQuery(Term("foo", "0")), 100)

        assertEquals(0, topDocs.totalHits.value)
        searcher.indexReader.close()
        directory.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDeleteDocuments() {
        val directory = createIndex()

        val writer = getWriter(directory)

        val mgr: ReferenceManager<IndexSearcher> = SearcherManager(writer, SearcherFactory())

        var searcher = mgr.acquire()

        var topDocs: TopDocs = searcher.search(TermQuery(Term("foo", "0")), 100)
        assertEquals(1, topDocs.totalHits.value)

        val result = writer.deleteDocuments(TermQuery(Term("foo", "0")))

        assertTrue(result != -1L)

        assertTrue(writer.hasDeletions())

        mgr.release(searcher)
        mgr.maybeRefresh()

        searcher = mgr.acquire()

        topDocs = searcher.search(TermQuery(Term("foo", "0")), 100)

        assertEquals(0, topDocs.totalHits.value)
        mgr.release(searcher)
        mgr.close()
        writer.close()
        directory.close()
    }
}
