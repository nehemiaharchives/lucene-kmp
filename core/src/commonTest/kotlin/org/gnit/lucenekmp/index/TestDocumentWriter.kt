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
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.InvertableType
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StoredValue
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.DocHelper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.Version
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestDocumentWriter : LuceneTestCase() {
    private lateinit var dir: Directory

    @BeforeTest
    fun setUp() {
        dir = newDirectory()
    }

    @AfterTest
    fun tearDown() {
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAddDocument() {
        val testDoc = Document()
        DocHelper.setupDoc(testDoc)
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        writer.addDocument(testDoc)
        writer.commit()
        val info = writer.newestSegment()!!
        writer.close()
        val reader = SegmentReader(info, Version.LATEST.major, newIOContext(random()))
        val doc = reader.storedFields().document(0)

        var fields = doc.getFields("textField2")
        assertEquals(1, fields.size)
        assertEquals(DocHelper.FIELD_2_TEXT, fields[0].stringValue())
        assertTrue(fields[0].fieldType().storeTermVectors())

        fields = doc.getFields("textField1")
        assertEquals(1, fields.size)
        assertEquals(DocHelper.FIELD_1_TEXT, fields[0].stringValue())
        assertFalse(fields[0].fieldType().storeTermVectors())

        fields = doc.getFields("keyField")
        assertEquals(1, fields.size)
        assertEquals(DocHelper.KEYWORD_TEXT, fields[0].stringValue())

        fields = doc.getFields(DocHelper.NO_NORMS_KEY)
        assertEquals(1, fields.size)
        assertEquals(DocHelper.NO_NORMS_TEXT, fields[0].stringValue())

        fields = doc.getFields(DocHelper.TEXT_FIELD_3_KEY)
        assertEquals(1, fields.size)
        assertEquals(DocHelper.FIELD_3_TEXT, fields[0].stringValue())

        for (fi in reader.fieldInfos) {
            if (fi.indexOptions != IndexOptions.NONE) {
                assertEquals(fi.omitsNorms(), reader.getNormValues(fi.name) == null)
            }
        }
        reader.close()
    }

    @Test
    @Throws(IOException::class)
    fun testPositionIncrementGap() {
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    return TokenStreamComponents(MockTokenizer(MockTokenizer.WHITESPACE, false))
                }

                override fun getPositionIncrementGap(fieldName: String?): Int {
                    return 500
                }
            }

        val writer = IndexWriter(dir, newIndexWriterConfig(analyzer))

        val doc = Document()
        doc.add(newTextField("repeated", "repeated one", Store.YES))
        doc.add(newTextField("repeated", "repeated two", Store.YES))

        writer.addDocument(doc)
        writer.commit()
        val info = writer.newestSegment()!!
        writer.close()
        val reader = SegmentReader(info, Version.LATEST.major, newIOContext(random()))

        val termPositions = MultiTerms.getTermPostingsEnum(reader, "repeated", BytesRef("repeated"))!!
        assertTrue(termPositions.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        val freq = termPositions.freq()
        assertEquals(2, freq)
        assertEquals(0, termPositions.nextPosition())
        assertEquals(502, termPositions.nextPosition())
        reader.close()
    }

    @Test
    @Throws(IOException::class)
    fun testTokenReuse() {
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(
                        tokenizer,
                        object : TokenFilter(tokenizer) {
                            var first = true
                            var state: AttributeSource.State? = null

                            val termAtt = addAttribute(CharTermAttribute::class)
                            val payloadAtt = addAttribute(PayloadAttribute::class)
                            val posIncrAtt = addAttribute(PositionIncrementAttribute::class)

                            override fun incrementToken(): Boolean {
                                if (state != null) {
                                    restoreState(state!!)
                                    payloadAtt.payload = null
                                    posIncrAtt.setPositionIncrement(0)
                                    termAtt.setEmpty()!!.append("b")
                                    state = null
                                    return true
                                }

                                val hasNext = input.incrementToken()
                                if (!hasNext) return false
                                if (termAtt.buffer()[0].isDigit()) {
                                    posIncrAtt.setPositionIncrement(termAtt.buffer()[0].digitToInt())
                                }
                                if (first) {
                                    payloadAtt.payload = BytesRef(byteArrayOf(100))
                                    first = false
                                }

                                state = captureState()
                                return true
                            }

                            override fun reset() {
                                super.reset()
                                first = true
                                state = null
                            }
                        },
                    )
                }
            }

        val writer = IndexWriter(dir, newIndexWriterConfig(analyzer))

        val doc = Document()
        doc.add(newTextField("f1", "a 5 a a", Store.YES))

        writer.addDocument(doc)
        writer.commit()
        val info = writer.newestSegment()!!
        writer.close()
        val reader = SegmentReader(info, Version.LATEST.major, newIOContext(random()))

        val termPositions = MultiTerms.getTermPostingsEnum(reader, "f1", BytesRef("a"))!!
        assertTrue(termPositions.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        val freq = termPositions.freq()
        assertEquals(3, freq)
        assertEquals(0, termPositions.nextPosition())
        assertNotNull(termPositions.payload)
        assertEquals(6, termPositions.nextPosition())
        assertNull(termPositions.payload)
        assertEquals(7, termPositions.nextPosition())
        assertNull(termPositions.payload)
        reader.close()
    }

    @Test
    @Throws(IOException::class)
    fun testPreAnalyzedField() {
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()

        doc.add(
            TextField(
                "preanalyzed",
                object : TokenStream() {
                    private val tokens = arrayOf("term1", "term2", "term3", "term2")
                    private var index = 0

                    private val termAtt = addAttribute(CharTermAttribute::class)

                    override fun incrementToken(): Boolean {
                        if (index == tokens.size) {
                            return false
                        } else {
                            clearAttributes()
                            termAtt.setEmpty()!!.append(tokens[index++])
                            return true
                        }
                    }
                },
            )
        )

        writer.addDocument(doc)
        writer.commit()
        val info = writer.newestSegment()!!
        writer.close()
        val reader = SegmentReader(info, Version.LATEST.major, newIOContext(random()))

        var termPositions = reader.postings(Term("preanalyzed", "term1"), PostingsEnum.ALL.toInt())!!
        assertTrue(termPositions.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(1, termPositions.freq())
        assertEquals(0, termPositions.nextPosition())

        termPositions = reader.postings(Term("preanalyzed", "term2"), PostingsEnum.ALL.toInt())!!
        assertTrue(termPositions.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(2, termPositions.freq())
        assertEquals(1, termPositions.nextPosition())
        assertEquals(3, termPositions.nextPosition())

        termPositions = reader.postings(Term("preanalyzed", "term3"), PostingsEnum.ALL.toInt())!!
        assertTrue(termPositions.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(1, termPositions.freq())
        assertEquals(2, termPositions.nextPosition())
        reader.close()
    }

    /**
     * Test adding two fields with the same name, one indexed the other stored only. The omitNorms and
     * omitTermFreqAndPositions setting of the stored field should not affect the indexed one
     * (LUCENE-1590)
     */
    @Test
    @Throws(Exception::class)
    fun testLUCENE_1590() {
        val doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setOmitNorms(true)
        val customType2 = FieldType()
        customType2.setStored(true)
        doc.add(newField("f1", "v1", customType))
        doc.add(newField("f1", "v2", customType2))
        val customType3 = FieldType(TextField.TYPE_NOT_STORED)
        customType3.setIndexOptions(IndexOptions.DOCS)
        val f = newField("f2", "v1", customType3)
        doc.add(f)
        doc.add(newField("f2", "v2", customType2))

        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        writer.addDocument(doc)
        writer.forceMerge(1)
        writer.close()

        TestUtil.checkIndex(dir)

        val reader = getOnlyLeafReader(DirectoryReader.open(dir))
        val fi = reader.fieldInfos
        assertFalse(fi.fieldInfo("f1")!!.hasNorms(), "f1 should have no norms")
        assertEquals(
            IndexOptions.DOCS_AND_FREQS_AND_POSITIONS,
            fi.fieldInfo("f1")!!.indexOptions,
            "omitTermFreqAndPositions field bit should not be set for f1",
        )
        assertTrue(fi.fieldInfo("f2")!!.hasNorms(), "f2 should have norms")
        assertEquals(
            IndexOptions.DOCS,
            fi.fieldInfo("f2")!!.indexOptions,
            "omitTermFreqAndPositions field bit should be set for f2",
        )
        reader.close()
    }

    /** Make sure that every new field doesn't increment memory usage by more than 16kB */
    @Throws(IOException::class)
    private fun doTestRAMUsage(fieldSupplier: (String) -> IndexableField) {
        newDirectory().use { dir ->
            IndexWriter(
                dir,
                newIndexWriterConfig()
                    .setMaxBufferedDocs(10)
                    .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble()),
            ).use { w ->
                val doc = Document()
                val numFields = 100
                for (i in 0..<numFields) {
                    doc.add(fieldSupplier("f$i"))
                }
                w.addDocument(doc)
                assertTrue(w.hasChangesInRam())
                assertTrue(w.ramBytesUsed() < numFields * 16384L)
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testRAMUsageStored() {
        doTestRAMUsage { field -> StoredField(field, BytesRef("Lucene")) }
    }

    @Test
    @Throws(IOException::class)
    fun testRAMUsageIndexed() {
        doTestRAMUsage { field -> StringField(field, BytesRef("Lucene"), Store.NO) }
    }

    @Test
    @Throws(IOException::class)
    fun testRAMUsagePoint() {
        doTestRAMUsage { field -> IntPoint(field, 42) }
    }

    @Test
    @Throws(IOException::class)
    fun testRAMUsageNumericDocValue() {
        doTestRAMUsage { field -> NumericDocValuesField(field, 42) }
    }

    @Test
    @Throws(IOException::class)
    fun testRAMUsageSortedDocValue() {
        doTestRAMUsage { field -> SortedDocValuesField(field, BytesRef("Lucene")) }
    }

    @Test
    @Throws(IOException::class)
    fun testRAMUsageBinaryDocValue() {
        doTestRAMUsage { field -> BinaryDocValuesField(field, BytesRef("Lucene")) }
    }

    @Test
    @Throws(IOException::class)
    fun testRAMUsageSortedNumericDocValue() {
        doTestRAMUsage { field -> SortedNumericDocValuesField(field, 42) }
    }

    @Test
    @Throws(IOException::class)
    fun testRAMUsageSortedSetDocValue() {
        doTestRAMUsage { field -> SortedSetDocValuesField(field, BytesRef("Lucene")) }
    }

    @Test
    @Throws(IOException::class)
    fun testRAMUsageVector() {
        doTestRAMUsage { field ->
            KnnFloatVectorField(field, floatArrayOf(1f, 2f, 3f, 4f), VectorSimilarityFunction.EUCLIDEAN)
        }
    }

    private data class MockIndexableField(
        val field: String,
        val value: BytesRef?,
        val fieldTypeValue: IndexableFieldType,
    ) : IndexableField {
        override fun name(): String {
            return field
        }

        override fun fieldType(): IndexableFieldType {
            return fieldTypeValue
        }

        override fun tokenStream(analyzer: Analyzer, reuse: TokenStream?): TokenStream? {
            return null
        }

        override fun binaryValue(): BytesRef? {
            return value
        }

        override fun stringValue(): String? {
            return null
        }

        override fun readerValue(): Reader? {
            return null
        }

        override fun numericValue(): Number? {
            return null
        }

        override fun storedValue(): StoredValue? {
            return null
        }

        override fun invertableType(): InvertableType {
            return InvertableType.BINARY
        }
    }

    @Test
    @Throws(IOException::class)
    fun testIndexBinaryValueWithoutTokenStream() {
        val illegalFieldTypes = mutableListOf<FieldType>()
        run {
            val illegalFT = FieldType()
            illegalFT.setTokenized(true)
            illegalFT.setIndexOptions(IndexOptions.DOCS)
            illegalFT.freeze()
            illegalFieldTypes.add(illegalFT)
        }
        run {
            val illegalFT = FieldType()
            illegalFT.setTokenized(false)
            illegalFT.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
            illegalFT.freeze()
            illegalFieldTypes.add(illegalFT)
        }
        run {
            val illegalFT = FieldType()
            illegalFT.setTokenized(false)
            illegalFT.setIndexOptions(IndexOptions.DOCS)
            illegalFT.setStoreTermVectors(true)
            illegalFT.setStoreTermVectorPositions(true)
            illegalFT.freeze()
            illegalFieldTypes.add(illegalFT)
        }
        run {
            val illegalFT = FieldType()
            illegalFT.setTokenized(false)
            illegalFT.setIndexOptions(IndexOptions.DOCS)
            illegalFT.setStoreTermVectors(true)
            illegalFT.setStoreTermVectorOffsets(true)
            illegalFT.freeze()
            illegalFieldTypes.add(illegalFT)
        }

        for (ft in illegalFieldTypes) {
            IndexWriter(dir, newIndexWriterConfig().setOpenMode(OpenMode.CREATE)).use { w ->
                val field = MockIndexableField("field", BytesRef("a"), ft)
                val doc = Document()
                doc.add(field)
                expectThrows(IllegalArgumentException::class) {
                    w.addDocument(doc)
                }
            }
        }

        IndexWriter(dir, newIndexWriterConfig().setOpenMode(OpenMode.CREATE)).use { w ->
            val field = MockIndexableField("field", null, StringField.TYPE_NOT_STORED)
            val doc = Document()
            doc.add(field)
            expectThrows(IllegalArgumentException::class) {
                w.addDocument(doc)
            }
        }

        val legalFieldTypes = mutableListOf<FieldType>()
        run {
            val ft = FieldType()
            ft.setTokenized(false)
            ft.setIndexOptions(IndexOptions.DOCS)
            ft.setOmitNorms(false)
            ft.freeze()
            legalFieldTypes.add(ft)
        }
        run {
            val ft = FieldType()
            ft.setTokenized(false)
            ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
            ft.setOmitNorms(false)
            ft.freeze()
            legalFieldTypes.add(ft)
        }
        run {
            val ft = FieldType()
            ft.setTokenized(false)
            ft.setIndexOptions(IndexOptions.DOCS)
            ft.setOmitNorms(true)
            ft.freeze()
            legalFieldTypes.add(ft)
        }
        run {
            val ft = FieldType()
            ft.setTokenized(false)
            ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
            ft.setOmitNorms(true)
            ft.freeze()
            legalFieldTypes.add(ft)
        }
        run {
            val ft = FieldType()
            ft.setTokenized(false)
            ft.setIndexOptions(IndexOptions.DOCS)
            ft.setStoreTermVectors(true)
            ft.freeze()
            legalFieldTypes.add(ft)
        }
        run {
            val ft = FieldType()
            ft.setTokenized(false)
            ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
            ft.setStoreTermVectors(true)
            ft.freeze()
            legalFieldTypes.add(ft)
        }

        for (ft in legalFieldTypes) {
            IndexWriter(dir, newIndexWriterConfig().setOpenMode(OpenMode.CREATE)).use { w ->
                val field = MockIndexableField("field", BytesRef("a"), ft)
                val doc = Document()
                doc.add(field)
                doc.add(field)
                w.addDocument(doc)
            }

            DirectoryReader.open(dir).use { reader ->
                val leafReader = getOnlyLeafReader(reader)

                run {
                    val terms = leafReader.terms("field")!!
                    assertEquals(1, terms.sumDocFreq)
                    if (ft.indexOptions() >= IndexOptions.DOCS_AND_FREQS) {
                        assertEquals(2, terms.sumTotalTermFreq)
                    } else {
                        assertEquals(1, terms.sumTotalTermFreq)
                    }
                    val termsEnum = terms.iterator()
                    assertTrue(termsEnum.seekExact(BytesRef("a")))
                    val pe = termsEnum.postings(null, PostingsEnum.ALL.toInt())!!
                    assertEquals(0, pe.nextDoc())
                    if (ft.indexOptions() >= IndexOptions.DOCS_AND_FREQS) {
                        assertEquals(2, pe.freq())
                    } else {
                        assertEquals(1, pe.freq())
                    }
                    assertEquals(-1, pe.nextPosition())
                    assertEquals(DocIdSetIterator.NO_MORE_DOCS, pe.nextDoc())
                }

                if (ft.storeTermVectors()) {
                    val tvTerms = leafReader.termVectors().get(0)!!.terms("field")!!
                    assertEquals(1, tvTerms.sumDocFreq)
                    assertEquals(2, tvTerms.sumTotalTermFreq)
                    val tvTermsEnum = tvTerms.iterator()
                    assertTrue(tvTermsEnum.seekExact(BytesRef("a")))
                    val pe = tvTermsEnum.postings(null, PostingsEnum.ALL.toInt())!!
                    assertEquals(0, pe.nextDoc())
                    assertEquals(2, pe.freq())
                    assertEquals(-1, pe.nextPosition())
                    assertEquals(DocIdSetIterator.NO_MORE_DOCS, pe.nextDoc())
                } else {
                    assertNull(leafReader.termVectors().get(0))
                }
            }
        }
    }
}
