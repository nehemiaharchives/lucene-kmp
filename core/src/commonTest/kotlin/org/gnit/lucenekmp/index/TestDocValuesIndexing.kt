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

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.InvertableType
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StoredValue
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NamedThreadFactory
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Tests DocValues integration into IndexWriter */
@OptIn(ExperimentalAtomicApi::class)
class TestDocValuesIndexing : LuceneTestCase() {
    /*
     * - add test for multi segment case with deletes
     * - add multithreaded tests / integrate into stress indexing?
     */

    @Test
    fun testAddIndexes() {
        val d1 = newDirectory()
        var w = RandomIndexWriter(random(), d1)
        var doc = Document()
        doc.add(newStringField("id", "1", Field.Store.YES))
        doc.add(NumericDocValuesField("dv", 1))
        w.addDocument(doc)
        val r1 = w.getReader(true, false)
        w.close()

        val d2 = newDirectory()
        w = RandomIndexWriter(random(), d2)
        doc = Document()
        doc.add(newStringField("id", "2", Field.Store.YES))
        doc.add(NumericDocValuesField("dv", 2))
        w.addDocument(doc)
        val r2 = w.getReader(true, false)
        w.close()

        val d3 = newDirectory()
        w = RandomIndexWriter(random(), d3)
        w.addIndexes(
            SlowCodecReaderWrapper.wrap(getOnlyLeafReader(r1)),
            SlowCodecReaderWrapper.wrap(getOnlyLeafReader(r2))
        )
        r1.close()
        d1.close()
        r2.close()
        d2.close()

        w.forceMerge(1)
        val r3 = w.getReader(true, false)
        w.close()
        val sr = getOnlyLeafReader(r3)
        assertEquals(2, sr.numDocs())
        val docValues = sr.getNumericDocValues("dv")
        assertNotNull(docValues)
        r3.close()
        d3.close()
    }

    @Test
    fun testMultiValuedDocValuesField() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        val doc = Document()
        val f = NumericDocValuesField("field", 17)
        doc.add(f)

        w.addDocument(doc)

        doc.add(f)
        expectThrows(IllegalArgumentException::class) {
            w.addDocument(doc)
            fail("didn't hit expected exception")
        }

