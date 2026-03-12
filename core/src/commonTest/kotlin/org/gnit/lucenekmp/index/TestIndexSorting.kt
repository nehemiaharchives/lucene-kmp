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

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.PointsFormat
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.PointsWriter
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.BinaryPoint
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoubleDocValuesField
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.FloatDocValuesField
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.FieldDoc
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.SortedNumericSortField
import org.gnit.lucenekmp.search.SortedSetSortField
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.TopFieldCollectorManager
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalAtomicApi::class)
class TestIndexSorting : LuceneTestCase() {
    internal class AssertingNeedsIndexSortCodec : FilterCodec(
        TestUtil.getDefaultCodec().name,
        TestUtil.getDefaultCodec()
    ) {
        var needsIndexSort: Boolean = false
        var numCalls: Int = 0

        override fun pointsFormat(): PointsFormat {
            val pf = delegate.pointsFormat()
            return object : PointsFormat() {
                override fun fieldsWriter(state: SegmentWriteState): PointsWriter {
                    val writer = pf.fieldsWriter(state)
                    return object : PointsWriter() {
                        override fun merge(mergeState: MergeState) {
                            // For single segment merge we cannot infer if the segment is already sorted or not.
                            if (mergeState.docMaps!!.size > 1) {
                                assertEquals(needsIndexSort, mergeState.needsIndexSort)
                            }
                            ++numCalls
                            writer.merge(mergeState)
                        }

                        override fun writeField(fieldInfo: FieldInfo, values: PointsReader) {
                            writer.writeField(fieldInfo, values)
                        }

                        override fun finish() {
                            writer.finish()
                        }

                        override fun close() {
                            writer.close()
                        }
                    }
                }

                override fun fieldsReader(state: SegmentReadState): PointsReader {
                    return pf.fieldsReader(state)
                }
            }
        }
    }

