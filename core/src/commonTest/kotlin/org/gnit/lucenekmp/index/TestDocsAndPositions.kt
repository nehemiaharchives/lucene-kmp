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
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestDocsAndPositions : LuceneTestCase() {
    private lateinit var fieldName: String

    @BeforeTest
    fun setUp() {
        fieldName = "field${random().nextInt()}"
    }

    /** Simple testcase for [PostingsEnum] */
    @Test
    @Throws(IOException::class)
    fun testPositionsSimple() {
        val directory: Directory = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                directory,
                newIndexWriterConfig(random(), MockAnalyzer(random())),
            )
        for (i in 0..<39) {
            val doc = Document()
            val customType = FieldType(TextField.TYPE_NOT_STORED)
            customType.setOmitNorms(true)
            doc.add(
                newField(
                    fieldName,
                    "1 2 3 4 5 6 7 8 9 10 " +
                        "1 2 3 4 5 6 7 8 9 10 " +
                        "1 2 3 4 5 6 7 8 9 10 " +
                        "1 2 3 4 5 6 7 8 9 10",
                    customType,
                )
            )
            writer.addDocument(doc)
        }
        val reader = writer.getReader(true, false)
        writer.close()

        val num = atLeast(13)
        for (i in 0..<num) {
            val bytes = newBytesRef("1")
            val topReaderContext = reader.context
            for (leafReaderContext in topReaderContext.leaves()) {
                val docsAndPosEnum = assertNotNull(getDocsAndPositions(leafReaderContext.reader(), bytes))
                if (leafReaderContext.reader().maxDoc() == 0) {
                    continue
                }
                val advance = docsAndPosEnum.advance(random().nextInt(leafReaderContext.reader().maxDoc()))
                do {
                    val msg =
                        "Advanced to: $advance current doc: ${docsAndPosEnum.docID()}" // TODO: + " usePayloads: " + usePayload;
                    assertEquals(4, docsAndPosEnum.freq(), msg)
                    assertEquals(0, docsAndPosEnum.nextPosition(), msg)
                    assertEquals(4, docsAndPosEnum.freq(), msg)
                    assertEquals(10, docsAndPosEnum.nextPosition(), msg)
                    assertEquals(4, docsAndPosEnum.freq(), msg)
                    assertEquals(20, docsAndPosEnum.nextPosition(), msg)
                    assertEquals(4, docsAndPosEnum.freq(), msg)
                    assertEquals(30, docsAndPosEnum.nextPosition(), msg)
                } while (docsAndPosEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
            }
        }
        reader.close()
        directory.close()
    }

    @Throws(IOException::class)
    fun getDocsAndPositions(reader: LeafReader, bytes: BytesRef): PostingsEnum? {
        val terms = reader.terms(fieldName)
        if (terms != null) {
            val te = terms.iterator()
            if (te.seekExact(bytes)) {
                return te.postings(null, PostingsEnum.ALL.toInt())
            }
        }
        return null
    }

    /**
     * this test indexes random numbers within a range into a field and checks their occurrences by
     * searching for a number from that range selected at random. All positions for that number are
     * saved up front and compared to the enums positions.
     */
    @Test
    @Throws(IOException::class)
    fun testRandomPositions() {
        val dir: Directory = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(random(), MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()),
            )
        val numDocs = atLeast(47)
        val max = 1051
        val term = random().nextInt(max)
        val positionsInDoc = arrayOfNulls<Array<Int>>(numDocs)
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setOmitNorms(true)
        for (i in 0..<numDocs) {
            val doc = Document()
            val positions = ArrayList<Int>()
            val builder = StringBuilder()
            val num = atLeast(131)
            for (j in 0..<num) {
                val nextInt = random().nextInt(max)
                builder.append(nextInt).append(" ")
                if (nextInt == term) {
                    positions.add(j)
                }
            }
            if (positions.size == 0) {
                builder.append(term)
                positions.add(num)
            }
            doc.add(newField(fieldName, builder.toString(), customType))
            positionsInDoc[i] = positions.toTypedArray()
            writer.addDocument(doc)
        }

        val reader = writer.getReader(true, false)
        writer.close()

        val num = atLeast(13)
        for (i in 0..<num) {
            val bytes = newBytesRef(term.toString())
            val topReaderContext = reader.context
            for (leafReaderContext in topReaderContext.leaves()) {
                val docsAndPosEnum = assertNotNull(getDocsAndPositions(leafReaderContext.reader(), bytes))
                var initDoc: Int
                val maxDoc = leafReaderContext.reader().maxDoc()
                if (random().nextBoolean()) {
                    initDoc = docsAndPosEnum.nextDoc()
                } else {
                    initDoc = docsAndPosEnum.advance(random().nextInt(maxDoc))
                }
                do {
                    val docID = docsAndPosEnum.docID()
                    if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                        break
                    }
                    val pos = positionsInDoc[leafReaderContext.docBase + docID]!!
                    assertEquals(pos.size, docsAndPosEnum.freq())
                    val howMany =
                        if (random().nextInt(20) == 0) {
                            pos.size - random().nextInt(pos.size)
                        } else {
                            pos.size
                        }
                    for (j in 0..<howMany) {
                        assertEquals(
                            pos[j],
                            docsAndPosEnum.nextPosition(),
                            "iteration: $i initDoc: $initDoc doc: $docID base: ${leafReaderContext.docBase} positions: ${pos.contentToString()}", // TODO: + " usePayloads: " + usePayload
                        )
                    }

                    if (random().nextInt(10) == 0) {
                        if (docsAndPosEnum.advance(docID + 1 + random().nextInt(maxDoc - docID)) ==
                            DocIdSetIterator.NO_MORE_DOCS
                        ) {
                            break
                        }
                    }
                } while (docsAndPosEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
            }
        }
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRandomDocs() {
        val dir: Directory = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(random(), MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()),
            )
        val numDocs = atLeast(49)
        val max = 15678
        val term = random().nextInt(max)
        val freqInDoc = IntArray(numDocs)
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setOmitNorms(true)
        for (i in 0..<numDocs) {
            val doc = Document()
            val builder = StringBuilder()
            for (j in 0..<199) {
                val nextInt = random().nextInt(max)
                builder.append(nextInt).append(' ')
                if (nextInt == term) {
                    freqInDoc[i]++
                }
            }
            doc.add(newField(fieldName, builder.toString(), customType))
            writer.addDocument(doc)
        }

        val reader = writer.getReader(true, false)
        writer.close()

        val num = atLeast(13)
        for (i in 0..<num) {
            val bytes = newBytesRef(term.toString())
            val topReaderContext = reader.context
            for (context in topReaderContext.leaves()) {
                val maxDoc = context.reader().maxDoc()
                val postingsEnum =
                    TestUtil.docs(random(), context.reader(), fieldName, bytes, null, PostingsEnum.FREQS.toInt())
                if (findNext(freqInDoc, context.docBase, context.docBase + maxDoc) == Int.MAX_VALUE) {
                    assertNull(postingsEnum)
                    continue
                }
                val postings = assertNotNull(postingsEnum)
                postings.nextDoc()
                for (j in 0..<maxDoc) {
                    if (freqInDoc[context.docBase + j] != 0) {
                        assertEquals(j, postings.docID())
                        assertEquals(postings.freq(), freqInDoc[context.docBase + j])
                        if (i % 2 == 0 && random().nextInt(10) == 0) {
                            val next =
                                findNext(freqInDoc, context.docBase + j + 1, context.docBase + maxDoc) - context.docBase
                            val advancedTo = postings.advance(next)
                            if (next >= maxDoc) {
                                assertEquals(DocIdSetIterator.NO_MORE_DOCS, advancedTo)
                            } else {
                                assertTrue(next >= advancedTo, "advanced to: $advancedTo but should be <= $next")
                            }
                        } else {
                            postings.nextDoc()
                        }
                    }
                }
                assertEquals(
                    DocIdSetIterator.NO_MORE_DOCS,
                    postings.docID(),
                    "docBase: ${context.docBase} maxDoc: $maxDoc ${postings::class}",
                )
            }
        }

        reader.close()
        dir.close()
    }

    private fun findNext(docs: IntArray, pos: Int, max: Int): Int {
        for (i in pos..<max) {
            if (docs[i] != 0) {
                return i
            }
        }
        return Int.MAX_VALUE
    }

    /**
     * tests retrieval of positions for terms that have a large number of occurrences to force test of
     * buffer refill during positions iteration.
     */
    @Test
    @Throws(IOException::class)
    fun testLargeNumberOfPositions() {
        val dir: Directory = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(random(), MockAnalyzer(random())),
            )
        val howMany = 1000
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setOmitNorms(true)
        for (i in 0..<39) {
            val doc = Document()
            val builder = StringBuilder()
            for (j in 0..<howMany) {
                if (j % 2 == 0) {
                    builder.append("even ")
                } else {
                    builder.append("odd ")
                }
            }
            doc.add(newField(fieldName, builder.toString(), customType))
            writer.addDocument(doc)
        }

        val reader = writer.getReader(true, false)
        writer.close()

        val num = atLeast(13)
        for (i in 0..<num) {
            val bytes = newBytesRef("even")
            val topReaderContext = reader.context
            for (leafReaderContext in topReaderContext.leaves()) {
                val docsAndPosEnum = assertNotNull(getDocsAndPositions(leafReaderContext.reader(), bytes))

                val initDoc: Int
                val maxDoc = leafReaderContext.reader().maxDoc()
                if (random().nextBoolean()) {
                    initDoc = docsAndPosEnum.nextDoc()
                } else {
                    initDoc = docsAndPosEnum.advance(random().nextInt(maxDoc))
                }
                val msg = "Iteration: $i initDoc: $initDoc" // TODO: + " payloads: " + usePayload;
                assertEquals(howMany / 2, docsAndPosEnum.freq())
                var j = 0
                while (j < howMany) {
                    assertEquals(
                        j,
                        docsAndPosEnum.nextPosition(),
                        "position missmatch index: $j with freq: ${docsAndPosEnum.freq()} -- $msg",
                    )
                    j += 2
                }
            }
        }
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDocsEnumStart() {
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(newStringField("foo", "bar", Field.Store.NO))
        writer.addDocument(doc)
        val reader = writer.getReader(true, false)
        val r = getOnlyLeafReader(reader)
        var disi = TestUtil.docs(random(), r, "foo", newBytesRef("bar"), null, PostingsEnum.NONE.toInt())
        var docid = assertNotNull(disi).docID()
        assertEquals(-1, docid)
        assertTrue(disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)

        val te = assertNotNull(r.terms("foo")).iterator()
        assertTrue(te.seekExact(newBytesRef("bar")))
        disi = TestUtil.docs(random(), te, disi, PostingsEnum.NONE.toInt())
        docid = disi.docID()
        assertEquals(-1, docid)
        assertTrue(disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        writer.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDocsAndPositionsEnumStart() {
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(newTextField("foo", "bar", Field.Store.NO))
        writer.addDocument(doc)
        val reader = writer.getReader(true, false)
        val r = getOnlyLeafReader(reader)
        var disi = assertNotNull(r.postings(Term("foo", "bar"), PostingsEnum.ALL.toInt()))
        var docid = disi.docID()
        assertEquals(-1, docid)
        assertTrue(disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)

        val te = assertNotNull(r.terms("foo")).iterator()
        assertTrue(te.seekExact(newBytesRef("bar")))
        disi = assertNotNull(te.postings(disi, PostingsEnum.ALL.toInt()))
        docid = disi.docID()
        assertEquals(-1, docid)
        assertTrue(disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        writer.close()
        r.close()
        dir.close()
    }
}