        val r = w.getReader(true, false)
        w.close()
        val values = DocValues.getNumeric(getOnlyLeafReader(r), "field")
        assertEquals(0, values.nextDoc())
        assertEquals(17L, values.longValue())
        r.close()
        d.close()
    }

    @Test
    fun testDifferentTypedDocValuesField() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        val doc = Document()
        doc.add(NumericDocValuesField("field", 17))
        w.addDocument(doc)

        doc.add(BinaryDocValuesField("field", newBytesRef("blah")))
        expectThrows(IllegalArgumentException::class) {
            w.addDocument(doc)
        }

        val r = w.getReader(true, false)
        w.close()
        val values = DocValues.getNumeric(getOnlyLeafReader(r), "field")
        assertEquals(0, values.nextDoc())
        assertEquals(17L, values.longValue())
        r.close()
        d.close()
    }

    @Test
    fun testDifferentTypedDocValuesField2() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        val doc = Document()
        doc.add(NumericDocValuesField("field", 17))
        w.addDocument(doc)

        doc.add(SortedDocValuesField("field", newBytesRef("hello")))
        expectThrows(IllegalArgumentException::class) {
            w.addDocument(doc)
        }

        val r = w.getReader(true, false)
        val values = DocValues.getNumeric(getOnlyLeafReader(r), "field")
        assertEquals(0, values.nextDoc())
        assertEquals(17L, values.longValue())
        r.close()
        w.close()
        d.close()
    }

    @Test
    fun testLengthPrefixAcrossTwoPages() {
        val d = newDirectory()
        val w = IndexWriter(d, IndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val bytes = ByteArray(32764)
        val b = BytesRef()
        b.bytes = bytes
        b.length = bytes.size
        doc.add(SortedDocValuesField("field", b))
        w.addDocument(doc)
        bytes[0] = 1
        w.addDocument(doc)
        w.forceMerge(1)
        val r = DirectoryReader.open(w)
        val s = DocValues.getSorted(getOnlyLeafReader(r), "field")
        assertEquals(0, s.nextDoc())
        var bytes1 = s.lookupOrd(s.ordValue())
        assertEquals(bytes.size, bytes1!!.length)
        bytes[0] = 0
        assertEquals(b, bytes1)

        assertEquals(1, s.nextDoc())
        bytes1 = s.lookupOrd(s.ordValue())
        assertEquals(bytes.size, bytes1!!.length)
        bytes[0] = 1
        assertEquals(b, bytes1)
        r.close()
        w.close()
        d.close()
    }

    @Test
    fun testDocValuesUnstored() {
        val dir = newDirectory()
        val iwconfig = newIndexWriterConfig(MockAnalyzer(random()))
        iwconfig.setMergePolicy(newLogMergePolicy())
        val writer = IndexWriter(dir, iwconfig)
        for (i in 0..<50) {
            val doc = Document()
            doc.add(NumericDocValuesField("dv", i.toLong()))
            doc.add(TextField("docId", "$i", Field.Store.YES))
            writer.addDocument(doc)
        }
        val r = DirectoryReader.open(writer)
        val fi = FieldInfos.getMergedFieldInfos(r)
        val dvInfo = fi.fieldInfo("dv")
        assertTrue(dvInfo!!.docValuesType != DocValuesType.NONE)
        val dv = MultiDocValues.getNumericValues(r, "dv")!!
        val storedFields = r.storedFields()
        for (i in 0..<50) {
            assertEquals(i, dv.nextDoc())
            assertEquals(i.toLong(), dv.longValue())
            val d = storedFields.document(i)
            assertNull(d.getField("dv"))
            assertEquals(i.toString(), d.get("docId"))
        }
        r.close()
        writer.close()
        dir.close()
    }

    @Test
    fun testMixedTypesSameDocument() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        w.addDocument(Document())

        val doc = Document()
        doc.add(NumericDocValuesField("foo", 0))
        doc.add(SortedDocValuesField("foo", newBytesRef("hello")))
        expectThrows(IllegalArgumentException::class) {
            w.addDocument(doc)
        }

        val ir = DirectoryReader.open(w)
        assertEquals(1, ir.numDocs())
        ir.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMixedTypesDifferentDocuments() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(NumericDocValuesField("foo", 0))
        w.addDocument(doc)

        val doc2 = Document()
        doc2.add(SortedDocValuesField("foo", newBytesRef("hello")))
        expectThrows(IllegalArgumentException::class) {
            w.addDocument(doc2)
        }

        val ir = DirectoryReader.open(w)
        assertEquals(1, ir.numDocs())
        ir.close()
        w.close()
        dir.close()
    }

    @Test
    fun testAddSortedTwice() {
        val analyzer = MockAnalyzer(random())

        val directory = newDirectory()
        val iwc = newIndexWriterConfig(analyzer)
        iwc.setMergePolicy(newLogMergePolicy())
        val iwriter = IndexWriter(directory, iwc)
        val doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("foo!")))
        iwriter.addDocument(doc)

        doc.add(SortedDocValuesField("dv", newBytesRef("bar!")))
        expectThrows(IllegalArgumentException::class) {
            iwriter.addDocument(doc)
        }

        val ir = DirectoryReader.open(iwriter)
        assertEquals(1, ir.numDocs())
        ir.close()
        iwriter.close()
        directory.close()
    }

    @Test
    fun testAddBinaryTwice() {
        val analyzer = MockAnalyzer(random())

        val directory = newDirectory()
        val iwc = newIndexWriterConfig(analyzer)
        iwc.setMergePolicy(newLogMergePolicy())
        val iwriter = IndexWriter(directory, iwc)
        val doc = Document()
        doc.add(BinaryDocValuesField("dv", newBytesRef("foo!")))
        iwriter.addDocument(doc)

        doc.add(BinaryDocValuesField("dv", newBytesRef("bar!")))
        expectThrows(IllegalArgumentException::class) {
            iwriter.addDocument(doc)
        }

        val ir = DirectoryReader.open(iwriter)
        assertEquals(1, ir.numDocs())
        ir.close()

        iwriter.close()
        directory.close()
    }

    @Test
    fun testAddNumericTwice() {
        val analyzer = MockAnalyzer(random())

        val directory = newDirectory()
        val iwc = newIndexWriterConfig(analyzer)
        iwc.setMergePolicy(newLogMergePolicy())
        val iwriter = IndexWriter(directory, iwc)
        val doc = Document()
        doc.add(NumericDocValuesField("dv", 1))
        iwriter.addDocument(doc)

        doc.add(NumericDocValuesField("dv", 2))
        expectThrows(IllegalArgumentException::class) {
            iwriter.addDocument(doc)
        }

        val ir = DirectoryReader.open(iwriter)
        assertEquals(1, ir.numDocs())
        ir.close()
        iwriter.close()
        directory.close()
    }

    @Test
    fun testTooLargeSortedBytes() {
        val analyzer = MockAnalyzer(random())

        val directory = newDirectory()
        val iwc = newIndexWriterConfig(analyzer)
        iwc.setMergePolicy(newLogMergePolicy())
        val iwriter = IndexWriter(directory, iwc)
        val doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("just fine")))
        iwriter.addDocument(doc)

        val hugeDoc = Document()
        val bytes = ByteArray(100000)
        val b = newBytesRef(bytes)
        random().nextBytes(bytes)
        hugeDoc.add(SortedDocValuesField("dv", b))
        expectThrows(IllegalArgumentException::class) {
            iwriter.addDocument(hugeDoc)
        }

        val ir = DirectoryReader.open(iwriter)
        assertEquals(1, ir.numDocs())
        ir.close()
        iwriter.close()
        directory.close()
    }

    @Test
    fun testTooLargeTermSortedSetBytes() {
        val analyzer = MockAnalyzer(random())

        val directory = newDirectory()
        val iwc = newIndexWriterConfig(analyzer)
        iwc.setMergePolicy(newLogMergePolicy())
        val iwriter = IndexWriter(directory, iwc)
        val doc = Document()
        doc.add(SortedSetDocValuesField("dv", newBytesRef("just fine")))
        iwriter.addDocument(doc)

        val hugeDoc = Document()
        val bytes = ByteArray(100000)
        val b = newBytesRef(bytes)
        random().nextBytes(bytes)
        hugeDoc.add(SortedSetDocValuesField("dv", b))
        expectThrows(IllegalArgumentException::class) {
            iwriter.addDocument(hugeDoc)
        }

        val ir = DirectoryReader.open(iwriter)
        assertEquals(1, ir.numDocs())
        ir.close()
        iwriter.close()
        directory.close()
    }

    @Test
    fun testMixedTypesDifferentSegments() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(NumericDocValuesField("foo", 0))
        w.addDocument(doc)
        w.commit()

        val doc2 = Document()
        doc2.add(SortedDocValuesField("foo", newBytesRef("hello")))
        expectThrows(IllegalArgumentException::class) {
            w.addDocument(doc2)
        }

        w.close()
        dir.close()
    }

    @Test
    fun testMixedTypesAfterDeleteAll() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        var doc = Document()
        doc.add(NumericDocValuesField("foo", 0))
        w.addDocument(doc)
        w.deleteAll()

        doc = Document()
        doc.add(SortedDocValuesField("foo", newBytesRef("hello")))
        w.addDocument(doc)
        w.close()
        dir.close()
    }

    @Test
    fun testMixedTypesAfterReopenCreate() {
        val dir = newDirectory()
        var w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        var doc = Document()
        doc.add(NumericDocValuesField("foo", 0))
        w.addDocument(doc)
        w.close()

        val iwc = newIndexWriterConfig(MockAnalyzer(random()))
        iwc.setOpenMode(OpenMode.CREATE)
        w = IndexWriter(dir, iwc)
        doc = Document()
        w.addDocument(doc)
        w.close()
        dir.close()
    }

    @Test
    fun testMixedTypesAfterReopenAppend1() {
        val dir = newDirectory()
        var w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(NumericDocValuesField("foo", 0))
        w.addDocument(doc)
        w.close()

        val w2 = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc2 = Document()
        doc2.add(SortedDocValuesField("foo", newBytesRef("hello")))
        expectThrows(IllegalArgumentException::class) {
            w2.addDocument(doc2)
        }

        w2.close()
        dir.close()
    }

    @Test
    fun testMixedTypesAfterReopenAppend2() {
        val dir = newDirectory()
        var w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(SortedSetDocValuesField("foo", newBytesRef("foo")))
        w.addDocument(doc)
        w.close()

        val doc2 = Document()
        val w2 = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        doc2.add(StringField("foo", "bar", Field.Store.NO))
        doc2.add(BinaryDocValuesField("foo", newBytesRef("foo")))
        expectThrows(IllegalArgumentException::class) {
            w2.addDocument(doc2)
        }

        w2.forceMerge(1)
        w2.close()
        dir.close()
    }

    @Test
    fun testMixedTypesAfterReopenAppend3() {
        val dir = newDirectory()
        var w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(SortedSetDocValuesField("foo", newBytesRef("foo")))
        w.addDocument(doc)
        w.close()

        val doc2 = Document()
        val w2 = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        doc2.add(StringField("foo", "bar", Field.Store.NO))
        doc2.add(BinaryDocValuesField("foo", newBytesRef("foo")))
        expectThrows(IllegalArgumentException::class) {
            w2.addDocument(doc2)
        }

        w2.addDocument(Document())
        w2.forceMerge(1)
        w2.close()
        dir.close()
    }

    @Test
    fun testMixedTypesDifferentThreads() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))

        val startingGun = CountDownLatch(1)
        val hitExc = AtomicBoolean(false)
        val threads = arrayOfNulls<kotlinx.coroutines.Job>(3)
        val threadFactory = NamedThreadFactory("TestDocValuesIndexing")
        for (i in 0..<3) {
            val field: Field =
                if (i == 0) {
                    SortedDocValuesField("foo", newBytesRef("hello"))
                } else if (i == 1) {
                    NumericDocValuesField("foo", 0)
                } else {
                    BinaryDocValuesField("foo", newBytesRef("bazz"))
                }
            val doc = Document()
            doc.add(field)

            threads[i] = threadFactory.newThread {
                try {
                    startingGun.await()
                    w.addDocument(doc)
                } catch (_: IllegalArgumentException) {
                    hitExc.store(true)
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }
        }

        startingGun.countDown()

        for (t in threads) {
            runBlocking {
                t!!.join()
            }
        }
        assertTrue(hitExc.load())
        w.close()
        dir.close()
    }

    @Test
    fun testMixedTypesViaAddIndexes() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        var doc = Document()
        doc.add(NumericDocValuesField("foo", 0))
        w.addDocument(doc)

        val dir2 = newDirectory()
        val w2 = IndexWriter(dir2, newIndexWriterConfig(MockAnalyzer(random())))
        doc = Document()
        doc.add(SortedDocValuesField("foo", newBytesRef("hello")))
        w2.addDocument(doc)
        w2.close()

        expectThrows(IllegalArgumentException::class) {
            w.addIndexes(dir2)
        }

        val r = DirectoryReader.open(dir2)
        expectThrows(IllegalArgumentException::class) {
            TestUtil.addIndexesSlowly(w, r)
        }

        r.close()
        dir2.close()
        w.close()
        dir.close()
    }

    @Test
    fun testIllegalTypeChange() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)
        val doc = Document()
        doc.add(NumericDocValuesField("dv", 0L))
        writer.addDocument(doc)
        val doc2 = Document()
        doc2.add(SortedDocValuesField("dv", newBytesRef("foo")))
        expectThrows(IllegalArgumentException::class) {
            writer.addDocument(doc2)
        }

        val ir = DirectoryReader.open(writer)
        assertEquals(1, ir.numDocs())
        ir.close()
        writer.close()
        dir.close()
    }

    @Test
    fun testIllegalTypeChangeAcrossSegments() {
        val dir = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random()))
        var writer = IndexWriter(dir, conf)
        val doc = Document()
        doc.add(NumericDocValuesField("dv", 0L))
        writer.addDocument(doc)
        writer.close()

        conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer2 = IndexWriter(dir, conf)
        val doc2 = Document()
        doc2.add(SortedDocValuesField("dv", newBytesRef("foo")))
        expectThrows(IllegalArgumentException::class) {
            writer2.addDocument(doc2)
        }

        writer2.close()
        dir.close()
    }

    @Test
    fun testTypeChangeAfterCloseAndDeleteAll() {
        val dir = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random()))
        var writer = IndexWriter(dir, conf)
        var doc = Document()
        doc.add(NumericDocValuesField("dv", 0L))
        writer.addDocument(doc)
        writer.close()

        conf = newIndexWriterConfig(MockAnalyzer(random()))
        writer = IndexWriter(dir, conf)
        writer.deleteAll()
        doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("foo")))
        writer.addDocument(doc)
        writer.close()
        dir.close()
    }

    @Test
    fun testTypeChangeAfterDeleteAll() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)
        var doc = Document()
        doc.add(NumericDocValuesField("dv", 0L))
        writer.addDocument(doc)
        writer.deleteAll()
        doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("foo")))
        writer.addDocument(doc)
        writer.close()
        dir.close()
    }

    @Test
    fun testTypeChangeAfterCommitAndDeleteAll() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)
        var doc = Document()
        doc.add(NumericDocValuesField("dv", 0L))
        writer.addDocument(doc)
        writer.commit()
        writer.deleteAll()
        doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("foo")))
        writer.addDocument(doc)
        writer.close()
        dir.close()
    }

    @Test
    fun testTypeChangeAfterOpenCreate() {
        val dir = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random()))
        var writer = IndexWriter(dir, conf)
        var doc = Document()
        doc.add(NumericDocValuesField("dv", 0L))
        writer.addDocument(doc)
        writer.close()
        conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setOpenMode(OpenMode.CREATE)
        writer = IndexWriter(dir, conf)
        doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("foo")))
        writer.addDocument(doc)
        writer.close()
        dir.close()
    }

    @Test
    fun testTypeChangeViaAddIndexes() {
        val dir = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random()))
        var writer = IndexWriter(dir, conf)
        var doc = Document()
        doc.add(NumericDocValuesField("dv", 0L))
        writer.addDocument(doc)
        writer.close()

        val dir2 = newDirectory()
        conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer2 = IndexWriter(dir2, conf)
        doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("foo")))
        writer2.addDocument(doc)
        expectThrows(IllegalArgumentException::class) {
            writer2.addIndexes(dir)
        }
        writer2.close()

        dir.close()
        dir2.close()
    }

    @Test
    fun testTypeChangeViaAddIndexesIR() {
        val dir = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random()))
        var writer = IndexWriter(dir, conf)
        var doc = Document()
        doc.add(NumericDocValuesField("dv", 0L))
        writer.addDocument(doc)
        writer.close()

        val dir2 = newDirectory()
        conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer2 = IndexWriter(dir2, conf)
        doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("foo")))
        writer2.addDocument(doc)
        val reader = DirectoryReader.open(dir)
        expectThrows(IllegalArgumentException::class) {
            TestUtil.addIndexesSlowly(writer2, reader)
        }

        reader.close()
        writer2.close()

        dir.close()
        dir2.close()
    }

    @Test
    fun testTypeChangeViaAddIndexes2() {
        val dir = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random()))
        var writer = IndexWriter(dir, conf)
        var doc = Document()
        doc.add(NumericDocValuesField("dv", 0L))
        writer.addDocument(doc)
        writer.close()

        val dir2 = newDirectory()
        conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer2 = IndexWriter(dir2, conf)
        writer2.addIndexes(dir)
        val doc2 = Document()
        doc2.add(SortedDocValuesField("dv", newBytesRef("foo")))
        expectThrows(IllegalArgumentException::class) {
            writer2.addDocument(doc2)
        }

        writer2.close()
        dir2.close()
        dir.close()
    }

    @Test
    fun testTypeChangeViaAddIndexesIR2() {
        val dir = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random()))
        var writer = IndexWriter(dir, conf)
        var doc = Document()
        doc.add(NumericDocValuesField("dv", 0L))
        writer.addDocument(doc)
        writer.close()

        val dir2 = newDirectory()
        conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer2 = IndexWriter(dir2, conf)
        val reader = DirectoryReader.open(dir)
        TestUtil.addIndexesSlowly(writer2, reader)
        reader.close()
        val doc2 = Document()
        doc2.add(SortedDocValuesField("dv", newBytesRef("foo")))
        expectThrows(IllegalArgumentException::class) {
            writer2.addDocument(doc2)
        }

        writer2.close()
        dir2.close()
        dir.close()
    }

    @Test
    fun testSameFieldNameForPostingAndDocValue() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        val doc = Document()
        doc.add(StringField("f", "mock-value", Field.Store.NO))
        doc.add(NumericDocValuesField("f", 5))
        writer.addDocument(doc)
        writer.commit()

        val doc2 = Document()
        doc2.add(BinaryDocValuesField("f", newBytesRef("mock")))
        expectThrows(IllegalArgumentException::class) {
            writer.addDocument(doc2)
        }
        writer.rollback()

        dir.close()
    }

    @Test
    fun testExcIndexingDocBeforeDocValues() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        val ft = FieldType(StringField.TYPE_NOT_STORED)
        ft.setDocValuesType(DocValuesType.SORTED)
        ft.freeze()
        val field = object : IndexableField {
            override fun name(): String {
                return "test"
            }

            override fun fieldType(): IndexableFieldType {
                return ft
            }

            override fun tokenStream(analyzer: Analyzer, reuse: TokenStream?): TokenStream {
                return object : TokenStream() {
                    override fun incrementToken(): Boolean {
                        throw RuntimeException()
                    }
                }
            }

            override fun binaryValue(): BytesRef {
                return BytesRef("value")
            }

            override fun stringValue(): String? {
                return null
            }

            override fun readerValue() = null

            override fun numericValue(): Number? {
                return null
            }

            override fun storedValue(): StoredValue? {
                return null
            }

            override fun invertableType(): InvertableType {
                return InvertableType.TOKEN_STREAM
            }
        }
        doc.add(field)
        expectThrows(RuntimeException::class) {
            w.addDocument(doc)
        }

        w.addDocument(Document())
        w.close()
        dir.close()
    }
}