    private fun assertNeedsIndexSortMerge(
        sortField: SortField,
        defaultValueConsumer: (Document) -> Unit,
        randomValueConsumer: (Document) -> Unit
    ) {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val codec = AssertingNeedsIndexSortCodec()
        iwc.setCodec(codec)
        val indexSort = Sort(sortField, SortField("id", SortField.Type.INT))
        iwc.setIndexSort(indexSort)
        val policy = newLogMergePolicy()
        // make sure that merge factor is always > 2 and target search concurrency is no more than 1 to
        // avoid creating merges that are accidentally sorted
        policy.targetSearchConcurrency = 1
        if (policy.mergeFactor <= 2) {
            policy.mergeFactor = 3
        }
        iwc.setMergePolicy(policy)

        // add already sorted documents
        codec.numCalls = 0
        codec.needsIndexSort = false
        val w = IndexWriter(dir, iwc)
        val withValues = random().nextBoolean()
        for (i in 100..<200) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.YES))
            doc.add(NumericDocValuesField("id", i.toLong()))
            doc.add(IntPoint("point", random().nextInt()))
            if (withValues) {
                defaultValueConsumer(doc)
            }
            w.addDocument(doc)
            if (i % 10 == 0) {
                w.commit()
            }
        }
        val deletedDocs = mutableSetOf<Int>()
        val num = random().nextInt(20)
        for (i in 0..<num) {
            val nextDoc = random().nextInt(100)
            w.deleteDocuments(Term("id", nextDoc.toString()))
            deletedDocs.add(nextDoc)
        }
        w.commit()
        w.waitForMerges()
        w.forceMerge(1)
        assertTrue(codec.numCalls > 0)

        // merge sort is needed
        w.deleteAll()
        codec.numCalls = 0
        codec.needsIndexSort = true
        for (i in 10 downTo 0) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.YES))
            doc.add(NumericDocValuesField("id", i.toLong()))
            doc.add(IntPoint("point", random().nextInt()))
            if (withValues) {
                defaultValueConsumer(doc)
            }
            w.addDocument(doc)
            w.commit()
        }
        w.commit()
        w.waitForMerges()
        w.forceMerge(1)
        assertTrue(codec.numCalls > 0)

        // segment sort is needed
        codec.needsIndexSort = true
        codec.numCalls = 0
        for (i in 201..<300) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.YES))
            doc.add(NumericDocValuesField("id", i.toLong()))
            doc.add(IntPoint("point", random().nextInt()))
            randomValueConsumer(doc)
            w.addDocument(doc)
            if (i % 10 == 0) {
                w.commit()
            }
        }
        w.commit()
        w.waitForMerges()
        w.forceMerge(1)
        assertTrue(codec.numCalls > 0)

        w.close()
        dir.close()
    }

    @Test
    fun testNumericAlreadySorted() {
        assertNeedsIndexSortMerge(
            SortField("foo", SortField.Type.INT),
            { doc -> doc.add(NumericDocValuesField("foo", 0)) },
            { doc -> doc.add(NumericDocValuesField("foo", random().nextInt().toLong())) }
        )
    }

    @Test
    fun testStringAlreadySorted() {
        assertNeedsIndexSortMerge(
            SortField("foo", SortField.Type.STRING),
            { doc -> doc.add(SortedDocValuesField("foo", newBytesRef("default"))) },
            { doc -> doc.add(SortedDocValuesField("foo", TestUtil.randomBinaryTerm(random()))) }
        )
    }

    @Test
    fun testMultiValuedNumericAlreadySorted() {
        assertNeedsIndexSortMerge(
            SortedNumericSortField("foo", SortField.Type.INT),
            { doc ->
                doc.add(SortedNumericDocValuesField("foo", Int.MIN_VALUE.toLong()))
                val num = random().nextInt(5)
                for (j in 0..<num) {
                    doc.add(SortedNumericDocValuesField("foo", random().nextInt().toLong()))
                }
            },
            { doc ->
                val num = random().nextInt(5)
                for (j in 0..<num) {
                    doc.add(SortedNumericDocValuesField("foo", random().nextInt().toLong()))
                }
            }
        )
    }

    @Test
    fun testMultiValuedStringAlreadySorted() {
        assertNeedsIndexSortMerge(
            SortedSetSortField("foo", false),
            { doc ->
                doc.add(SortedSetDocValuesField("foo", newBytesRef("")))
                val num = random().nextInt(5)
                for (j in 0..<num) {
                    doc.add(SortedSetDocValuesField("foo", TestUtil.randomBinaryTerm(random())))
                }
            },
            { doc ->
                val num = random().nextInt(5)
                for (j in 0..<num) {
                    doc.add(SortedSetDocValuesField("foo", TestUtil.randomBinaryTerm(random())))
                }
            }
        )
    }

    @Test
    fun testBasicString() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortField("foo", SortField.Type.STRING))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(SortedDocValuesField("foo", newBytesRef("zzz")))
        w.addDocument(doc)
        // so we get more than one segment, so that forceMerge actually does merge, since we only get a
        // sorted segment by merging:
        w.commit()

        doc = Document()
        doc.add(SortedDocValuesField("foo", newBytesRef("aaa")))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(SortedDocValuesField("foo", newBytesRef("mmm")))
        w.addDocument(doc)
        w.forceMerge(1)

        val r = DirectoryReader.open(w)
        val leaf = getOnlyLeafReader(r)
        assertEquals(3, leaf.maxDoc())
        val values = leaf.getSortedDocValues("foo")!!
        assertEquals(0, values.nextDoc())
        assertEquals("aaa", values.lookupOrd(values.ordValue())!!.utf8ToString())
        assertEquals(1, values.nextDoc())
        assertEquals("mmm", values.lookupOrd(values.ordValue())!!.utf8ToString())
        assertEquals(2, values.nextDoc())
        assertEquals("zzz", values.lookupOrd(values.ordValue())!!.utf8ToString())
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testBasicMultiValuedString() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedSetSortField("foo", false))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(NumericDocValuesField("id", 3))
        doc.add(SortedSetDocValuesField("foo", newBytesRef("zzz")))
        w.addDocument(doc)
        // so we get more than one segment, so that forceMerge actually does merge, since we only get a
        // sorted segment by merging:
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("id", 1))
        doc.add(SortedSetDocValuesField("foo", newBytesRef("aaa")))
        doc.add(SortedSetDocValuesField("foo", newBytesRef("zzz")))
        doc.add(SortedSetDocValuesField("foo", newBytesRef("bcg")))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("id", 2))
        doc.add(SortedSetDocValuesField("foo", newBytesRef("mmm")))
        doc.add(SortedSetDocValuesField("foo", newBytesRef("pppp")))
        w.addDocument(doc)
        w.forceMerge(1)

        val r = DirectoryReader.open(w)
        val leaf = getOnlyLeafReader(r)
        assertEquals(3, leaf.maxDoc())
        val values = leaf.getNumericDocValues("id")!!
        assertEquals(0, values.nextDoc())
        assertEquals(1L, values.longValue())
        assertEquals(1, values.nextDoc())
        assertEquals(2L, values.longValue())
        assertEquals(2, values.nextDoc())
        assertEquals(3L, values.longValue())
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMissingStringFirst() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField = SortField("foo", SortField.Type.STRING, reverse)
            sortField.missingValue = SortField.STRING_FIRST
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(SortedDocValuesField("foo", newBytesRef("zzz")))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            w.addDocument(Document())
            w.commit()

            doc = Document()
            doc.add(SortedDocValuesField("foo", newBytesRef("mmm")))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getSortedDocValues("foo")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals("zzz", values.lookupOrd(values.ordValue())!!.utf8ToString())
                assertEquals(1, values.nextDoc())
                assertEquals("mmm", values.lookupOrd(values.ordValue())!!.utf8ToString())
            } else {
                // docID 0 is missing:
                assertEquals(1, values.nextDoc())
                assertEquals("mmm", values.lookupOrd(values.ordValue())!!.utf8ToString())
                assertEquals(2, values.nextDoc())
                assertEquals("zzz", values.lookupOrd(values.ordValue())!!.utf8ToString())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingMultiValuedStringFirst() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField: SortField = SortedSetSortField("foo", reverse)
            sortField.missingValue = SortField.STRING_FIRST
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("id", 3))
            doc.add(SortedSetDocValuesField("foo", newBytesRef("zzz")))
            doc.add(SortedSetDocValuesField("foo", newBytesRef("zzza")))
            doc.add(SortedSetDocValuesField("foo", newBytesRef("zzzd")))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            doc = Document()
            doc.add(NumericDocValuesField("id", 1))
            w.addDocument(doc)
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("id", 2))
            doc.add(SortedSetDocValuesField("foo", newBytesRef("mmm")))
            doc.add(SortedSetDocValuesField("foo", newBytesRef("nnnn")))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("id")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(3L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(1L, values.longValue())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(1L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(3L, values.longValue())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingStringLast() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField = SortField("foo", SortField.Type.STRING, reverse)
            sortField.missingValue = SortField.STRING_LAST
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(SortedDocValuesField("foo", newBytesRef("zzz")))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            w.addDocument(Document())
            w.commit()

            doc = Document()
            doc.add(SortedDocValuesField("foo", newBytesRef("mmm")))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getSortedDocValues("foo")!!
            if (reverse) {
                assertEquals(1, values.nextDoc())
                assertEquals("zzz", values.lookupOrd(values.ordValue())!!.utf8ToString())
                assertEquals(2, values.nextDoc())
                assertEquals("mmm", values.lookupOrd(values.ordValue())!!.utf8ToString())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals("mmm", values.lookupOrd(values.ordValue())!!.utf8ToString())
                assertEquals(1, values.nextDoc())
                assertEquals("zzz", values.lookupOrd(values.ordValue())!!.utf8ToString())
            }
            assertEquals(NO_MORE_DOCS, values.nextDoc())
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingMultiValuedStringLast() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField: SortField = SortedSetSortField("foo", reverse)
            sortField.missingValue = SortField.STRING_LAST
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("id", 2))
            doc.add(SortedSetDocValuesField("foo", newBytesRef("zzz")))
            doc.add(SortedSetDocValuesField("foo", newBytesRef("zzzd")))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            doc = Document()
            doc.add(NumericDocValuesField("id", 3))
            w.addDocument(doc)
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("id", 1))
            doc.add(SortedSetDocValuesField("foo", newBytesRef("mmm")))
            doc.add(SortedSetDocValuesField("foo", newBytesRef("ppp")))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("id")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(3L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(1L, values.longValue())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(1L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(3L, values.longValue())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testBasicLong() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortField("foo", SortField.Type.LONG))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(NumericDocValuesField("foo", 18))
        w.addDocument(doc)
        // so we get more than one segment, so that forceMerge actually does merge, since we only get a
        // sorted segment by merging:
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("foo", -1))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("foo", 7))
        w.addDocument(doc)
        w.forceMerge(1)

        val r = DirectoryReader.open(w)
        val leaf = getOnlyLeafReader(r)
        assertEquals(3, leaf.maxDoc())
        val values = leaf.getNumericDocValues("foo")!!
        assertEquals(0, values.nextDoc())
        assertEquals(-1L, values.longValue())
        assertEquals(1, values.nextDoc())
        assertEquals(7L, values.longValue())
        assertEquals(2, values.nextDoc())
        assertEquals(18L, values.longValue())
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testBasicMultiValuedLong() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField("foo", SortField.Type.LONG))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(NumericDocValuesField("id", 3))
        doc.add(SortedNumericDocValuesField("foo", 18))
        doc.add(SortedNumericDocValuesField("foo", 35))
        w.addDocument(doc)
        // so we get more than one segment, so that forceMerge actually does merge, since we only get a
        // sorted segment by merging:
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("id", 1))
        doc.add(SortedNumericDocValuesField("foo", -1))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("id", 2))
        doc.add(SortedNumericDocValuesField("foo", 7))
        doc.add(SortedNumericDocValuesField("foo", 22))
        w.addDocument(doc)
        w.forceMerge(1)

        val r = DirectoryReader.open(w)
        val leaf = getOnlyLeafReader(r)
        assertEquals(3, leaf.maxDoc())
        val values = leaf.getNumericDocValues("id")!!
        assertEquals(0, values.nextDoc())
        assertEquals(1L, values.longValue())
        assertEquals(1, values.nextDoc())
        assertEquals(2L, values.longValue())
        assertEquals(2, values.nextDoc())
        assertEquals(3L, values.longValue())
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMissingLongFirst() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField = SortField("foo", SortField.Type.LONG, reverse)
            sortField.missingValue = Long.MIN_VALUE
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("foo", 18))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            w.addDocument(Document())
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("foo", 7))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("foo")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(18L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(7L, values.longValue())
            } else {
                // docID 0 has no value
                assertEquals(1, values.nextDoc())
                assertEquals(7L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(18L, values.longValue())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingMultiValuedLongFirst() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField: SortField = SortedNumericSortField("foo", SortField.Type.LONG, reverse)
            sortField.missingValue = Long.MIN_VALUE
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("id", 3))
            doc.add(SortedNumericDocValuesField("foo", 18))
            doc.add(SortedNumericDocValuesField("foo", 27))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            doc = Document()
            doc.add(NumericDocValuesField("id", 1))
            w.addDocument(doc)
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("id", 2))
            doc.add(SortedNumericDocValuesField("foo", 7))
            doc.add(SortedNumericDocValuesField("foo", 24))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("id")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(3L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(1L, values.longValue())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(1L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(3L, values.longValue())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingLongLast() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField = SortField("foo", SortField.Type.LONG, reverse)
            sortField.missingValue = Long.MAX_VALUE
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("foo", 18))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            w.addDocument(Document())
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("foo", 7))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("foo")!!
            if (reverse) {
                // docID 0 is missing
                assertEquals(1, values.nextDoc())
                assertEquals(18L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(7L, values.longValue())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(7L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(18L, values.longValue())
            }
            assertEquals(NO_MORE_DOCS, values.nextDoc())
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingMultiValuedLongLast() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField: SortField = SortedNumericSortField("foo", SortField.Type.LONG, reverse)
            sortField.missingValue = Long.MAX_VALUE
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("id", 2))
            doc.add(SortedNumericDocValuesField("foo", 18))
            doc.add(SortedNumericDocValuesField("foo", 65))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            doc = Document()
            doc.add(NumericDocValuesField("id", 3))
            w.addDocument(doc)
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("id", 1))
            doc.add(SortedNumericDocValuesField("foo", 7))
            doc.add(SortedNumericDocValuesField("foo", 34))
            doc.add(SortedNumericDocValuesField("foo", 74))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("id")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(3L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(1L, values.longValue())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(1L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(3L, values.longValue())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testBasicInt() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortField("foo", SortField.Type.INT))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(NumericDocValuesField("foo", 18))
        w.addDocument(doc)
        // so we get more than one segment, so that forceMerge actually does merge, since we only get a
        // sorted segment by merging:
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("foo", -1))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("foo", 7))
        w.addDocument(doc)
        w.forceMerge(1)

        val r = DirectoryReader.open(w)
        val leaf = getOnlyLeafReader(r)
        assertEquals(3, leaf.maxDoc())
        val values = leaf.getNumericDocValues("foo")!!
        assertEquals(0, values.nextDoc())
        assertEquals(-1L, values.longValue())
        assertEquals(1, values.nextDoc())
        assertEquals(7L, values.longValue())
        assertEquals(2, values.nextDoc())
        assertEquals(18L, values.longValue())
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testBasicMultiValuedInt() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField("foo", SortField.Type.INT))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(NumericDocValuesField("id", 3))
        doc.add(SortedNumericDocValuesField("foo", 18))
        doc.add(SortedNumericDocValuesField("foo", 34))
        w.addDocument(doc)
        // so we get more than one segment, so that forceMerge actually does merge, since we only get a
        // sorted segment by merging:
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("id", 1))
        doc.add(SortedNumericDocValuesField("foo", -1))
        doc.add(SortedNumericDocValuesField("foo", 34))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("id", 2))
        doc.add(SortedNumericDocValuesField("foo", 7))
        doc.add(SortedNumericDocValuesField("foo", 22))
        doc.add(SortedNumericDocValuesField("foo", 27))
        w.addDocument(doc)
        w.forceMerge(1)

        val r = DirectoryReader.open(w)
        val leaf = getOnlyLeafReader(r)
        assertEquals(3, leaf.maxDoc())
        val values = leaf.getNumericDocValues("id")!!
        assertEquals(0, values.nextDoc())
        assertEquals(1L, values.longValue())
        assertEquals(1, values.nextDoc())
        assertEquals(2L, values.longValue())
        assertEquals(2, values.nextDoc())
        assertEquals(3L, values.longValue())
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMissingIntFirst() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField = SortField("foo", SortField.Type.INT, reverse)
            sortField.missingValue = Int.MIN_VALUE
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("foo", 18))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            w.addDocument(Document())
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("foo", 7))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("foo")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(18L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(7L, values.longValue())
            } else {
                assertEquals(1, values.nextDoc())
                assertEquals(7L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(18L, values.longValue())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingMultiValuedIntFirst() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField: SortField = SortedNumericSortField("foo", SortField.Type.INT, reverse)
            sortField.missingValue = Int.MIN_VALUE
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("id", 3))
            doc.add(SortedNumericDocValuesField("foo", 18))
            doc.add(SortedNumericDocValuesField("foo", 187667))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            doc = Document()
            doc.add(NumericDocValuesField("id", 1))
            w.addDocument(doc)
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("id", 2))
            doc.add(SortedNumericDocValuesField("foo", 7))
            doc.add(SortedNumericDocValuesField("foo", 34))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("id")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(3L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(1L, values.longValue())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(1L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(3L, values.longValue())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingIntLast() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField = SortField("foo", SortField.Type.INT, reverse)
            sortField.missingValue = Int.MAX_VALUE
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("foo", 18))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            w.addDocument(Document())
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("foo", 7))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("foo")!!
            if (reverse) {
                // docID 0 is missing
                assertEquals(1, values.nextDoc())
                assertEquals(18L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(7L, values.longValue())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(7L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(18L, values.longValue())
            }
            assertEquals(NO_MORE_DOCS, values.nextDoc())
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingMultiValuedIntLast() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField: SortField = SortedNumericSortField("foo", SortField.Type.INT, reverse)
            sortField.missingValue = Int.MAX_VALUE
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("id", 2))
            doc.add(SortedNumericDocValuesField("foo", 18))
            doc.add(SortedNumericDocValuesField("foo", 6372))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            doc = Document()
            doc.add(NumericDocValuesField("id", 3))
            w.addDocument(doc)
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("id", 1))
            doc.add(SortedNumericDocValuesField("foo", 7))
            doc.add(SortedNumericDocValuesField("foo", 8))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("id")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(3L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(1L, values.longValue())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(1L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(3L, values.longValue())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testBasicDouble() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortField("foo", SortField.Type.DOUBLE))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(DoubleDocValuesField("foo", 18.0))
        w.addDocument(doc)
        // so we get more than one segment, so that forceMerge actually does merge, since we only get a
        // sorted segment by merging:
        w.commit()

        doc = Document()
        doc.add(DoubleDocValuesField("foo", -1.0))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(DoubleDocValuesField("foo", 7.0))
        w.addDocument(doc)
        w.forceMerge(1)

        val r = DirectoryReader.open(w)
        val leaf = getOnlyLeafReader(r)
        assertEquals(3, leaf.maxDoc())
        val values = leaf.getNumericDocValues("foo")!!
        assertEquals(0, values.nextDoc())
        assertEquals(-1.0, Double.fromBits(values.longValue()), 0.0)
        assertEquals(1, values.nextDoc())
        assertEquals(7.0, Double.fromBits(values.longValue()), 0.0)
        assertEquals(2, values.nextDoc())
        assertEquals(18.0, Double.fromBits(values.longValue()), 0.0)
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testBasicMultiValuedDouble() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField("foo", SortField.Type.DOUBLE))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(NumericDocValuesField("id", 3))
        doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(7.54)))
        doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(27.0)))
        w.addDocument(doc)
        // so we get more than one segment, so that forceMerge actually does merge, since we only get a
        // sorted segment by merging:
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("id", 1))
        doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(-1.0)))
        doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(0.0)))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("id", 2))
        doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(7.0)))
        doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(7.67)))
        w.addDocument(doc)
        w.forceMerge(1)

        val r = DirectoryReader.open(w)
        val leaf = getOnlyLeafReader(r)
        assertEquals(3, leaf.maxDoc())
        val values = leaf.getNumericDocValues("id")!!
        assertEquals(0, values.nextDoc())
        assertEquals(1L, values.longValue())
        assertEquals(1, values.nextDoc())
        assertEquals(2L, values.longValue())
        assertEquals(2, values.nextDoc())
        assertEquals(3L, values.longValue())
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMissingDoubleFirst() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField = SortField("foo", SortField.Type.DOUBLE, reverse)
            sortField.missingValue = Double.NEGATIVE_INFINITY
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(DoubleDocValuesField("foo", 18.0))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            w.addDocument(Document())
            w.commit()

            doc = Document()
            doc.add(DoubleDocValuesField("foo", 7.0))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("foo")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(18.0, Double.fromBits(values.longValue()), 0.0)
                assertEquals(1, values.nextDoc())
                assertEquals(7.0, Double.fromBits(values.longValue()), 0.0)
            } else {
                assertEquals(1, values.nextDoc())
                assertEquals(7.0, Double.fromBits(values.longValue()), 0.0)
                assertEquals(2, values.nextDoc())
                assertEquals(18.0, Double.fromBits(values.longValue()), 0.0)
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingMultiValuedDoubleFirst() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField: SortField = SortedNumericSortField("foo", SortField.Type.DOUBLE, reverse)
            sortField.missingValue = Double.NEGATIVE_INFINITY
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("id", 3))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(18.0)))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(18.76)))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            doc = Document()
            doc.add(NumericDocValuesField("id", 1))
            w.addDocument(doc)
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("id", 2))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(7.0)))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(70.0)))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("id")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(3L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(1L, values.longValue())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(1L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(3L, values.longValue())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingDoubleLast() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField = SortField("foo", SortField.Type.DOUBLE, reverse)
            sortField.missingValue = Double.POSITIVE_INFINITY
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(DoubleDocValuesField("foo", 18.0))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            w.addDocument(Document())
            w.commit()

            doc = Document()
            doc.add(DoubleDocValuesField("foo", 7.0))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("foo")!!
            if (reverse) {
                assertEquals(1, values.nextDoc())
                assertEquals(18.0, Double.fromBits(values.longValue()), 0.0)
                assertEquals(2, values.nextDoc())
                assertEquals(7.0, Double.fromBits(values.longValue()), 0.0)
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(7.0, Double.fromBits(values.longValue()), 0.0)
                assertEquals(1, values.nextDoc())
                assertEquals(18.0, Double.fromBits(values.longValue()), 0.0)
            }
            assertEquals(NO_MORE_DOCS, values.nextDoc())
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingMultiValuedDoubleLast() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField: SortField = SortedNumericSortField("foo", SortField.Type.DOUBLE, reverse)
            sortField.missingValue = Double.POSITIVE_INFINITY
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("id", 2))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(18.0)))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(8262.0)))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            doc = Document()
            doc.add(NumericDocValuesField("id", 3))
            w.addDocument(doc)
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("id", 1))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(7.0)))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.doubleToSortableLong(7.87)))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("id")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(3L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(1L, values.longValue())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(1L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(3L, values.longValue())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testBasicFloat() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortField("foo", SortField.Type.FLOAT))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(FloatDocValuesField("foo", 18.0f))
        w.addDocument(doc)
        // so we get more than one segment, so that forceMerge actually does merge, since we only get a
        // sorted segment by merging:
        w.commit()

        doc = Document()
        doc.add(FloatDocValuesField("foo", -1.0f))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(FloatDocValuesField("foo", 7.0f))
        w.addDocument(doc)
        w.forceMerge(1)

        val r = DirectoryReader.open(w)
        val leaf = getOnlyLeafReader(r)
        assertEquals(3, leaf.maxDoc())
        val values = leaf.getNumericDocValues("foo")!!
        assertEquals(0, values.nextDoc())
        assertEquals(-1.0f, Float.fromBits(values.longValue().toInt()), 0.0f)
        assertEquals(1, values.nextDoc())
        assertEquals(7.0f, Float.fromBits(values.longValue().toInt()), 0.0f)
        assertEquals(2, values.nextDoc())
        assertEquals(18.0f, Float.fromBits(values.longValue().toInt()), 0.0f)
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testBasicMultiValuedFloat() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField("foo", SortField.Type.FLOAT))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(NumericDocValuesField("id", 3))
        doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(18.0f).toLong()))
        doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(29.0f).toLong()))
        w.addDocument(doc)
        // so we get more than one segment, so that forceMerge actually does merge, since we only get a
        // sorted segment by merging:
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("id", 1))
        doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(-1.0f).toLong()))
        doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(34.0f).toLong()))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("id", 2))
        doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(7.0f).toLong()))
        w.addDocument(doc)
        w.forceMerge(1)

        val r = DirectoryReader.open(w)
        val leaf = getOnlyLeafReader(r)
        assertEquals(3, leaf.maxDoc())
        val values = leaf.getNumericDocValues("id")!!
        assertEquals(0, values.nextDoc())
        assertEquals(1L, values.longValue())
        assertEquals(1, values.nextDoc())
        assertEquals(2L, values.longValue())
        assertEquals(2, values.nextDoc())
        assertEquals(3L, values.longValue())
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMissingFloatFirst() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField = SortField("foo", SortField.Type.FLOAT, reverse)
            sortField.missingValue = Float.NEGATIVE_INFINITY
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(FloatDocValuesField("foo", 18.0f))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            w.addDocument(Document())
            w.commit()

            doc = Document()
            doc.add(FloatDocValuesField("foo", 7.0f))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("foo")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(18.0f, Float.fromBits(values.longValue().toInt()), 0.0f)
                assertEquals(1, values.nextDoc())
                assertEquals(7.0f, Float.fromBits(values.longValue().toInt()), 0.0f)
            } else {
                assertEquals(1, values.nextDoc())
                assertEquals(7.0f, Float.fromBits(values.longValue().toInt()), 0.0f)
                assertEquals(2, values.nextDoc())
                assertEquals(18.0f, Float.fromBits(values.longValue().toInt()), 0.0f)
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingMultiValuedFloatFirst() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField: SortField = SortedNumericSortField("foo", SortField.Type.FLOAT, reverse)
            sortField.missingValue = Float.NEGATIVE_INFINITY
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("id", 3))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(18.0f).toLong()))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(726.0f).toLong()))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            doc = Document()
            doc.add(NumericDocValuesField("id", 1))
            w.addDocument(doc)
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("id", 2))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(7.0f).toLong()))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(18.0f).toLong()))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("id")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(3L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(1L, values.longValue())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(1L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(3L, values.longValue())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingFloatLast() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField = SortField("foo", SortField.Type.FLOAT, reverse)
            sortField.missingValue = Float.POSITIVE_INFINITY
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(FloatDocValuesField("foo", 18.0f))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            w.addDocument(Document())
            w.commit()

            doc = Document()
            doc.add(FloatDocValuesField("foo", 7.0f))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("foo")!!
            if (reverse) {
                assertEquals(1, values.nextDoc())
                assertEquals(18.0f, Float.fromBits(values.longValue().toInt()), 0.0f)
                assertEquals(2, values.nextDoc())
                assertEquals(7.0f, Float.fromBits(values.longValue().toInt()), 0.0f)
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(7.0f, Float.fromBits(values.longValue().toInt()), 0.0f)
                assertEquals(1, values.nextDoc())
                assertEquals(18.0f, Float.fromBits(values.longValue().toInt()), 0.0f)
            }
            assertEquals(NO_MORE_DOCS, values.nextDoc())
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testMissingMultiValuedFloatLast() {
        for (reverse in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField: SortField = SortedNumericSortField("foo", SortField.Type.FLOAT, reverse)
            sortField.missingValue = Float.POSITIVE_INFINITY
            val indexSort = Sort(sortField)
            iwc.setIndexSort(indexSort)
            val w = IndexWriter(dir, iwc)
            var doc = Document()
            doc.add(NumericDocValuesField("id", 2))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(726.0f).toLong()))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(18.0f).toLong()))
            w.addDocument(doc)
            // so we get more than one segment, so that forceMerge actually does merge, since we only get
            // a sorted segment by merging:
            w.commit()

            // missing
            doc = Document()
            doc.add(NumericDocValuesField("id", 3))
            w.addDocument(doc)
            w.commit()

            doc = Document()
            doc.add(NumericDocValuesField("id", 1))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(12.67f).toLong()))
            doc.add(SortedNumericDocValuesField("foo", NumericUtils.floatToSortableInt(7.0f).toLong()))
            w.addDocument(doc)
            w.forceMerge(1)

            val r = DirectoryReader.open(w)
            val leaf = getOnlyLeafReader(r)
            assertEquals(3, leaf.maxDoc())
            val values = leaf.getNumericDocValues("id")!!
            if (reverse) {
                assertEquals(0, values.nextDoc())
                assertEquals(3L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(1L, values.longValue())
            } else {
                assertEquals(0, values.nextDoc())
                assertEquals(1L, values.longValue())
                assertEquals(1, values.nextDoc())
                assertEquals(2L, values.longValue())
                assertEquals(2, values.nextDoc())
                assertEquals(3L, values.longValue())
            }
            r.close()
            w.close()
            dir.close()
        }
    }

    @Test
    fun testRandom1() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortField("foo", SortField.Type.LONG))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        val numDocs = atLeast(20) // TODO reduced from 200 to 20 for dev speed
        val deleted = FixedBitSet(numDocs)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(NumericDocValuesField("foo", random().nextInt(20).toLong()))
            doc.add(StringField("id", i.toString(), Field.Store.YES))
            doc.add(NumericDocValuesField("id", i.toLong()))
            w.addDocument(doc)
            if (random().nextInt(5) == 0) {
                DirectoryReader.open(w).close()
            } else if (random().nextInt(30) == 0) {
                w.forceMerge(2)
            } else if (random().nextInt(4) == 0) {
                val id = TestUtil.nextInt(random(), 0, i)
                deleted.set(id)
                w.deleteDocuments(Term("id", id.toString()))
            }
        }

        // Check that segments are sorted
        val reader = DirectoryReader.open(w)
        for (ctx in reader.leaves()) {
            val leaf = ctx.reader() as SegmentReader
            val info = leaf.segmentInfo.info
            when (info.diagnostics[IndexWriter.SOURCE]) {
                IndexWriter.SOURCE_FLUSH, IndexWriter.SOURCE_MERGE -> {
                    assertEquals(indexSort, info.indexSort)
                    val values = leaf.getNumericDocValues("foo")!!
                    var previous = Long.MIN_VALUE
                    for (i in 0..<leaf.maxDoc()) {
                        assertEquals(i, values.nextDoc())
                        val value = values.longValue()
                        assertTrue(value >= previous)
                        previous = value
                    }
                }

                else -> fail()
            }
        }

        // Now check that the index is consistent
        val searcher = newSearcher(reader)
        val storedFields = reader.storedFields()
        for (i in 0..<numDocs) {
            val termQuery = TermQuery(Term("id", i.toString()))
            val topDocs = searcher.search(termQuery, 1)
            if (deleted.get(i)) {
                assertEquals(0L, topDocs.totalHits.value)
            } else {
                assertEquals(1L, topDocs.totalHits.value)
                val values = MultiDocValues.getNumericValues(reader, "id")!!
                assertEquals(topDocs.scoreDocs[0].doc, values.advance(topDocs.scoreDocs[0].doc))
                assertEquals(i.toLong(), values.longValue())
                val document = storedFields.document(topDocs.scoreDocs[0].doc)
                assertEquals(i.toString(), document.get("id"))
            }
        }

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMultiValuedRandom1() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField("foo", SortField.Type.LONG))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        val numDocs = atLeast(20) // TODO reduced from 200 to 20 for dev speed
        val deleted = FixedBitSet(numDocs)
        for (i in 0..<numDocs) {
            val doc = Document()
            val num = random().nextInt(10)
            for (j in 0..<num) {
                doc.add(SortedNumericDocValuesField("foo", random().nextInt(2000).toLong()))
            }
            doc.add(StringField("id", i.toString(), Field.Store.YES))
            doc.add(NumericDocValuesField("id", i.toLong()))
            w.addDocument(doc)
            if (random().nextInt(5) == 0) {
                DirectoryReader.open(w).close()
            } else if (random().nextInt(30) == 0) {
                w.forceMerge(2)
            } else if (random().nextInt(4) == 0) {
                val id = TestUtil.nextInt(random(), 0, i)
                deleted.set(id)
                w.deleteDocuments(Term("id", id.toString()))
            }
        }

        val reader = DirectoryReader.open(w)
        // Now check that the index is consistent
        val searcher = newSearcher(reader)
        val storedFields = reader.storedFields()
        for (i in 0..<numDocs) {
            val termQuery = TermQuery(Term("id", i.toString()))
            val topDocs = searcher.search(termQuery, 1)
            if (deleted.get(i)) {
                assertEquals(0L, topDocs.totalHits.value)
            } else {
                assertEquals(1L, topDocs.totalHits.value)
                val values = MultiDocValues.getNumericValues(reader, "id")!!
                assertEquals(topDocs.scoreDocs[0].doc, values.advance(topDocs.scoreDocs[0].doc))
                assertEquals(i.toLong(), values.longValue())
                val document = storedFields.document(topDocs.scoreDocs[0].doc)
                assertEquals(i.toString(), document.get("id"))
            }
        }

        reader.close()
        w.close()
        dir.close()
    }

    internal class UpdateRunnable(
        private val numDocs: Int,
        private val random: Random,
        private val latch: CountDownLatch,
        private val updateCount: AtomicInteger,
        private val w: IndexWriter,
        private val values: MutableMap<Int, Long>,
        private val lock: ReentrantLock,
    ) {
        fun run() {
            try {
                latch.await()
                while (updateCount.fetchAndAdd(-1) - 1 >= 0) {
                    val id = random.nextInt(numDocs)
                    val value = random.nextInt(20).toLong()
                    val doc = Document()
                    doc.add(StringField("id", id.toString(), Field.Store.NO))
                    doc.add(NumericDocValuesField("foo", value))

                    lock.lock()
                    try {
                        w.updateDocument(Term("id", id.toString()), doc)
                        values[id] = value
                    } finally {
                        lock.unlock()
                    }

                    when (random.nextInt(10)) {
                        0, 1 -> DirectoryReader.open(w).close()
                        2 -> w.forceMerge(3)
                    }
                }
            } catch (e: Throwable) {
                throw RuntimeException(e)
            }
        }
    }

    // There is tricky logic to resolve deletes that happened while merging
    @Test
    fun testConcurrentUpdates() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortField("foo", SortField.Type.LONG))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        val values = mutableMapOf<Int, Long>()
        val lock = ReentrantLock()

        val numDocs = atLeast(10) // TODO reduced from 100 to 10 for dev speed
        val updateCount = AtomicInteger(atLeast(100)) // TODO reduced from 1000 to 100 for dev speed
        val latch = CountDownLatch(1)
        val threads = Array(2) { Thread {} }
        for (i in threads.indices) {
            val r = Random(random().nextLong())
            threads[i] =
                Thread(UpdateRunnable(numDocs, r, latch, updateCount, w, values, lock)::run)
        }
        for (thread in threads) {
            thread.start()
        }
        latch.countDown()
        for (thread in threads) {
            thread.join()
        }
        w.forceMerge(1)
        val reader = DirectoryReader.open(w)
        val searcher = newSearcher(reader)
        for (i in 0..<numDocs) {
            val topDocs = searcher.search(TermQuery(Term("id", i.toString())), 1)
            if (values.containsKey(i) == false) {
                assertEquals(0L, topDocs.totalHits.value)
            } else {
                assertEquals(1L, topDocs.totalHits.value)
                val dvs = MultiDocValues.getNumericValues(reader, "foo")!!
                val docID = topDocs.scoreDocs[0].doc
                assertEquals(docID, dvs.advance(docID))
                assertEquals(values[i], dvs.longValue())
            }
        }
        reader.close()
        w.close()
        dir.close()
    }

    // docvalues fields involved in the index sort cannot be updated
    @Test
    fun testBadDVUpdate() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortField("foo", SortField.Type.LONG))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(StringField("id", newBytesRef("0"), Field.Store.NO))
        doc.add(NumericDocValuesField("foo", random().nextInt().toLong()))
        w.addDocument(doc)
        w.commit()
        var exc = expectThrows(IllegalArgumentException::class) {
            w.updateDocValues(Term("id", "0"), NumericDocValuesField("foo", -1))
        }
        assertEquals(
            "cannot update docvalues field involved in the index sort, field=foo, sort=<long: \"foo\">",
            exc.message
        )
        exc = expectThrows(IllegalArgumentException::class) {
            w.updateNumericDocValue(Term("id", "0"), "foo", -1)
        }
        assertEquals(
            "cannot update docvalues field involved in the index sort, field=foo, sort=<long: \"foo\">",
            exc.message
        )
        w.close()
        dir.close()
    }

    internal class DVUpdateRunnable(
        private val numDocs: Int,
        private val random: Random,
        private val latch: CountDownLatch,
        private val updateCount: AtomicInteger,
        private val w: IndexWriter,
        private val values: MutableMap<Int, Long>,
        private val lock: ReentrantLock,
    ) {
        fun run() {
            try {
                latch.await()
                while (updateCount.fetchAndAdd(-1) - 1 >= 0) {
                    val id = random.nextInt(numDocs)
                    val value = random.nextInt(20).toLong()

                    lock.lock()
                    try {
                        w.updateDocValues(Term("id", id.toString()), NumericDocValuesField("bar", value))
                        values[id] = value
                    } finally {
                        lock.unlock()
                    }

                    when (random.nextInt(10)) {
                        0, 1 -> DirectoryReader.open(w).close()
                        2 -> w.forceMerge(3)
                    }
                }
            } catch (e: Throwable) {
                throw RuntimeException(e)
            }
        }
    }

    // There is tricky logic to resolve dv updates that happened while merging
    @Test
    fun testConcurrentDVUpdates() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortField("foo", SortField.Type.LONG))
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        val values = mutableMapOf<Int, Long>()
        val lock = ReentrantLock()

        val numDocs = atLeast(10) // TODO reduced from 100 to 10 for dev speed
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.NO))
            doc.add(NumericDocValuesField("foo", random().nextInt().toLong()))
            doc.add(NumericDocValuesField("bar", -1))
            w.addDocument(doc)
            values[i] = -1L
        }
        val threads = Array(2) { Thread {} }
        val updateCount = AtomicInteger(atLeast(100)) // TODO reduced from 1000 to 100 for dev speed
        val latch = CountDownLatch(1)
        for (i in threads.indices) {
            val r = Random(random().nextLong())
            threads[i] =
                Thread(DVUpdateRunnable(numDocs, r, latch, updateCount, w, values, lock)::run)
        }
        for (thread in threads) {
            thread.start()
        }
        latch.countDown()
        for (thread in threads) {
            thread.join()
        }
        w.forceMerge(1)
        val reader = DirectoryReader.open(w)
        val searcher = newSearcher(reader)
        for (i in 0..<numDocs) {
            val topDocs = searcher.search(TermQuery(Term("id", i.toString())), 1)
            assertEquals(1L, topDocs.totalHits.value)
            val dvs = MultiDocValues.getNumericValues(reader, "bar")!!
            val hitDoc = topDocs.scoreDocs[0].doc
            assertEquals(hitDoc, dvs.advance(hitDoc))
            assertEquals(values[i], dvs.longValue())
        }
        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testBadAddIndexes() {
        val dir: Directory = newDirectory()
        val indexSort = Sort(SortField("foo", SortField.Type.LONG))
        val iwc1 = newIndexWriterConfig()
        iwc1.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc1)
        w.addDocument(Document())
        val indexSorts = listOf<Sort?>(null, Sort(SortField("bar", SortField.Type.LONG)))
        for (sort in indexSorts) {
            val dir2: Directory = newDirectory()
            val iwc2 = newIndexWriterConfig()
            if (sort != null) {
                iwc2.setIndexSort(sort)
            }
            val w2 = IndexWriter(dir2, iwc2)
            w2.addDocument(Document())
            val reader = DirectoryReader.open(w2)
            w2.close()
            var expected = expectThrows(IllegalArgumentException::class) {
                w.addIndexes(dir2)
            }
            assertTrue(expected.message!!.contains("cannot change index sort"))
            val codecReaders = Array(reader.leaves().size) { i ->
                reader.leaves()[i].reader() as CodecReader
            }
            expected = expectThrows(IllegalArgumentException::class) {
                w.addIndexes(*codecReaders)
            }
            assertTrue(expected.message!!.contains("cannot change index sort"))

            reader.close()
            dir2.close()
        }
        w.close()
        dir.close()
    }

    fun testAddIndexes(withDeletes: Boolean, useReaders: Boolean) {
        val dir: Directory = newDirectory()
        val iwc1 = newIndexWriterConfig()
        val useParent = rarely()
        if (useParent) {
            iwc1.setParentField("___parent")
        }
        val indexSort =
            Sort(
                SortField("foo", SortField.Type.LONG),
                SortField("bar", SortField.Type.LONG)
            )
        iwc1.setIndexSort(indexSort)
        val w = RandomIndexWriter(random(), dir, iwc1)
        val numDocs = atLeast(100)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.NO))
            doc.add(NumericDocValuesField("foo", random().nextInt(20).toLong()))
            doc.add(NumericDocValuesField("bar", random().nextInt(20).toLong()))
            w.addDocument(doc)
        }
        if (withDeletes) {
            var i = random().nextInt(5)
            while (i < numDocs) {
                w.deleteDocuments(Term("id", i.toString()))
                i += TestUtil.nextInt(random(), 1, 5)
            }
        }
        if (random().nextBoolean()) {
            w.forceMerge(1)
        }
        val reader = w.reader
        w.close()

        val dir2: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        if (random().nextBoolean()) {
            // test congruent index sort
            iwc.setIndexSort(Sort(SortField("foo", SortField.Type.LONG)))
        } else {
            iwc.setIndexSort(indexSort)
        }
        if (useParent) {
            iwc.setParentField("___parent")
        }
        val w2 = IndexWriter(dir2, iwc)

        if (useReaders) {
            val codecReaders = Array(reader.leaves().size) { i ->
                reader.leaves()[i].reader() as CodecReader
            }
            w2.addIndexes(*codecReaders)
        } else {
            w2.addIndexes(dir)
        }
        val reader2 = DirectoryReader.open(w2)
        val searcher = newSearcher(reader)
        val searcher2 = newSearcher(reader2)
        for (i in 0..<numDocs) {
            val query = TermQuery(Term("id", i.toString()))
            val topDocs = searcher.search(query, 1)
            val topDocs2 = searcher2.search(query, 1)
            assertEquals(topDocs.totalHits.value, topDocs2.totalHits.value)
            if (topDocs.totalHits.value == 1L) {
                val dvs1 = MultiDocValues.getNumericValues(reader, "foo")!!
                val hitDoc1 = topDocs.scoreDocs[0].doc
                assertEquals(hitDoc1, dvs1.advance(hitDoc1))
                val value1 = dvs1.longValue()
                val dvs2 = MultiDocValues.getNumericValues(reader2, "foo")!!
                val hitDoc2 = topDocs2.scoreDocs[0].doc
                assertEquals(hitDoc2, dvs2.advance(hitDoc2))
                val value2 = dvs2.longValue()
                assertEquals(value1, value2)
            }
        }

        IOUtils.close(reader, reader2, w2, dir, dir2)
    }

    @Test
    fun testAddIndexes() {
        testAddIndexes(false, true)
    }

    @Test
    fun testAddIndexesWithDeletions() {
        testAddIndexes(true, true)
    }

    @Test
    fun testAddIndexesWithDirectory() {
        testAddIndexes(false, false)
    }

    @Test
    fun testAddIndexesWithDeletionsAndDirectory() {
        testAddIndexes(true, false)
    }

    @Test
    fun testBadSort() {
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val expected = expectThrows(IllegalArgumentException::class) {
            iwc.setIndexSort(Sort.RELEVANCE)
        }
        assertEquals("Cannot sort index with sort field <score>", expected.message)
    }

    // you can't change the index sort on an existing index:
    @Test
    fun testIllegalChangeSort() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setIndexSort(Sort(SortField("foo", SortField.Type.LONG)))
        val w = IndexWriter(dir, iwc)
        w.addDocument(Document())
        DirectoryReader.open(w).close()
        w.addDocument(Document())
        w.forceMerge(1)
        w.close()

        val iwc2 = IndexWriterConfig(MockAnalyzer(random()))
        iwc2.setIndexSort(Sort(SortField("bar", SortField.Type.LONG)))
        val e = expectThrows(IllegalArgumentException::class) {
            IndexWriter(dir, iwc2)
        }
        val message = e.message!!
        assertTrue(message.contains("cannot change previous indexSort=<long: \"foo\">"))
        assertTrue(message.contains("to new indexSort=<long: \"bar\">"))
        dir.close()
    }

    class NormsSimilarity(
        private val `in`: Similarity
    ) : Similarity() {
        override fun computeNorm(state: FieldInvertState): Long {
            return if (state.name == "norms") {
                state.length.toLong()
            } else {
                `in`.computeNorm(state)
            }
        }

        override fun scorer(
            boost: Float,
            collectionStats: CollectionStatistics,
            vararg termStats: TermStatistics
        ): Similarity.SimScorer {
            return `in`.scorer(boost, collectionStats, *termStats)
        }
    }

    class PositionsTokenStream : TokenStream() {
        private val term = addAttribute(CharTermAttribute::class)
        private val payload = addAttribute(PayloadAttribute::class)
        private val offset = addAttribute(OffsetAttribute::class)

        private var pos = 0
        private var off = 0

        override fun incrementToken(): Boolean {
            if (pos == 0) {
                return false
            }

            clearAttributes()
            term.append("#all#")
            payload.payload = newBytesRef(pos.toString())
            offset.setOffset(off, off)
            --pos
            ++off
            return true
        }

        fun setId(id: Int) {
            pos = id / 10 + 1
            off = 0
        }
    }

    @Test
    fun testRandom2() {
        val numDocs = atLeast(100)

        val POSITIONS_TYPE = FieldType(TextField.TYPE_NOT_STORED)
        POSITIONS_TYPE.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        POSITIONS_TYPE.freeze()

        val TERM_VECTORS_TYPE = FieldType(TextField.TYPE_NOT_STORED)
        TERM_VECTORS_TYPE.setStoreTermVectors(true)
        TERM_VECTORS_TYPE.freeze()

        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = MockTokenizer()
                return TokenStreamComponents(tokenizer, tokenizer)
            }
        }

        val docs = mutableListOf<Document>()
        for (i in 0..<numDocs) {
            val id = i * 10
            val doc = Document()
            doc.add(StringField("id", id.toString(), Field.Store.YES))
            doc.add(StringField("docs", "#all#", Field.Store.NO))
            val positions = PositionsTokenStream()
            positions.setId(id)
            doc.add(Field("positions", positions, POSITIONS_TYPE))
            doc.add(NumericDocValuesField("numeric", id.toLong()))
            val value = (0 until id).joinToString(" ") { id.toString() }
            val norms = TextField("norms", value, Field.Store.NO)
            doc.add(norms)
            doc.add(BinaryDocValuesField("binary", newBytesRef(id.toString())))
            doc.add(SortedDocValuesField("sorted", newBytesRef(id.toString())))
            doc.add(SortedSetDocValuesField("multi_valued_string", newBytesRef(id.toString())))
            doc.add(SortedSetDocValuesField("multi_valued_string", newBytesRef((id + 1).toString())))
            doc.add(SortedNumericDocValuesField("multi_valued_numeric", id.toLong()))
            doc.add(SortedNumericDocValuesField("multi_valued_numeric", (id + 1).toLong()))
            doc.add(Field("term_vectors", id.toString(), TERM_VECTORS_TYPE))
            val bytes = ByteArray(4)
            NumericUtils.intToSortableBytes(id, bytes, 0)
            doc.add(BinaryPoint("points", arrayOf(bytes)))
            docs.add(doc)
        }

        // Must use the same seed for both RandomIndexWriters so they behave identically
        val seed = random().nextLong()

        // We add document already in ID order for the first writer:
        val dir1 = newFSDirectory(createTempDir())

        val random1 = Random(seed)
        val iwc1 = newIndexWriterConfig(random1, a)
        iwc1.setSimilarity(NormsSimilarity(iwc1.similarity)) // for testing norms field
        // preserve docIDs
        iwc1.setMergePolicy(newLogMergePolicy())
        if (VERBOSE) {
            println("TEST: now index pre-sorted")
        }
        val w1 = RandomIndexWriter(random1, dir1, iwc1)
        for (doc in docs) {
            ((doc.getField("positions") as Field).tokenStreamValue() as PositionsTokenStream).setId(doc.get("id")!!.toInt())
            w1.addDocument(doc)
        }

        // We shuffle documents, but set index sort, for the second writer:
        val dir2 = newFSDirectory(createTempDir())

        val random2 = Random(seed)
        val iwc2 = newIndexWriterConfig(random2, a)
        iwc2.setSimilarity(NormsSimilarity(iwc2.similarity)) // for testing norms field

        val sort = Sort(SortField("numeric", SortField.Type.INT))
        iwc2.setIndexSort(sort)

        docs.shuffle(random())
        if (VERBOSE) {
            println("TEST: now index with index-time sorting")
        }
        val w2 = RandomIndexWriter(random2, dir2, iwc2)
        var count = 0
        val commitAtCount = TestUtil.nextInt(random(), 1, numDocs - 1)
        for (doc in docs) {
            ((doc.getField("positions") as Field).tokenStreamValue() as PositionsTokenStream).setId(doc.get("id")!!.toInt())
            if (count++ == commitAtCount) {
                // Ensure forceMerge really does merge
                w2.commit()
            }
            w2.addDocument(doc)
        }
        if (VERBOSE) {
            println("TEST: now force merge")
        }
        w2.forceMerge(1)

        val r1 = w1.getReader(true, false)
        val r2 = w2.getReader(true, false)
        if (VERBOSE) {
            println("TEST: now compare r1=$r1 r2=$r2")
        }
        assertEquals(sort, getOnlyLeafReader(r2).metaData.sort)
        assertReaderEquals("left: sorted by hand; right: sorted by Lucene", r1, r2)
        IOUtils.close(w1, w2, r1, r2, dir1, dir2)
    }

    private class RandomDoc(id: Int) {
        val intValue: Int
        val intValues: IntArray
        val longValue: Long
        val longValues: LongArray
        val floatValue: Float
        val floatValues: FloatArray
        val doubleValue: Double
        val doubleValues: DoubleArray
        val bytesValue: ByteArray
        val bytesValues: Array<ByteArray?>

        init {
            val random = LuceneTestCase.random()
            intValue = random.nextInt()
            longValue = random.nextLong()
            floatValue = random.nextFloat()
            doubleValue = random.nextDouble()
            bytesValue = ByteArray(TestUtil.nextInt(random, 1, 50))
            random.nextBytes(bytesValue)

            val numValues = random.nextInt(10)
            intValues = IntArray(numValues)
            longValues = LongArray(numValues)
            floatValues = FloatArray(numValues)
            doubleValues = DoubleArray(numValues)
            bytesValues = arrayOfNulls(numValues)
            for (i in 0..<numValues) {
                intValues[i] = random.nextInt()
                longValues[i] = random.nextLong()
                floatValues[i] = random.nextFloat()
                doubleValues[i] = random.nextDouble()
                bytesValues[i] = ByteArray(TestUtil.nextInt(random, 1, 50))
                random.nextBytes(bytesValue)
            }
        }
    }

    private fun randomIndexSortField(): SortField {
        val reversed = random().nextBoolean()
        val sortField = when (random().nextInt(10)) {
            0 -> SortField("int", SortField.Type.INT, reversed).also {
                if (random().nextBoolean()) {
                    it.missingValue = random().nextInt()
                }
            }

            1 -> SortedNumericSortField("multi_valued_int", SortField.Type.INT, reversed).also {
                if (random().nextBoolean()) {
                    it.missingValue = random().nextInt()
                }
            }

            2 -> SortField("long", SortField.Type.LONG, reversed).also {
                if (random().nextBoolean()) {
                    it.missingValue = random().nextLong()
                }
            }

            3 -> SortedNumericSortField("multi_valued_long", SortField.Type.LONG, reversed).also {
                if (random().nextBoolean()) {
                    it.missingValue = random().nextLong()
                }
            }

            4 -> SortField("float", SortField.Type.FLOAT, reversed).also {
                if (random().nextBoolean()) {
                    it.missingValue = random().nextFloat()
                }
            }

            5 -> SortedNumericSortField("multi_valued_float", SortField.Type.FLOAT, reversed).also {
                if (random().nextBoolean()) {
                    it.missingValue = random().nextFloat()
                }
            }

            6 -> SortField("double", SortField.Type.DOUBLE, reversed).also {
                if (random().nextBoolean()) {
                    it.missingValue = random().nextDouble()
                }
            }

            7 -> SortedNumericSortField("multi_valued_double", SortField.Type.DOUBLE, reversed).also {
                if (random().nextBoolean()) {
                    it.missingValue = random().nextDouble()
                }
            }

            8 -> SortField("bytes", SortField.Type.STRING, reversed).also {
                if (random().nextBoolean()) {
                    it.missingValue = SortField.STRING_LAST
                }
            }

            9 -> SortedSetSortField("multi_valued_bytes", reversed).also {
                if (random().nextBoolean()) {
                    it.missingValue = SortField.STRING_LAST
                }
            }

            else -> {
                fail()
            }
        }
        return sortField
    }

    private fun randomSort(): Sort {
        // at least 2
        val numFields = TestUtil.nextInt(random(), 2, 4)
        val sortFields = arrayOfNulls<SortField>(numFields)
        for (i in 0..<numFields - 1) {
            val sortField = randomIndexSortField()
            sortFields[i] = sortField
        }

        // tie-break by id:
        sortFields[numFields - 1] = SortField("id", SortField.Type.INT)

        return Sort(*sortFields.requireNoNulls())
    }

    // pits index time sorting against query time sorting
    @Test
    fun testRandom3() {
        val numDocs = atLeast(1000)
        val docs = mutableListOf<RandomDoc>()

        val sort = randomSort()
        if (VERBOSE) {
            println("TEST: numDocs=$numDocs use sort=$sort")
        }

        // no index sorting, all search-time sorting:
        val dir1 = newFSDirectory(createTempDir())
        val iwc1 = newIndexWriterConfig(MockAnalyzer(random()))
        val w1 = IndexWriter(dir1, iwc1)

        // use index sorting:
        val dir2 = newFSDirectory(createTempDir())
        val iwc2 = newIndexWriterConfig(MockAnalyzer(random()))
        iwc2.setIndexSort(sort)
        val w2 = IndexWriter(dir2, iwc2)

        val toDelete = mutableSetOf<Int>()

        val deleteChance = random().nextDouble()

        for (id in 0..<numDocs) {
            val docValues = RandomDoc(id)
            docs.add(docValues)
            if (VERBOSE) {
                println("TEST: doc id=$id")
                println("  int=${docValues.intValue}")
                println("  long=${docValues.longValue}")
                println("  float=${docValues.floatValue}")
                println("  double=${docValues.doubleValue}")
                println("  bytes=${newBytesRef(docValues.bytesValue)}")
                println("  mvf=${docValues.floatValues.contentToString()}")
            }

            val doc = Document()
            doc.add(StringField("id", id.toString(), Field.Store.YES))
            doc.add(NumericDocValuesField("id", id.toLong()))
            doc.add(NumericDocValuesField("int", docValues.intValue.toLong()))
            doc.add(NumericDocValuesField("long", docValues.longValue))
            doc.add(DoubleDocValuesField("double", docValues.doubleValue))
            doc.add(FloatDocValuesField("float", docValues.floatValue))
            doc.add(SortedDocValuesField("bytes", newBytesRef(docValues.bytesValue)))

            for (value in docValues.intValues) {
                doc.add(SortedNumericDocValuesField("multi_valued_int", value.toLong()))
            }

            for (value in docValues.longValues) {
                doc.add(SortedNumericDocValuesField("multi_valued_long", value))
            }

            for (value in docValues.floatValues) {
                doc.add(
                    SortedNumericDocValuesField(
                        "multi_valued_float",
                        NumericUtils.floatToSortableInt(value).toLong()
                    )
                )
            }

            for (value in docValues.doubleValues) {
                doc.add(
                    SortedNumericDocValuesField(
                        "multi_valued_double",
                        NumericUtils.doubleToSortableLong(value)
                    )
                )
            }

            for (value in docValues.bytesValues) {
                doc.add(SortedSetDocValuesField("multi_valued_bytes", newBytesRef(value!!)))
            }

            w1.addDocument(doc)
            w2.addDocument(doc)
            if (random().nextDouble() < deleteChance) {
                toDelete.add(id)
            }
        }
        for (id in toDelete) {
            w1.deleteDocuments(Term("id", id.toString()))
            w2.deleteDocuments(Term("id", id.toString()))
        }
        val r1 = DirectoryReader.open(w1)
        val s1 = newSearcher(r1)

        if (random().nextBoolean()) {
            val maxSegmentCount = TestUtil.nextInt(random(), 1, 5)
            if (VERBOSE) {
                println("TEST: now forceMerge($maxSegmentCount)")
            }
            w2.forceMerge(maxSegmentCount)
        }

        val r2 = DirectoryReader.open(w2)
        val s2 = newSearcher(r2)

        /*
        System.out.println("TEST: full index:");
        SortedDocValues docValues = MultiDocValues.getSortedValues(r2, "bytes");
        for(int i=0;i<r2.maxDoc();i++) {
          System.out.println("  doc " + i + " id=" + r2.storedFields().document(i).get("id") + " bytes=" + docValues.get(i));
        }
        */

        for (iter in 0..<100) {
            val numHits = TestUtil.nextInt(random(), 1, numDocs)
            if (VERBOSE) {
                println("TEST: iter=$iter numHits=$numHits")
            }

            val hits1 = s1.search(
                MatchAllDocsQuery(),
                TopFieldCollectorManager(sort, numHits, Int.MAX_VALUE)
            )
            val hits2 = s2.search(
                MatchAllDocsQuery(),
                TopFieldCollectorManager(sort, numHits, 1)
            )

            if (VERBOSE) {
                println("  topDocs query-time sort: totalHits=${hits1.totalHits.value}")
                for (scoreDoc in hits1.scoreDocs) {
                    println("    ${scoreDoc.doc}")
                }
                println("  topDocs index-time sort: totalHits=${hits2.totalHits.value}")
                for (scoreDoc in hits2.scoreDocs) {
                    println("    ${scoreDoc.doc}")
                }
            }

            assertEquals(hits2.scoreDocs.size, hits1.scoreDocs.size)
            val storedFields1 = r1.storedFields()
            val storedFields2 = r2.storedFields()
            for (i in hits2.scoreDocs.indices) {
                val hit1 = hits1.scoreDocs[i]
                val hit2 = hits2.scoreDocs[i]
                assertEquals(
                    storedFields1.document(hit1.doc).get("id"),
                    storedFields2.document(hit2.doc).get("id")
                )
                assertContentEquals((hit1 as FieldDoc).fields, (hit2 as FieldDoc).fields)
            }
        }

        IOUtils.close(r1, r2, w1, w2, dir1, dir2)
    }

    @Test
    fun testTieBreak() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig(MockAnalyzer(random()))
        iwc.setIndexSort(Sort(SortField("foo", SortField.Type.STRING)))
        iwc.setMergePolicy(newLogMergePolicy())
        val w = IndexWriter(dir, iwc)
        for (id in 0..<1000) {
            val doc = Document()
            doc.add(StoredField("id", id))
            val value = if (id < 500) {
                "bar2"
            } else {
                "bar1"
            }
            doc.add(SortedDocValuesField("foo", newBytesRef(value)))
            w.addDocument(doc)
            if (id == 500) {
                w.commit()
            }
        }
        w.forceMerge(1)
        val r = DirectoryReader.open(w)
        val storedFields = r.storedFields()
        for (docID in 0..<1000) {
            val expectedID = if (docID < 500) {
                500 + docID
            } else {
                docID - 500
            }
            assertEquals(
                expectedID,
                storedFields.document(docID).getField("id")!!.numericValue()!!.toInt()
            )
        }
        IOUtils.close(r, w, dir)
    }

    @Test
    fun testIndexSortWithSparseField() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val sortField = SortField("dense_int", SortField.Type.INT, true)
        val indexSort = Sort(sortField)
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        val textField = newTextField("sparse_text", "", Field.Store.NO)
        for (i in 0..<128) {
            val doc = Document()
            doc.add(NumericDocValuesField("dense_int", i.toLong()))
            if (i < 64) {
                doc.add(NumericDocValuesField("sparse_int", i.toLong()))
                doc.add(BinaryDocValuesField("sparse_binary", newBytesRef(i.toString())))
                textField.setStringValue("foo")
                doc.add(textField)
            }
            w.addDocument(doc)
        }
        w.commit()
        w.forceMerge(1)
        val r = DirectoryReader.open(w)
        assertEquals(1, r.leaves().size)
        val leafReader = r.leaves()[0].reader()

        val denseValues = leafReader.getNumericDocValues("dense_int")!!
        val sparseValues = leafReader.getNumericDocValues("sparse_int")!!
        val sparseBinaryValues = leafReader.getBinaryDocValues("sparse_binary")!!
        val normsValues = leafReader.getNormValues("sparse_text")!!
        for (docID in 0..<128) {
            assertTrue(denseValues.advanceExact(docID))
            assertEquals(127 - docID, denseValues.longValue().toInt())
            if (docID >= 64) {
                assertTrue(denseValues.advanceExact(docID))
                assertTrue(sparseValues.advanceExact(docID))
                assertTrue(sparseBinaryValues.advanceExact(docID))
                assertTrue(normsValues.advanceExact(docID))
                assertEquals(1L, normsValues.longValue())
                assertEquals(127 - docID, sparseValues.longValue().toInt())
                assertEquals(newBytesRef((127 - docID).toString()), sparseBinaryValues.binaryValue())
            } else {
                assertFalse(sparseBinaryValues.advanceExact(docID))
                assertFalse(sparseValues.advanceExact(docID))
                assertFalse(normsValues.advanceExact(docID))
            }
        }
        IOUtils.close(r, w, dir)
    }

    @Test
    fun testIndexSortOnSparseField() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val sortField = SortField("sparse", SortField.Type.INT, false)
        sortField.missingValue = Int.MIN_VALUE
        val indexSort = Sort(sortField)
        iwc.setIndexSort(indexSort)
        val w = IndexWriter(dir, iwc)
        for (i in 0..<128) {
            val doc = Document()
            if (i < 64) {
                doc.add(NumericDocValuesField("sparse", i.toLong()))
            }
            w.addDocument(doc)
        }
        w.commit()
        w.forceMerge(1)
        val r = DirectoryReader.open(w)
        assertEquals(1, r.leaves().size)
        val leafReader = r.leaves()[0].reader()
        val sparseValues = leafReader.getNumericDocValues("sparse")!!
        for (docID in 0..<128) {
            if (docID >= 64) {
                assertTrue(sparseValues.advanceExact(docID))
                assertEquals(docID - 64, sparseValues.longValue().toInt())
            } else {
                assertFalse(sparseValues.advanceExact(docID))
            }
        }
        IOUtils.close(r, w, dir)
    }

    @Test
    fun testWrongSortFieldType() {
        val dir = newDirectory()
        val dvs = mutableListOf<Field>()
        dvs.add(SortedDocValuesField("field", newBytesRef("")))
        dvs.add(SortedSetDocValuesField("field", newBytesRef("")))
        dvs.add(NumericDocValuesField("field", 42))
        dvs.add(SortedNumericDocValuesField("field", 42))

        val sortFields = mutableListOf<SortField>()
        sortFields.add(SortField("field", SortField.Type.STRING))
        sortFields.add(SortedSetSortField("field", false))
        sortFields.add(SortField("field", SortField.Type.INT))
        sortFields.add(SortedNumericSortField("field", SortField.Type.INT))

        for (i in sortFields.indices) {
            for (j in dvs.indices) {
                if (i == j) {
                    continue
                }
                val indexSort = Sort(sortFields[i])
                val iwc = IndexWriterConfig(MockAnalyzer(random()))
                iwc.setIndexSort(indexSort)
                val w = IndexWriter(dir, iwc)
                val doc = Document()
                doc.add(dvs[j])
                val exc = expectThrows(IllegalArgumentException::class) {
                    w.addDocument(doc)
                }
                assertTrue(exc.message!!.contains("expected field [field] to be "))
                doc.clear()
                doc.add(dvs[i])
                w.addDocument(doc)
                doc.add(dvs[j])
                val exc2 = expectThrows(IllegalArgumentException::class) {
                    w.addDocument(doc)
                }
                assertEquals(
                    "Inconsistency of field data structures across documents for field [field] of doc [2]. doc values type: expected '${dvs[i].fieldType().docValuesType()}', but it has '${dvs[j].fieldType().docValuesType()}'.",
                    exc2.message
                )
                w.rollback()
                IOUtils.close(w)
            }
        }
        IOUtils.close(dir)
    }

    @Test
    fun testDeleteByTermOrQuery() {
        val dir = newDirectory()
        val config = newIndexWriterConfig()
        config.setIndexSort(Sort(SortField("numeric", SortField.Type.LONG)))
        val w = IndexWriter(dir, config)
        val doc = Document()
        val numDocs = random().nextInt(2000) + 5
        val expectedValues = LongArray(numDocs)

        for (i in 0..<numDocs) {
            expectedValues[i] = random().nextInt(Int.MAX_VALUE).toLong()
            doc.clear()
            doc.add(StringField("id", i.toString(), Field.Store.YES))
            doc.add(NumericDocValuesField("numeric", expectedValues[i]))
            w.addDocument(doc)
        }
        val numDeleted = random().nextInt(numDocs) + 1
        for (i in 0..<numDeleted) {
            val idToDelete = random().nextInt(numDocs)
            if (random().nextBoolean()) {
                w.deleteDocuments(TermQuery(Term("id", idToDelete.toString())))
            } else {
                w.deleteDocuments(Term("id", idToDelete.toString()))
            }

            expectedValues[idToDelete] = -random().nextInt(Int.MAX_VALUE).toLong() // force a reordering
            doc.clear()
            doc.add(StringField("id", idToDelete.toString(), Field.Store.YES))
            doc.add(NumericDocValuesField("numeric", expectedValues[idToDelete]))
            w.addDocument(doc)
        }

        var docCount = 0
        val reader = DirectoryReader.open(w)
        for (leafCtx in reader.leaves()) {
            val liveDocs: Bits? = leafCtx.reader().liveDocs
            val values = leafCtx.reader().getNumericDocValues("numeric") ?: continue
            val storedFields = leafCtx.reader().storedFields()
            for (id in 0..<leafCtx.reader().maxDoc()) {
                if (liveDocs != null && !liveDocs.get(id)) {
                    continue
                }
                if (!values.advanceExact(id)) {
                    continue
                }
                val globalId = storedFields.document(id).getField("id")!!.stringValue()!!.toInt()
                assertTrue(values.advanceExact(id))
                assertEquals(expectedValues[globalId], values.longValue())
                docCount++
            }
        }
        assertEquals(docCount, numDocs)
        IOUtils.close(reader, w, dir)
    }

    @Test
    fun testSortDocs() {
        val dir = newDirectory()
        val config = newIndexWriterConfig()
        config.setIndexSort(Sort(SortField("sort", SortField.Type.LONG)))
        val w = IndexWriter(dir, config)
        val doc = Document()
        val sort = NumericDocValuesField("sort", 0L)
        doc.add(sort)
        val field = StringField("field", "a", Field.Store.NO)
        doc.add(field)
        w.addDocument(doc)
        sort.setLongValue(1)
        field.setStringValue("b")
        w.addDocument(doc)
        sort.setLongValue(-1)
        field.setStringValue("a")
        w.addDocument(doc)
        sort.setLongValue(2)
        field.setStringValue("a")
        w.addDocument(doc)
        sort.setLongValue(3)
        field.setStringValue("b")
        w.addDocument(doc)
        w.forceMerge(1)
        val reader = DirectoryReader.open(w)
        w.close()
        val leafReader = getOnlyLeafReader(reader)
        val fieldTerms = leafReader.terms("field")!!.iterator()
        assertEquals(BytesRef("a"), fieldTerms.next())
        var postings = fieldTerms.postings(null, PostingsEnum.ALL.toInt())!!
        assertEquals(0, postings.nextDoc())
        assertEquals(1, postings.nextDoc())
        assertEquals(3, postings.nextDoc())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())
        assertEquals(BytesRef("b"), fieldTerms.next())
        postings = fieldTerms.postings(postings, PostingsEnum.ALL.toInt())!!
        assertEquals(2, postings.nextDoc())
        assertEquals(4, postings.nextDoc())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())
        assertNull(fieldTerms.next())
        IOUtils.close(reader, dir)
    }

    @Test
    fun testSortDocsAndFreqs() {
        val dir = newDirectory()
        val config = newIndexWriterConfig()
        config.setIndexSort(Sort(SortField("sort", SortField.Type.LONG)))
        val w = IndexWriter(dir, config)
        val ft = FieldType()
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        ft.setTokenized(false)
        ft.freeze()
        var doc = Document()
        doc.add(NumericDocValuesField("sort", 0L))
        doc.add(Field("field", "a", ft))
        doc.add(Field("field", "a", ft))
        w.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("sort", 1L))
        doc.add(Field("field", "b", ft))
        w.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("sort", -1L))
        doc.add(Field("field", "a", ft))
        doc.add(Field("field", "a", ft))
        doc.add(Field("field", "a", ft))
        w.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("sort", 2L))
        doc.add(Field("field", "a", ft))
        w.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("sort", 3L))
        doc.add(Field("field", "b", ft))
        doc.add(Field("field", "b", ft))
        doc.add(Field("field", "b", ft))
        w.addDocument(doc)
        w.forceMerge(1)
        val reader = DirectoryReader.open(w)
        w.close()
        val leafReader = getOnlyLeafReader(reader)
        val fieldTerms = leafReader.terms("field")!!.iterator()
        assertEquals(BytesRef("a"), fieldTerms.next())
        var postings = fieldTerms.postings(null, PostingsEnum.ALL.toInt())!!
        assertEquals(0, postings.nextDoc())
        assertEquals(3, postings.freq())
        assertEquals(1, postings.nextDoc())
        assertEquals(2, postings.freq())
        assertEquals(3, postings.nextDoc())
        assertEquals(1, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())
        assertEquals(BytesRef("b"), fieldTerms.next())
        postings = fieldTerms.postings(postings, PostingsEnum.ALL.toInt())!!
        assertEquals(2, postings.nextDoc())
        assertEquals(1, postings.freq())
        assertEquals(4, postings.nextDoc())
        assertEquals(3, postings.freq())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())
        assertNull(fieldTerms.next())
        IOUtils.close(reader, dir)
    }

    @Test
    fun testSortDocsAndFreqsAndPositions() {
        val dir = newDirectory()
        val config = newIndexWriterConfig(MockAnalyzer(random()))
        config.setIndexSort(Sort(SortField("sort", SortField.Type.LONG)))
        val w = IndexWriter(dir, config)
        val ft = FieldType()
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
        ft.setTokenized(true)
        ft.freeze()
        var doc = Document()
        doc.add(NumericDocValuesField("sort", 0L))
        doc.add(Field("field", "a a b", ft))
        w.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("sort", 1L))
        doc.add(Field("field", "b", ft))
        w.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("sort", -1L))
        doc.add(Field("field", "b a b b", ft))
        w.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("sort", 2L))
        doc.add(Field("field", "a", ft))
        w.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("sort", 3L))
        doc.add(Field("field", "b b", ft))
        w.addDocument(doc)
        w.forceMerge(1)
        val reader = DirectoryReader.open(w)
        w.close()
        val leafReader = getOnlyLeafReader(reader)
        val fieldTerms = leafReader.terms("field")!!.iterator()
        assertEquals(BytesRef("a"), fieldTerms.next())
        var postings = fieldTerms.postings(null, PostingsEnum.ALL.toInt())!!
        assertEquals(0, postings.nextDoc())
        assertEquals(1, postings.freq())
        assertEquals(1, postings.nextPosition())
        assertEquals(1, postings.nextDoc())
        assertEquals(2, postings.freq())
        assertEquals(0, postings.nextPosition())
        assertEquals(1, postings.nextPosition())
        assertEquals(3, postings.nextDoc())
        assertEquals(1, postings.freq())
        assertEquals(0, postings.nextPosition())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())
        assertEquals(BytesRef("b"), fieldTerms.next())
        postings = fieldTerms.postings(postings, PostingsEnum.ALL.toInt())!!
        assertEquals(0, postings.nextDoc())
        assertEquals(3, postings.freq())
        assertEquals(0, postings.nextPosition())
        assertEquals(2, postings.nextPosition())
        assertEquals(3, postings.nextPosition())
        assertEquals(1, postings.nextDoc())
        assertEquals(1, postings.freq())
        assertEquals(2, postings.nextPosition())
        assertEquals(2, postings.nextDoc())
        assertEquals(1, postings.freq())
        assertEquals(0, postings.nextPosition())
        assertEquals(4, postings.nextDoc())
        assertEquals(2, postings.freq())
        assertEquals(0, postings.nextPosition())
        assertEquals(1, postings.nextPosition())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())
        assertNull(fieldTerms.next())
        IOUtils.close(reader, dir)
    }

    @Test
    fun testSortDocsAndFreqsAndPositionsAndOffsets() {
        val dir = newDirectory()
        val config = newIndexWriterConfig(MockAnalyzer(random()))
        config.setIndexSort(Sort(SortField("sort", SortField.Type.LONG)))
        val w = IndexWriter(dir, config)
        val ft = FieldType()
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        ft.setTokenized(true)
        ft.freeze()
        var doc = Document()
        doc.add(NumericDocValuesField("sort", 0L))
        doc.add(Field("field", "a a b", ft))
        w.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("sort", 1L))
        doc.add(Field("field", "b", ft))
        w.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("sort", -1L))
        doc.add(Field("field", "b a b b", ft))
        w.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("sort", 2L))
        doc.add(Field("field", "a", ft))
        w.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("sort", 3L))
        doc.add(Field("field", "b b", ft))
        w.addDocument(doc)
        w.forceMerge(1)
        val reader = DirectoryReader.open(w)
        w.close()
        val leafReader = getOnlyLeafReader(reader)
        val fieldTerms = leafReader.terms("field")!!.iterator()
        assertEquals(BytesRef("a"), fieldTerms.next())
        var postings = fieldTerms.postings(null, PostingsEnum.ALL.toInt())!!
        assertEquals(0, postings.nextDoc())
        assertEquals(1, postings.freq())
        assertEquals(1, postings.nextPosition())
        assertEquals(2, postings.startOffset())
        assertEquals(3, postings.endOffset())
        assertEquals(1, postings.nextDoc())
        assertEquals(2, postings.freq())
        assertEquals(0, postings.nextPosition())
        assertEquals(0, postings.startOffset())
        assertEquals(1, postings.endOffset())
        assertEquals(1, postings.nextPosition())
        assertEquals(2, postings.startOffset())
        assertEquals(3, postings.endOffset())
        assertEquals(3, postings.nextDoc())
        assertEquals(1, postings.freq())
        assertEquals(0, postings.nextPosition())
        assertEquals(0, postings.startOffset())
        assertEquals(1, postings.endOffset())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())
        assertEquals(BytesRef("b"), fieldTerms.next())
        postings = fieldTerms.postings(postings, PostingsEnum.ALL.toInt())!!
        assertEquals(0, postings.nextDoc())
        assertEquals(3, postings.freq())
        assertEquals(0, postings.nextPosition())
        assertEquals(0, postings.startOffset())
        assertEquals(1, postings.endOffset())
        assertEquals(2, postings.nextPosition())
        assertEquals(4, postings.startOffset())
        assertEquals(5, postings.endOffset())
        assertEquals(3, postings.nextPosition())
        assertEquals(6, postings.startOffset())
        assertEquals(7, postings.endOffset())
        assertEquals(1, postings.nextDoc())
        assertEquals(1, postings.freq())
        assertEquals(2, postings.nextPosition())
        assertEquals(4, postings.startOffset())
        assertEquals(5, postings.endOffset())
        assertEquals(2, postings.nextDoc())
        assertEquals(1, postings.freq())
        assertEquals(0, postings.nextPosition())
        assertEquals(0, postings.startOffset())
        assertEquals(1, postings.endOffset())
        assertEquals(4, postings.nextDoc())
        assertEquals(2, postings.freq())
        assertEquals(0, postings.nextPosition())
        assertEquals(0, postings.startOffset())
        assertEquals(1, postings.endOffset())
        assertEquals(1, postings.nextPosition())
        assertEquals(2, postings.startOffset())
        assertEquals(3, postings.endOffset())
        assertEquals(NO_MORE_DOCS, postings.nextDoc())
        assertNull(fieldTerms.next())
        IOUtils.close(reader, dir)
    }

    @Test
    fun testParentFieldNotConfigured() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortField("foo", SortField.Type.INT))
        iwc.setIndexSort(indexSort)
        val writer = IndexWriter(dir, iwc)
        val ex = expectThrows(IllegalArgumentException::class) {
            writer.addDocuments(listOf(Document(), Document()))
        }
        assertEquals(
            "a parent field must be set in order to use document blocks with index sorting; see IndexWriterConfig#setParentField",
            ex.message
        )
        IOUtils.close(writer, dir)
    }

    @Test
    fun testBlockContainsParentField() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val parentField = "parent"
        iwc.setParentField(parentField)
        val indexSort = Sort(SortField("foo", SortField.Type.INT))
        iwc.setIndexSort(indexSort)
        val writer = IndexWriter(dir, iwc)
        val runnabels =
            mutableListOf<() -> Unit>(
                {
                    val ex = expectThrows(IllegalArgumentException::class) {
                        val doc = Document()
                        doc.add(NumericDocValuesField("parent", 0))
                        writer.addDocuments(listOf(doc, Document()))
                    }
                    assertEquals(
                        "\"parent\" is a reserved field and should not be added to any document",
                        ex.message
                    )
                },
                {
                    val ex = expectThrows(IllegalArgumentException::class) {
                        val doc = Document()
                        doc.add(NumericDocValuesField("parent", 0))
                        writer.addDocuments(listOf(Document(), doc))
                    }
                    assertEquals(
                        "\"parent\" is a reserved field and should not be added to any document",
                        ex.message
                    )
                }
            )
        runnabels.shuffle(random())
        for (runnable in runnabels) {
            runnable()
        }
        IOUtils.close(writer, dir)
    }

    @Test
    fun testIndexSortWithBlocks() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val codec = AssertingNeedsIndexSortCodec()
        iwc.setCodec(codec)
        val parentField = "parent"
        val indexSort = Sort(SortField("foo", SortField.Type.INT))
        iwc.setIndexSort(indexSort)
        iwc.setParentField(parentField)
        val policy = newLogMergePolicy()
        // make sure that merge factor is always > 2
        if (policy.mergeFactor <= 2) {
            policy.mergeFactor = 3
        }
        iwc.setMergePolicy(policy)

        // add already sorted documents
        codec.numCalls = 0
        codec.needsIndexSort = false
        val w = IndexWriter(dir, iwc)
        val numDocs = random().nextInt(50, 100)
        for (i in 0..<numDocs) {
            val child1 = Document()
            child1.add(StringField("id", i.toString(), Field.Store.YES))
            child1.add(NumericDocValuesField("id", i.toLong()))
            child1.add(NumericDocValuesField("child", 1))
            child1.add(NumericDocValuesField("foo", random().nextInt().toLong()))
            val child2 = Document()
            child2.add(StringField("id", i.toString(), Field.Store.YES))
            child2.add(NumericDocValuesField("id", i.toLong()))
            child2.add(NumericDocValuesField("child", 2))
            child2.add(NumericDocValuesField("foo", random().nextInt().toLong()))
            val parent = Document()
            parent.add(StringField("id", i.toString(), Field.Store.YES))
            parent.add(NumericDocValuesField("id", i.toLong()))
            parent.add(NumericDocValuesField("foo", random().nextInt().toLong()))
            w.addDocuments(listOf(child1, child2, parent))
            if (rarely()) {
                w.commit()
            }
        }
        w.commit()
        if (random().nextBoolean()) {
            w.forceMerge(1, true)
        }
        w.close()

        val reader = DirectoryReader.open(dir)
        for (ctx in reader.leaves()) {
            val leaf = ctx.reader()
            val parentDISI = assertNotNull(leaf.getNumericDocValues(parentField))
            val ids = assertNotNull(leaf.getNumericDocValues("id"))
            val children = assertNotNull(leaf.getNumericDocValues("child"))
            var expectedDocID = 2
            while (true) {
                val doc = parentDISI.nextDoc()
                if (doc == NO_MORE_DOCS) {
                    break
                }
                assertEquals(-1L, parentDISI.longValue())
                assertEquals(expectedDocID, doc)
                var id = ids.nextDoc()
                val child1ID = ids.longValue()
                assertEquals(id, children.nextDoc())
                val child1 = children.longValue()
                assertEquals(1L, child1)

                id = ids.nextDoc()
                val child2ID = ids.longValue()
                assertEquals(id, children.nextDoc())
                val child2 = children.longValue()
                assertEquals(2L, child2)

                val idParent = ids.nextDoc()
                assertEquals(id + 1, idParent)
                val parent = ids.longValue()
                assertEquals(child1ID, parent)
                assertEquals(child2ID, parent)
                expectedDocID += 3
            }
        }
        IOUtils.close(reader, dir)
    }

    @Test
    fun testMixRandomDocumentsWithBlocks() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val codec = AssertingNeedsIndexSortCodec()
        iwc.setCodec(codec)
        val parentField = "parent"
        val indexSort = Sort(SortField("foo", SortField.Type.INT))
        iwc.setIndexSort(indexSort)
        iwc.setParentField(parentField)
        val randomIndexWriter = RandomIndexWriter(random(), dir, iwc)
        val numDocs = random().nextInt(10, 100) // TODO reduced from 100, 1000 to 10, 100 for dev speed
        for (i in 0..<numDocs) {
            if (rarely()) {
                randomIndexWriter.deleteDocuments(Term("id", random().nextInt(0, i + 1).toString()))
            }
            val docs = mutableListOf<Document>()
            when (random().nextInt(100) % 5) {
                4 -> {
                    val child3 = Document()
                    child3.add(StringField("id", i.toString(), Field.Store.YES))
                    child3.add(NumericDocValuesField("type", 2))
                    child3.add(NumericDocValuesField("child_ord", 3))
                    child3.add(NumericDocValuesField("foo", random().nextInt().toLong()))
                    docs.add(child3)
                    val child2 = Document()
                    child2.add(StringField("id", i.toString(), Field.Store.YES))
                    child2.add(NumericDocValuesField("type", 2))
                    child2.add(NumericDocValuesField("child_ord", 2))
                    child2.add(NumericDocValuesField("foo", random().nextInt().toLong()))
                    docs.add(child2)
                    val child1 = Document()
                    child1.add(StringField("id", i.toString(), Field.Store.YES))
                    child1.add(NumericDocValuesField("type", 2))
                    child1.add(NumericDocValuesField("child_ord", 1))
                    child1.add(NumericDocValuesField("foo", random().nextInt().toLong()))
                    docs.add(child1)
                    val root = Document()
                    root.add(StringField("id", i.toString(), Field.Store.YES))
                    root.add(NumericDocValuesField("type", 1))
                    root.add(NumericDocValuesField("num_children", docs.size.toLong()))
                    root.add(NumericDocValuesField("foo", random().nextInt().toLong()))
                    docs.add(root)
                    randomIndexWriter.addDocuments(docs)
                }
                3 -> {
                    val child2 = Document()
                    child2.add(StringField("id", i.toString(), Field.Store.YES))
                    child2.add(NumericDocValuesField("type", 2))
                    child2.add(NumericDocValuesField("child_ord", 2))
                    child2.add(NumericDocValuesField("foo", random().nextInt().toLong()))
                    docs.add(child2)
                    val child1 = Document()
                    child1.add(StringField("id", i.toString(), Field.Store.YES))
                    child1.add(NumericDocValuesField("type", 2))
                    child1.add(NumericDocValuesField("child_ord", 1))
                    child1.add(NumericDocValuesField("foo", random().nextInt().toLong()))
                    docs.add(child1)
                    val root = Document()
                    root.add(StringField("id", i.toString(), Field.Store.YES))
                    root.add(NumericDocValuesField("type", 1))
                    root.add(NumericDocValuesField("num_children", docs.size.toLong()))
                    root.add(NumericDocValuesField("foo", random().nextInt().toLong()))
                    docs.add(root)
                    randomIndexWriter.addDocuments(docs)
                }
                2 -> {
                    val child1 = Document()
                    child1.add(StringField("id", i.toString(), Field.Store.YES))
                    child1.add(NumericDocValuesField("type", 2))
                    child1.add(NumericDocValuesField("child_ord", 1))
                    child1.add(NumericDocValuesField("foo", random().nextInt().toLong()))
                    docs.add(child1)
                    val root = Document()
                    root.add(StringField("id", i.toString(), Field.Store.YES))
                    root.add(NumericDocValuesField("type", 1))
                    root.add(NumericDocValuesField("num_children", docs.size.toLong()))
                    root.add(NumericDocValuesField("foo", random().nextInt().toLong()))
                    docs.add(root)
                    randomIndexWriter.addDocuments(docs)
                }
                1 -> {
                    val root = Document()
                    root.add(StringField("id", i.toString(), Field.Store.YES))
                    root.add(NumericDocValuesField("type", 1))
                    root.add(NumericDocValuesField("num_children", docs.size.toLong()))
                    root.add(NumericDocValuesField("foo", random().nextInt().toLong()))
                    docs.add(root)
                    randomIndexWriter.addDocuments(docs)
                }
                0 -> {
                    val single = Document()
                    single.add(StringField("id", i.toString(), Field.Store.YES))
                    single.add(NumericDocValuesField("type", 0))
                    single.add(NumericDocValuesField("foo", random().nextInt().toLong()))
                    randomIndexWriter.addDocument(single)
                }
            }
            if (rarely()) {
                randomIndexWriter.forceMerge(1)
            }
            randomIndexWriter.commit()
        }

        randomIndexWriter.close()
        val reader = DirectoryReader.open(dir)
        for (ctx in reader.leaves()) {
            val leaf = ctx.reader()
            val parentDISI = assertNotNull(leaf.getNumericDocValues(parentField))
            val type = assertNotNull(leaf.getNumericDocValues("type"))
            val childOrd = leaf.getNumericDocValues("child_ord")
            val numChildren = leaf.getNumericDocValues("num_children")
            var numCurrentChildren = 0
            var totalPendingChildren = 0
            var childId: String? = null
            val liveDocs = leaf.liveDocs
            for (i in 0..<leaf.maxDoc()) {
                if (liveDocs == null || liveDocs.get(i)) {
                    assertTrue(type.advanceExact(i))
                    val typeValue = type.longValue().toInt()
                    when (typeValue) {
                        2 -> {
                            assertFalse(parentDISI.advanceExact(i))
                            val currentChildOrd = assertNotNull(childOrd)
                            assertTrue(currentChildOrd.advanceExact(i))
                            if (numCurrentChildren == 0) { // first child
                                childId = leaf.storedFields().document(i).get("id")
                                totalPendingChildren = currentChildOrd.longValue().toInt() - 1
                            } else {
                                assertNotNull(childId)
                                assertEquals(totalPendingChildren--.toLong(), currentChildOrd.longValue())
                                assertEquals(childId, leaf.storedFields().document(i).get("id"))
                            }
                            numCurrentChildren++
                        }
                        1 -> {
                            assertTrue(parentDISI.advanceExact(i))
                            assertEquals(-1L, parentDISI.longValue())
                            if (childOrd != null) {
                                assertFalse(childOrd.advanceExact(i))
                            }
                            val currentNumChildren = assertNotNull(numChildren)
                            assertTrue(currentNumChildren.advanceExact(i))
                            assertEquals(0, totalPendingChildren)
                            assertEquals(numCurrentChildren.toLong(), currentNumChildren.longValue())
                            if (numCurrentChildren > 0) {
                                assertEquals(childId, leaf.storedFields().document(i).get("id"))
                            } else {
                                assertNull(childId)
                            }
                            numCurrentChildren = 0
                            childId = null
                        }
                        0 -> {
                            assertEquals(-1L, parentDISI.longValue())
                            assertTrue(parentDISI.advanceExact(i))
                            if (childOrd != null) {
                                assertFalse(childOrd.advanceExact(i))
                            }
                            if (numChildren != null) {
                                assertFalse(numChildren.advanceExact(i))
                            }
                        }
                        else -> fail()
                    }
                }
            }
        }
        IOUtils.close(reader, dir)
    }
}
