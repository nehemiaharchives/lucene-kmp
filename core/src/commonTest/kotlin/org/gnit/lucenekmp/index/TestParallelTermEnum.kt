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
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestParallelTermEnum : LuceneTestCase() {
    private lateinit var ir1: LeafReader
    private lateinit var ir2: LeafReader
    private lateinit var rd1: Directory
    private lateinit var rd2: Directory

    @BeforeTest
    fun setUp() {
        rd1 = newDirectory()
        val iw1 = IndexWriter(rd1, newIndexWriterConfig(MockAnalyzer(random())))

        val doc = Document()
        doc.add(newTextField("field1", "the quick brown fox jumps", Field.Store.YES))
        doc.add(newTextField("field2", "the quick brown fox jumps", Field.Store.YES))
        iw1.addDocument(doc)

        iw1.close()
        rd2 = newDirectory()
        val iw2 = IndexWriter(rd2, newIndexWriterConfig(MockAnalyzer(random())))

        val doc2 = Document()
        doc2.add(newTextField("field1", "the fox jumps over the lazy dog", Field.Store.YES))
        doc2.add(newTextField("field3", "the fox jumps over the lazy dog", Field.Store.YES))
        iw2.addDocument(doc2)

        iw2.close()

        this.ir1 = getOnlyLeafReader(DirectoryReader.open(rd1))
        this.ir2 = getOnlyLeafReader(DirectoryReader.open(rd2))
    }

    @AfterTest
    fun tearDown() {
        ir1.close()
        ir2.close()
        rd1.close()
        rd2.close()
    }

    private fun checkTerms(terms: Terms?, vararg termsList: String) {
        assertNotNull(terms)
        val te = terms.iterator()

        for (t in termsList) {
            val b = te.next()
            assertNotNull(b)
            assertEquals(t, b.utf8ToString())
            val td = TestUtil.docs(random(), te, null, PostingsEnum.NONE.toInt())
            assertTrue(td.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
            assertEquals(0, td.docID())
            assertEquals(td.nextDoc(), DocIdSetIterator.NO_MORE_DOCS)
        }
        assertNull(te.next())
    }

    @Test
    fun test1() {
        val pr = ParallelLeafReader(ir1, ir2)

        assertEquals(3, pr.fieldInfos.size())

        checkTerms(pr.terms("field1"), "brown", "fox", "jumps", "quick", "the")
        checkTerms(pr.terms("field2"), "brown", "fox", "jumps", "quick", "the")
        checkTerms(pr.terms("field3"), "dog", "fox", "jumps", "lazy", "over", "the")
    }
}
