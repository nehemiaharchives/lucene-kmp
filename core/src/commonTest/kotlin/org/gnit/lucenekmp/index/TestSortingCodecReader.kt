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
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.HnswGraphProvider
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.SortedNumericSortField
import org.gnit.lucenekmp.search.SortedSetSelector
import org.gnit.lucenekmp.search.SortedSetSortField
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.RandomStrings
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.hnsw.HnswGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TestSortingCodecReader : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testSortOnAddIndicesOrd() {
        val tmpDir = newDirectory()
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(tmpDir, iwc)

        val doc = Document()
        doc.add(SortedSetDocValuesField("foo", BytesRef("b")))
        w.addDocument(doc)

        doc.add(SortedSetDocValuesField("foo", BytesRef("a")))
        doc.add(SortedSetDocValuesField("foo", BytesRef("b")))
        doc.add(SortedSetDocValuesField("foo", BytesRef("b")))
        w.addDocument(doc)

        w.commit()

        val indexSort = Sort(SortedSetSortField("foo", false, SortedSetSelector.Type.MIN))
        val reader = DirectoryReader.open(tmpDir)
        try {
            for (ctx in reader.leaves()) {
                val wrap = SortingCodecReader.wrap(SlowCodecReaderWrapper.wrap(ctx.reader()), indexSort)
                assertTrue(wrap.toString().startsWith("SortingCodecReader("), wrap.toString())
                val sortingCodecReader = wrap as SortingCodecReader
                val sortedSetDocValues =
                    sortingCodecReader.docValuesReader!!
                        .getSortedSet(ctx.reader().fieldInfos.fieldInfo("foo")!!)!!
                sortedSetDocValues.nextDoc()
                assertEquals(2, sortedSetDocValues.docValueCount())
                sortedSetDocValues.nextDoc()
                assertEquals(1, sortedSetDocValues.docValueCount())
                assertEquals(NO_MORE_DOCS, sortedSetDocValues.nextDoc())
            }
        } finally {
            reader.close()
        }
        IOUtils.close(w, dir, tmpDir)
    }

    @Test
    @Throws(IOException::class)
    fun testSortOnAddIndicesInt() {
        val tmpDir = newDirectory()
        val dir = newDirectory()
        var iwc = IndexWriterConfig(MockAnalyzer(random()))
        var w = IndexWriter(tmpDir, iwc)
        var doc = Document()
        doc.add(NumericDocValuesField("foo", 18))
        w.addDocument(doc)

        doc = Document()
        doc.add(NumericDocValuesField("foo", -1))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(NumericDocValuesField("foo", 7))
        w.addDocument(doc)
        w.commit()
        w.close()
        val indexSort = Sort(SortField("foo", SortField.Type.INT))

        iwc = IndexWriterConfig(MockAnalyzer(random())).setIndexSort(indexSort)
        w = IndexWriter(dir, iwc)
        val reader = DirectoryReader.open(tmpDir)
        try {
            val readers = mutableListOf<CodecReader>()
            for (ctx in reader.leaves()) {
                val wrap = SortingCodecReader.wrap(SlowCodecReaderWrapper.wrap(ctx.reader()), indexSort)
                assertTrue(wrap.toString().startsWith("SortingCodecReader("), wrap.toString())
                readers.add(wrap)
            }
            w.addIndexes(*readers.toTypedArray())
        } finally {
            reader.close()
        }
        val r = DirectoryReader.open(w)
        val leaf = getOnlyLeafReader(r)
        assertEquals(3, leaf.maxDoc())
        val values = leaf.getNumericDocValues("foo")!!
        assertEquals(0, values.nextDoc())
        assertEquals(-1, values.longValue())
        assertEquals(1, values.nextDoc())
        assertEquals(7, values.longValue())
        assertEquals(2, values.nextDoc())
        assertEquals(18, values.longValue())
        assertNotNull(leaf.metaData.sort)
        IOUtils.close(r, w, dir, tmpDir)
    }

    @Test
    @Throws(IOException::class)
    fun testSortOnAddIndicesRandom() {
        val dir = newDirectory()
        try {
            val numDocs = atLeast(200)
            var actualNumDocs: Int
            val docIds = MutableList(numDocs) { it }
            docIds.shuffle(random())
            // If true, index a vector and points for every doc
            val dense = random().nextBoolean()
            val iw = RandomIndexWriter(random(), dir)
            try {
                for (i in 0 until numDocs) {
                    val docId = docIds[i]
                    val doc = Document()
                    doc.add(StringField("string_id", docId.toString(), Field.Store.YES))
                    if (dense || docId % 3 == 0) {
                        doc.add(LongPoint("point_id", docId.toLong()))
                    }
                    val s = RandomStrings.randomRealisticUnicodeOfLength(random(), 25)
                    doc.add(TextField("text_field", s, Field.Store.YES))
                    doc.add(BinaryDocValuesField("text_field", BytesRef(s)))
                    doc.add(TextField("another_text_field", s, Field.Store.YES))
                    doc.add(BinaryDocValuesField("another_text_field", BytesRef(s)))
                    doc.add(SortedNumericDocValuesField("sorted_numeric_dv", docId.toLong()))
                    doc.add(SortedDocValuesField("binary_sorted_dv", BytesRef(docId.toString())))
                    doc.add(BinaryDocValuesField("binary_dv", BytesRef(docId.toString())))
                    doc.add(SortedSetDocValuesField("sorted_set_dv", BytesRef(docId.toString())))
                    if (dense || docId % 2 == 0) {
                        doc.add(KnnFloatVectorField("vector", floatArrayOf(docId.toFloat())))
                    }
                    doc.add(NumericDocValuesField("foo", random().nextInt(20).toLong()))

                    val ft = FieldType(StringField.TYPE_NOT_STORED)
                    ft.setStoreTermVectors(true)
                    doc.add(Field("term_vectors", "test$docId", ft))
                    if (rarely() == false) {
                        doc.add(NumericDocValuesField("id", docId.toLong()))
                        doc.add(
                            SortedSetDocValuesField(
                                "sorted_set_sort_field",
                                BytesRef(docId.toString().padStart(6, '0'))
                            )
                        )
                        doc.add(
                            SortedDocValuesField(
                                "sorted_binary_sort_field",
                                BytesRef(docId.toString().padStart(6, '0'))
                            )
                        )
                        doc.add(SortedNumericDocValuesField("sorted_numeric_sort_field", docId.toLong()))
                    } else {
                        doc.add(NumericDocValuesField("alt_id", docId.toLong()))
                    }
                    iw.addDocument(doc)
                    if (i > 0 && random().nextInt(5) == 0) {
                        val id = RandomPicks.randomFrom(random(), docIds.subList(0, i))
                        iw.deleteDocuments(Term("string_id", id.toString()))
                    }
                }
                iw.commit()
                actualNumDocs = iw.w.getDocStats().numDocs
            } finally {
                iw.close()
            }
            val indexSort =
                RandomPicks.randomFrom(
                    random(),
                    mutableListOf(
                        Sort(
                            SortField("id", SortField.Type.INT),
                            SortField("alt_id", SortField.Type.INT)
                        ),
                        Sort(
                            SortedSetSortField("sorted_set_sort_field", false),
                            SortField("alt_id", SortField.Type.INT)
                        ),
                        Sort(
                            SortedNumericSortField("sorted_numeric_sort_field", SortField.Type.INT),
                            SortField("alt_id", SortField.Type.INT)
                        ),
                        Sort(
                            SortField("sorted_binary_sort_field", SortField.Type.STRING, false),
                            SortField("alt_id", SortField.Type.INT)
                        )
                    )
                )
            val sortDir = newDirectory()
            try {
                val writer = IndexWriter(sortDir, newIndexWriterConfig().setIndexSort(indexSort))
                try {
                    val reader = DirectoryReader.open(dir)
                    try {
                        val readers = mutableListOf<CodecReader>()
                        for (ctx in reader.leaves()) {
                            val wrap = SortingCodecReader.wrap(SlowCodecReaderWrapper.wrap(ctx.reader()), indexSort)
                            readers.add(wrap)
                            val termVectorsReader = wrap.termVectorsReader!!
                            val clone: TermVectorsReader = termVectorsReader.clone()
                            assertNotSame(termVectorsReader, clone)
                            clone.close()
                        }
                        writer.addIndexes(*readers.toTypedArray())
                    } finally {
                        reader.close()
                    }
                    assumeTrue("must have at least one doc", actualNumDocs > 0)
                    val r = DirectoryReader.open(writer)
                    try {
                        val leaf = getOnlyLeafReader(r)
                        assertEquals(actualNumDocs, leaf.maxDoc())
                        var binary_dv = leaf.getBinaryDocValues("binary_dv")!!
                        var sorted_numeric_dv = leaf.getSortedNumericDocValues("sorted_numeric_dv")!!
                        var sorted_set_dv = leaf.getSortedSetDocValues("sorted_set_dv")!!
                        var binary_sorted_dv = leaf.getSortedDocValues("binary_sorted_dv")!!
                        var vectorValues = leaf.getFloatVectorValues("vector")
                        val vectorsReader: KnnVectorsReader = (leaf as CodecReader).vectorReader!!
                        val graph: HnswGraph? =
                            if (vectorsReader is HnswGraphProvider) {
                                vectorsReader.getGraph("vector")
                            } else {
                                null
                            }
                        var ids = leaf.getNumericDocValues("id")!!
                        var prevValue = -1L
                        var usingAltIds = false
                        var valuesIterator = vectorValues?.iterator()
                        for (i in 0 until actualNumDocs) {
                            var idNext = ids.nextDoc()
                            if (idNext == NO_MORE_DOCS) {
                                assertFalse(usingAltIds)
                                usingAltIds = true
                                ids = leaf.getNumericDocValues("alt_id")!!
                                idNext = ids.nextDoc()
                                binary_dv = leaf.getBinaryDocValues("binary_dv")!!
                                sorted_numeric_dv = leaf.getSortedNumericDocValues("sorted_numeric_dv")!!
                                sorted_set_dv = leaf.getSortedSetDocValues("sorted_set_dv")!!
                                binary_sorted_dv = leaf.getSortedDocValues("binary_sorted_dv")!!
                                vectorValues = leaf.getFloatVectorValues("vector")
                                valuesIterator = vectorValues?.iterator()
                                prevValue = -1
                            }
                            assertTrue(prevValue < ids.longValue(), "$prevValue < ${ids.longValue()}")
                            prevValue = ids.longValue()
                            assertTrue(binary_dv.advanceExact(idNext))
                            assertTrue(sorted_numeric_dv.advanceExact(idNext))
                            assertTrue(sorted_set_dv.advanceExact(idNext))
                            assertTrue(binary_sorted_dv.advanceExact(idNext))
                            if (dense || prevValue % 2L == 0L) {
                                assertEquals(idNext, valuesIterator!!.advance(idNext))
                                if (graph != null) {
                                    graph.seek(0, valuesIterator.index())
                                    assertTrue(graph.nextNeighbor() != NO_MORE_DOCS)
                                }
                            }

                            assertEquals(BytesRef("${ids.longValue()}"), binary_dv.binaryValue())
                            assertEquals(
                                BytesRef("${ids.longValue()}"),
                                binary_sorted_dv.lookupOrd(binary_sorted_dv.ordValue())
                            )
                            assertEquals(
                                BytesRef("${ids.longValue()}"),
                                sorted_set_dv.lookupOrd(sorted_set_dv.nextOrd())
                            )
                            assertEquals(1, sorted_set_dv.docValueCount())
                            assertEquals(1, sorted_numeric_dv.docValueCount())
                            assertEquals(ids.longValue(), sorted_numeric_dv.nextValue())

                            if (dense || prevValue % 2L == 0L) {
                                val vectorValue = vectorValues!!.vectorValue(valuesIterator!!.index())
                                assertEquals(1, vectorValue.size)
                                assertEquals(ids.longValue().toFloat(), vectorValue[0], 0.001f)
                            }

                            val termVectors = leaf.termVectors().get(idNext)!!
                            assertTrue(
                                termVectors.terms("term_vectors")!!
                                    .iterator()
                                    .seekExact(BytesRef("test${ids.longValue()}"))
                            )
                            assertEquals(ids.longValue().toString(), leaf.storedFields().document(idNext).get("string_id"))
                            val searcher = IndexSearcher(r)
                            var result: TopDocs =
                                searcher.search(LongPoint.newExactQuery("point_id", ids.longValue()), 10)
                            if (dense || ids.longValue() % 3L == 0L) {
                                assertEquals(1L, result.totalHits.value)
                                assertEquals(idNext, result.scoreDocs[0].doc)
                            } else {
                                assertEquals(0L, result.totalHits.value)
                            }

                            result = searcher.search(TermQuery(Term("string_id", "${ids.longValue()}")), 1)
                            assertEquals(1L, result.totalHits.value)
                            assertEquals(idNext, result.scoreDocs[0].doc)
                        }
                        assertEquals(NO_MORE_DOCS, ids.nextDoc())
                    } finally {
                        r.close()
                    }
                } finally {
                    writer.close()
                }
            } finally {
                sortDir.close()
            }
        } finally {
            dir.close()
        }
    }
}

