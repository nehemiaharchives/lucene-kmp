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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.jdkport.RejectedExecutionException
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermRangeQuery
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.OwnCacheKeyMultiReader
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TestReaderClosed : LuceneTestCase() {
    private var reader: DirectoryReader? = null
    private var dir: Directory? = null

    @BeforeTest
    fun setUp() {
        dir = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                dir!!,
                newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.KEYWORD, false))
                    .setMaxBufferedDocs(TestUtil.nextInt(random(), 50, 1000))
            )

        val doc = Document()
        val field = newStringField("field", "", Field.Store.NO)
        doc.add(field)

        // we generate aweful prefixes: good for testing.
        // but for preflex codec, the test can be very slow, so use less iterations.
        val num = atLeast(10)
        for (i in 0..<num) {
            field.setStringValue(TestUtil.randomUnicodeString(random(), 10))
            writer.addDocument(doc)
        }
        writer.forceMerge(1)
        reader = writer.getReader(true, false)
        writer.close()
    }

    @Test
    fun test() {
        assertTrue(reader!!.getRefCount() > 0)
        val searcher: IndexSearcher = newSearcher(reader!!)
        val query = TermRangeQuery.newStringRange("field", "a", "z", true, true)
        searcher.search(query, 5)
        reader!!.close()
        try {
            searcher.search(query, 5)
            fail("expected AlreadyClosedException or RejectedExecutionException")
        } catch (_: AlreadyClosedException) {
            // expected
        } catch (_: RejectedExecutionException) {
            // expected if the searcher has been created with threads since LuceneTestCase
            // closes the thread-pool in a reader close listener
        }
    }

    // LUCENE-3800
    @Test
    fun testReaderChaining() {
        assertTrue(reader!!.getRefCount() > 0)
        val wrappedReader: LeafReader = ParallelLeafReader(getOnlyLeafReader(reader!!))

        // We wrap with a OwnCacheKeyMultiReader so that closing the underlying reader
        // does not terminate the threadpool (if that index searcher uses one)
        val searcher = newSearcher(OwnCacheKeyMultiReader(wrappedReader))

        val query = TermRangeQuery.newStringRange("field", "a", "z", true, true)
        searcher.search(query, 5)
        reader!!.close() // close original child reader
        try {
            searcher.search(query, 5)
            fail("expected AlreadyClosedException")
        } catch (e: Exception) {
            var ace: AlreadyClosedException? = null
            var t: Throwable? = e
            while (t != null) {
                if (t is AlreadyClosedException) {
                    ace = t
                }
                t = t.cause
            }
            if (ace == null) {
                throw AssertionError("Query failed, but not due to an AlreadyClosedException", e)
            }
            assertEquals(
                "this IndexReader cannot be used anymore as one of its child readers was closed",
                ace.message
            )
        } finally {
            // close executor: in case of wrap-wrap-wrapping
            searcher.indexReader.close()
        }
    }

    @AfterTest
    fun tearDown() {
        dir!!.close()
    }
}
