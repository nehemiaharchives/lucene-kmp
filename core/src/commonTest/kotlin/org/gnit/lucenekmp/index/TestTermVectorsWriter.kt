package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CachingTokenFilter
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenFilter
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestTermVectorsWriter : LuceneTestCase() {

    // LUCENE-1442
    @Test
    @Throws(Exception::class)
    fun testDoubleOffsetCounting() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val customType = FieldType(StringField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        val f = newField("field", "abcd", customType)
        doc.add(f)
        doc.add(f)
        val f2 = newField("field", "", customType)
        doc.add(f2)
        doc.add(f)
        w.addDocument(doc)
        w.close()

        val r: IndexReader = DirectoryReader.open(dir)
        val vector: Terms? = r.termVectors().get(0)!!.terms("field")
        assertNotNull(vector)
        val termsEnum: TermsEnum = vector.iterator()
        assertNotNull(termsEnum.next())
        assertEquals("", termsEnum.term()!!.utf8ToString())

        // Token "" occurred once
        assertEquals(1, termsEnum.totalTermFreq())

        var dpEnum: PostingsEnum? = termsEnum.postings(null, PostingsEnum.ALL.toInt())
        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(8, dpEnum.startOffset())
        assertEquals(8, dpEnum.endOffset())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, dpEnum.nextDoc())

        // Token "abcd" occurred three times
        assertEquals(BytesRef("abcd"), termsEnum.next())
        dpEnum = termsEnum.postings(dpEnum, PostingsEnum.ALL.toInt())
        assertEquals(3, termsEnum.totalTermFreq())

        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(0, dpEnum.startOffset())
        assertEquals(4, dpEnum.endOffset())

        dpEnum.nextPosition()
        assertEquals(4, dpEnum.startOffset())
        assertEquals(8, dpEnum.endOffset())

        dpEnum.nextPosition()
        assertEquals(8, dpEnum.startOffset())
        assertEquals(12, dpEnum.endOffset())

        assertEquals(DocIdSetIterator.NO_MORE_DOCS, dpEnum.nextDoc())
        assertNull(termsEnum.next())
        r.close()
        dir.close()
    }

    // LUCENE-1442
    @Test
    @Throws(Exception::class)
    fun testDoubleOffsetCounting2() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        val f = newField("field", "abcd", customType)
        doc.add(f)
        doc.add(f)
        w.addDocument(doc)
        w.close()

        val r: IndexReader = DirectoryReader.open(dir)
        val termsEnum: TermsEnum = r.termVectors().get(0)!!.terms("field")!!.iterator()
        assertNotNull(termsEnum.next())
        val dpEnum: PostingsEnum? = termsEnum.postings(null, PostingsEnum.ALL.toInt())
        assertEquals(2, termsEnum.totalTermFreq())

        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(0, dpEnum.startOffset())
        assertEquals(4, dpEnum.endOffset())

        dpEnum.nextPosition()
        assertEquals(5, dpEnum.startOffset())
        assertEquals(9, dpEnum.endOffset())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, dpEnum.nextDoc())

        r.close()
        dir.close()
    }

    // LUCENE-1448
    @Test
    @Throws(Exception::class)
    fun testEndOffsetPositionCharAnalyzer() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        val f = newField("field", "abcd   ", customType)
        doc.add(f)
        doc.add(f)
        w.addDocument(doc)
        w.close()

        val r: IndexReader = DirectoryReader.open(dir)
        val termsEnum: TermsEnum = r.termVectors().get(0)!!.terms("field")!!.iterator()
        assertNotNull(termsEnum.next())
        val dpEnum: PostingsEnum? = termsEnum.postings(null, PostingsEnum.ALL.toInt())
        assertEquals(2, termsEnum.totalTermFreq())

        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(0, dpEnum.startOffset())
        assertEquals(4, dpEnum.endOffset())

        dpEnum.nextPosition()
        assertEquals(8, dpEnum.startOffset())
        assertEquals(12, dpEnum.endOffset())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, dpEnum.nextDoc())

        r.close()
        dir.close()
    }

    // LUCENE-1448
    @Test
    @Throws(Exception::class)
    fun testEndOffsetPositionWithCachingTokenFilter() {
        val dir: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val w = IndexWriter(dir, newIndexWriterConfig(analyzer))
        val doc = Document()
        CachingTokenFilter(analyzer.tokenStream("field", "abcd   ")).use { stream ->
            val customType = FieldType(TextField.TYPE_NOT_STORED)
            customType.setStoreTermVectors(true)
            customType.setStoreTermVectorPositions(true)
            customType.setStoreTermVectorOffsets(true)
            val f = Field("field", stream, customType)
            doc.add(f)
            doc.add(f)
            w.addDocument(doc)
        }
        w.close()

        val r: IndexReader = DirectoryReader.open(dir)
        val termsEnum: TermsEnum = r.termVectors().get(0)!!.terms("field")!!.iterator()
        assertNotNull(termsEnum.next())
        val dpEnum: PostingsEnum? = termsEnum.postings(null, PostingsEnum.ALL.toInt())
        assertEquals(2, termsEnum.totalTermFreq())

        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(0, dpEnum.startOffset())
        assertEquals(4, dpEnum.endOffset())

        dpEnum.nextPosition()
        assertEquals(8, dpEnum.startOffset())
        assertEquals(12, dpEnum.endOffset())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, dpEnum.nextDoc())

        r.close()
        dir.close()
    }

    // LUCENE-1448
    @Test
    @Throws(Exception::class)
    fun testEndOffsetPositionStopFilter() {
        val dir: Directory = newDirectory()
        val w =
            IndexWriter(
                dir,
                newIndexWriterConfig(
                    MockAnalyzer(
                        random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET
                    )
                )
            )
        val doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        val f = newField("field", "abcd the", customType)
        doc.add(f)
        doc.add(f)
        w.addDocument(doc)
        w.close()

        val r: IndexReader = DirectoryReader.open(dir)
        val termsEnum: TermsEnum = r.termVectors().get(0)!!.terms("field")!!.iterator()
        assertNotNull(termsEnum.next())
        val dpEnum: PostingsEnum? = termsEnum.postings(null, PostingsEnum.ALL.toInt())
        assertEquals(2, termsEnum.totalTermFreq())

        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(0, dpEnum.startOffset())
        assertEquals(4, dpEnum.endOffset())

        dpEnum.nextPosition()
        assertEquals(9, dpEnum.startOffset())
        assertEquals(13, dpEnum.endOffset())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, dpEnum.nextDoc())

        r.close()
        dir.close()
    }

    // LUCENE-1448
    @Test
    @Throws(Exception::class)
    fun testEndOffsetPositionStandard() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        val f = newField("field", "abcd the  ", customType)
        val f2 = newField("field", "crunch man", customType)
        doc.add(f)
        doc.add(f2)
        w.addDocument(doc)
        w.close()

        val r: IndexReader = DirectoryReader.open(dir)
        val termsEnum: TermsEnum = r.termVectors().get(0)!!.terms("field")!!.iterator()
        assertNotNull(termsEnum.next())
        var dpEnum: PostingsEnum? = termsEnum.postings(null, PostingsEnum.ALL.toInt())

        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(0, dpEnum.startOffset())
        assertEquals(4, dpEnum.endOffset())

        assertNotNull(termsEnum.next())
        dpEnum = termsEnum.postings(dpEnum, PostingsEnum.ALL.toInt())
        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(11, dpEnum.startOffset())
        assertEquals(17, dpEnum.endOffset())

        assertNotNull(termsEnum.next())
        dpEnum = termsEnum.postings(dpEnum, PostingsEnum.ALL.toInt())
        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(18, dpEnum.startOffset())
        assertEquals(21, dpEnum.endOffset())

        r.close()
        dir.close()
    }

    // LUCENE-1448
    @Test
    @Throws(Exception::class)
    fun testEndOffsetPositionStandardEmptyField() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        val f = newField("field", "", customType)
        val f2 = newField("field", "crunch man", customType)
        doc.add(f)
        doc.add(f2)
        w.addDocument(doc)
        w.close()

        val r: IndexReader = DirectoryReader.open(dir)
        val termsEnum: TermsEnum = r.termVectors().get(0)!!.terms("field")!!.iterator()
        assertNotNull(termsEnum.next())
        var dpEnum: PostingsEnum? = termsEnum.postings(null, PostingsEnum.ALL.toInt())

        assertEquals(1, termsEnum.totalTermFreq().toInt())
        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(1, dpEnum.startOffset())
        assertEquals(7, dpEnum.endOffset())

        assertNotNull(termsEnum.next())
        dpEnum = termsEnum.postings(dpEnum, PostingsEnum.ALL.toInt())
        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(8, dpEnum.startOffset())
        assertEquals(11, dpEnum.endOffset())

        r.close()
        dir.close()
    }

    // LUCENE-1448
    @Test
    @Throws(Exception::class)
    fun testEndOffsetPositionStandardEmptyField2() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)

        val f = newField("field", "abcd", customType)
        doc.add(f)
        doc.add(newField("field", "", customType))

        val f2 = newField("field", "crunch", customType)
        doc.add(f2)

        w.addDocument(doc)
        w.close()

        val r: IndexReader = DirectoryReader.open(dir)
        val termsEnum: TermsEnum = r.termVectors().get(0)!!.terms("field")!!.iterator()
        assertNotNull(termsEnum.next())
        var dpEnum: PostingsEnum? = termsEnum.postings(null, PostingsEnum.ALL.toInt())

        assertEquals(1, termsEnum.totalTermFreq().toInt())
        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(0, dpEnum.startOffset())
        assertEquals(4, dpEnum.endOffset())

        assertNotNull(termsEnum.next())
        dpEnum = termsEnum.postings(dpEnum, PostingsEnum.ALL.toInt())
        assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        dpEnum.nextPosition()
        assertEquals(6, dpEnum.startOffset())
        assertEquals(12, dpEnum.endOffset())

        r.close()
        dir.close()
    }

    // LUCENE-1168
    @Test
    @Throws(IOException::class)
    fun testTermVectorCorruption() {
        val dir: Directory = newDirectory()
        for (iter in 0..1) {
            var writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMaxBufferedDocs(2)
                        .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
                        .setMergeScheduler(SerialMergeScheduler())
                        .setMergePolicy(LogDocMergePolicy())
                )

            var document = Document()
            val customType = FieldType()
            customType.setStored(true)

            val storedField = newField("stored", "stored", customType)
            document.add(storedField)
            writer.addDocument(document)
            writer.addDocument(document)

            document = Document()
            document.add(storedField)
            val customType2 = FieldType(StringField.TYPE_NOT_STORED)
            customType2.setStoreTermVectors(true)
            customType2.setStoreTermVectorPositions(true)
            customType2.setStoreTermVectorOffsets(true)
            val termVectorField = newField("termVector", "termVector", customType2)

            document.add(termVectorField)
            writer.addDocument(document)
            writer.forceMerge(1)
            writer.close()

            val reader: IndexReader = DirectoryReader.open(dir)
            val storedFields: StoredFields = reader.storedFields()
            val termVectors: TermVectors = reader.termVectors()
            for (i in 0..<reader.numDocs()) {
                storedFields.document(i)
                termVectors.get(i)
            }
            reader.close()

            writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMaxBufferedDocs(2)
                        .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
                        .setMergeScheduler(SerialMergeScheduler())
                        .setMergePolicy(LogDocMergePolicy())
                )

            val indexDirs = arrayOf<Directory>(MockDirectoryWrapper(random(), TestUtil.ramCopyOf(dir)))
            writer.addIndexes(*indexDirs)
            writer.forceMerge(1)
            writer.close()
        }
        dir.close()
    }

    // LUCENE-1168
    @Test
    @Throws(IOException::class)
    fun testTermVectorCorruption2() {
        val dir: Directory = newDirectory()
        for (iter in 0..1) {
            val writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMaxBufferedDocs(2)
                        .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
                        .setMergeScheduler(SerialMergeScheduler())
                        .setMergePolicy(LogDocMergePolicy())
                )

            var document = Document()

            val customType = FieldType()
            customType.setStored(true)

            val storedField = newField("stored", "stored", customType)
            document.add(storedField)
            writer.addDocument(document)
            writer.addDocument(document)

            document = Document()
            document.add(storedField)
            val customType2 = FieldType(StringField.TYPE_NOT_STORED)
            customType2.setStoreTermVectors(true)
            customType2.setStoreTermVectorPositions(true)
            customType2.setStoreTermVectorOffsets(true)
            val termVectorField = newField("termVector", "termVector", customType2)
            document.add(termVectorField)
            writer.addDocument(document)
            writer.forceMerge(1)
            writer.close()

            val reader: IndexReader = DirectoryReader.open(dir)
            assertNull(reader.termVectors().get(0))
            assertNull(reader.termVectors().get(1))
            assertNotNull(reader.termVectors().get(2))
            reader.close()
        }
        dir.close()
    }

    // LUCENE-1168
    @Test
    @Throws(IOException::class)
    fun testTermVectorCorruption3() {
        val dir: Directory = newDirectory()
        var writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(2)
                    .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
                    .setMergeScheduler(SerialMergeScheduler())
                    .setMergePolicy(LogDocMergePolicy())
            )

        val document = Document()
        val customType = FieldType()
        customType.setStored(true)

        val storedField = newField("stored", "stored", customType)
        document.add(storedField)
        val customType2 = FieldType(StringField.TYPE_NOT_STORED)
        customType2.setStoreTermVectors(true)
        customType2.setStoreTermVectorPositions(true)
        customType2.setStoreTermVectorOffsets(true)
        val termVectorField = newField("termVector", "termVector", customType2)
        document.add(termVectorField)
        for (i in 0..9) writer.addDocument(document)
        writer.close()

        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(2)
                    .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
                    .setMergeScheduler(SerialMergeScheduler())
                    .setMergePolicy(LogDocMergePolicy())
            )
        for (i in 0..5) writer.addDocument(document)

        writer.forceMerge(1)
        writer.close()

        val reader: IndexReader = DirectoryReader.open(dir)
        val storedFields: StoredFields = reader.storedFields()
        val termVectors: TermVectors = reader.termVectors()
        for (i in 0..9) {
            termVectors.get(i)
            storedFields.document(i)
        }
        reader.close()
        dir.close()
    }

    // LUCENE-1008
    @Test
    @Throws(IOException::class)
    fun testNoTermVectorAfterTermVector() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        var document = Document()
        val customType2 = FieldType(TextField.TYPE_NOT_STORED)
        customType2.setStoreTermVectors(true)
        customType2.setStoreTermVectorPositions(true)
        customType2.setStoreTermVectorOffsets(true)
        document.add(newField("tvtest", "a b c", customType2))
        iw.addDocument(document)
        document = Document()
        document.add(newTextField("tvtest", "x y z", Field.Store.NO))
        iw.addDocument(document)
        // Make first segment
        iw.commit()

        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        document = Document()
        document.add(newField("tvtest", "a b c", customType))
        iw.addDocument(document)
        // Make 2nd segment
        iw.commit()

        iw.forceMerge(1)
        iw.close()
        dir.close()
    }

    // LUCENE-1010
    @Test
    @Throws(IOException::class)
    fun testNoTermVectorAfterTermVectorMerge() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        var document = Document()
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setStoreTermVectors(true)
        document.add(newField("tvtest", "a b c", customType))
        iw.addDocument(document)
        iw.commit()

        document = Document()
        document.add(newTextField("tvtest", "x y z", Field.Store.NO))
        iw.addDocument(document)
        // Make first segment
        iw.commit()

        iw.forceMerge(1)

        val customType2 = FieldType(TextField.TYPE_NOT_STORED)
        customType2.setStoreTermVectors(true)
        document.add(newField("tvtest", "a b c", customType2))
        document = Document()
        iw.addDocument(document)
        // Make 2nd segment
        iw.commit()
        iw.forceMerge(1)

        iw.close()
        dir.close()
    }

    /** In a single doc, for the same field, mix the term vectors up  */
    @Test
    @Throws(IOException::class)
    fun testInconsistentTermVectorOptions() {
        var a: FieldType?
        var b: FieldType?

        // no vectors + vectors
        a = FieldType(TextField.TYPE_NOT_STORED)
        b = FieldType(TextField.TYPE_NOT_STORED)
        b.setStoreTermVectors(true)
        doTestMixup(a, b)

        // vectors + vectors with pos
        a = FieldType(TextField.TYPE_NOT_STORED)
        a.setStoreTermVectors(true)
        b = FieldType(TextField.TYPE_NOT_STORED)
        b.setStoreTermVectors(true)
        b.setStoreTermVectorPositions(true)
        doTestMixup(a, b)

        // vectors + vectors with off
        a = FieldType(TextField.TYPE_NOT_STORED)
        a.setStoreTermVectors(true)
        b = FieldType(TextField.TYPE_NOT_STORED)
        b.setStoreTermVectors(true)
        b.setStoreTermVectorOffsets(true)
        doTestMixup(a, b)

        // vectors with pos + vectors with pos + off
        a = FieldType(TextField.TYPE_NOT_STORED)
        a.setStoreTermVectors(true)
        a.setStoreTermVectorPositions(true)
        b = FieldType(TextField.TYPE_NOT_STORED)
        b.setStoreTermVectors(true)
        b.setStoreTermVectorPositions(true)
        b.setStoreTermVectorOffsets(true)
        doTestMixup(a, b)

        // vectors with pos + vectors with pos + pay
        a = FieldType(TextField.TYPE_NOT_STORED)
        a.setStoreTermVectors(true)
        a.setStoreTermVectorPositions(true)
        b = FieldType(TextField.TYPE_NOT_STORED)
        b.setStoreTermVectors(true)
        b.setStoreTermVectorPositions(true)
        b.setStoreTermVectorPayloads(true)
        doTestMixup(a, b)
    }

    @Throws(IOException::class)
    private fun doTestMixup(ft1: FieldType?, ft2: FieldType?) {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)

        // add 3 good docs
        for (i in 0..2) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.NO))
            iw.addDocument(doc)
        }

        // add broken doc
        val doc = Document()
        doc.add(Field("field", "value1", ft1!!))
        doc.add(Field("field", "value2", ft2!!))

        // ensure broken doc hits exception
        val expected: IllegalArgumentException =
            expectThrows(IllegalArgumentException::class) { iw.addDocument(doc) }
        val message = assertNotNull(expected.message)
        assertTrue(
            message.startsWith(
                "all instances of a given field name must have the same term vectors settings"
            ) || message.startsWith(
                "Inconsistency of field data structures across documents for field [field]"
            )
        )
        // ensure good docs are still ok
        val ir: IndexReader = iw.reader
        assertEquals(3, ir.numDocs())

        ir.close()
        iw.close()
        dir.close()
    }

    // LUCENE-5611: don't abort segment when term vector settings are wrong
    @Test
    @Throws(Exception::class)
    fun testNoAbortOnBadTVSettings() {
        val dir: Directory = newDirectory()
        // Don't use RandomIndexWriter because we want to be sure both docs go to 1 seg:
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val iw = IndexWriter(dir, iwc)

        val doc = Document()
        iw.addDocument(doc)
        val ft = FieldType(StoredField.TYPE)
        ft.setStoreTermVectors(true)
        ft.freeze()
        doc.add(Field("field", "value", ft))

        expectThrows(
            IllegalArgumentException::class
        ) {
            iw.addDocument(doc)
        }

        val r: IndexReader = DirectoryReader.open(iw)

        // Make sure the exc didn't lose our first document:
        assertEquals(1, r.numDocs())
        iw.close()
        r.close()
        dir.close()
    }
}
