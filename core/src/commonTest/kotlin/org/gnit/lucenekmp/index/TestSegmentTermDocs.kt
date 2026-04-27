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
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.DocHelper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.Version
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestSegmentTermDocs : LuceneTestCase() {
    private val testDoc = Document()
    private lateinit var dir: Directory
    private lateinit var info: SegmentCommitInfo

    @BeforeTest
    fun setUp() {
        dir = newDirectory()
        DocHelper.setupDoc(testDoc)
        info = DocHelper.writeDoc(random(), dir, testDoc)
    }

    @AfterTest
    fun tearDown() {
        dir.close()
    }

    @Test
    fun test() {
        assertTrue(this::dir.isInitialized)
    }

    @Test
    @Throws(IOException::class)
    fun testTermDocs() {
        // After adding the document, we should be able to read it back in
        val reader = SegmentReader(info, Version.LATEST.major, newIOContext(random()))

        val terms = reader.terms(DocHelper.TEXT_FIELD_2_KEY)!!.iterator()
        terms.seekCeil(BytesRef("field"))
        val termDocs = TestUtil.docs(random(), terms, null, PostingsEnum.FREQS.toInt())
        if (termDocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            val docId = termDocs.docID()
            assertEquals(0, docId)
            val freq = termDocs.freq()
            assertEquals(3, freq)
        }
        reader.close()
    }

    @Test
    @Throws(IOException::class)
    fun testBadSeek() {
        run {
            // After adding the document, we should be able to read it back in
            val reader = SegmentReader(info, Version.LATEST.major, newIOContext(random()))
            val termDocs = TestUtil.docs(random(), reader, "textField2", BytesRef("bad"), null, 0)

            assertNull(termDocs)
            reader.close()
        }
        run {
            // After adding the document, we should be able to read it back in
            val reader = SegmentReader(info, Version.LATEST.major, newIOContext(random()))
            val termDocs = TestUtil.docs(random(), reader, "junk", BytesRef("bad"), null, 0)
            assertNull(termDocs)
            reader.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSkipTo() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy())
            )

        val ta = Term("content", "aaa")
        repeat(10) { addDoc(writer, "aaa aaa aaa aaa") }

        val tb = Term("content", "bbb")
        repeat(16) { addDoc(writer, "bbb bbb bbb bbb") }

        val tc = Term("content", "ccc")
        repeat(50) { addDoc(writer, "ccc ccc ccc ccc") }

        // assure that we deal with a single segment
        writer.forceMerge(1)
        writer.close()

        val reader: IndexReader = DirectoryReader.open(dir)

        var tdocs =
            TestUtil.docs(
                random(),
                reader,
                ta.field(),
                BytesRef(ta.text()),
                null,
                PostingsEnum.FREQS.toInt(),
            )!!

        // without optimization (assumption skipInterval == 16)

        // with next
        assertTrue(tdocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(0, tdocs.docID())
        assertEquals(4, tdocs.freq())
        assertTrue(tdocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(1, tdocs.docID())
        assertEquals(4, tdocs.freq())
        assertTrue(tdocs.advance(2) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(2, tdocs.docID())
        assertTrue(tdocs.advance(4) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(4, tdocs.docID())
        assertTrue(tdocs.advance(9) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(9, tdocs.docID())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, tdocs.advance(10))

        // without next
        tdocs = TestUtil.docs(random(), reader, ta.field(), BytesRef(ta.text()), null, 0)!!

        assertTrue(tdocs.advance(0) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(0, tdocs.docID())
        assertTrue(tdocs.advance(4) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(4, tdocs.docID())
        assertTrue(tdocs.advance(9) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(9, tdocs.docID())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, tdocs.advance(10))

        // exactly skipInterval documents and therefore with optimization

        // with next
        tdocs =
            TestUtil.docs(
                random(),
                reader,
                tb.field(),
                BytesRef(tb.text()),
                null,
                PostingsEnum.FREQS.toInt(),
            )!!

        assertTrue(tdocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(10, tdocs.docID())
        assertEquals(4, tdocs.freq())
        assertTrue(tdocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(11, tdocs.docID())
        assertEquals(4, tdocs.freq())
        assertTrue(tdocs.advance(12) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(12, tdocs.docID())
        assertTrue(tdocs.advance(15) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(15, tdocs.docID())
        assertTrue(tdocs.advance(24) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(24, tdocs.docID())
        assertTrue(tdocs.advance(25) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(25, tdocs.docID())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, tdocs.advance(26))

        // without next
        tdocs =
            TestUtil.docs(
                random(),
                reader,
                tb.field(),
                BytesRef(tb.text()),
                null,
                PostingsEnum.FREQS.toInt(),
            )!!

        assertTrue(tdocs.advance(5) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(10, tdocs.docID())
        assertTrue(tdocs.advance(15) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(15, tdocs.docID())
        assertTrue(tdocs.advance(24) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(24, tdocs.docID())
        assertTrue(tdocs.advance(25) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(25, tdocs.docID())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, tdocs.advance(26))

        // much more than skipInterval documents and therefore with optimization

        // with next
        tdocs =
            TestUtil.docs(
                random(),
                reader,
                tc.field(),
                BytesRef(tc.text()),
                null,
                PostingsEnum.FREQS.toInt(),
            )!!

        assertTrue(tdocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(26, tdocs.docID())
        assertEquals(4, tdocs.freq())
        assertTrue(tdocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(27, tdocs.docID())
        assertEquals(4, tdocs.freq())
        assertTrue(tdocs.advance(28) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(28, tdocs.docID())
        assertTrue(tdocs.advance(40) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(40, tdocs.docID())
        assertTrue(tdocs.advance(57) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(57, tdocs.docID())
        assertTrue(tdocs.advance(74) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(74, tdocs.docID())
        assertTrue(tdocs.advance(75) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(75, tdocs.docID())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, tdocs.advance(76))

        // without next
        tdocs = TestUtil.docs(random(), reader, tc.field(), BytesRef(tc.text()), null, 0)!!
        assertTrue(tdocs.advance(5) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(26, tdocs.docID())
        assertTrue(tdocs.advance(40) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(40, tdocs.docID())
        assertTrue(tdocs.advance(57) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(57, tdocs.docID())
        assertTrue(tdocs.advance(74) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(74, tdocs.docID())
        assertTrue(tdocs.advance(75) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(75, tdocs.docID())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, tdocs.advance(76))

        reader.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun addDoc(writer: IndexWriter, value: String) {
        val doc = Document()
        doc.add(newTextField("content", value, Field.Store.NO))
        writer.addDocument(doc)
    }
}
