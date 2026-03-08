package org.gnit.lucenekmp.index

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.FileNotFoundException
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.DocHelper
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.Version
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestDirectoryReader : LuceneTestCase() {
    @Test
    fun testDocument() {
        val dir = newDirectory()
        val doc1 = Document()
        val doc2 = Document()
        DocHelper.setupDoc(doc1)
        DocHelper.setupDoc(doc2)
        DocHelper.writeDoc(random(), dir, doc1)
        DocHelper.writeDoc(random(), dir, doc2)
        val reader = DirectoryReader.open(dir)
        assertTrue(reader != null)
        assertTrue(reader is StandardDirectoryReader)
        val storedFields = reader.storedFields()

        val newDoc1 = storedFields.document(0)
        assertTrue(newDoc1 != null)
        assertTrue(DocHelper.numFields(newDoc1) == DocHelper.numFields(doc1) - DocHelper.unstored.size)
        val newDoc2 = storedFields.document(1)
        assertTrue(newDoc2 != null)
        assertTrue(DocHelper.numFields(newDoc2) == DocHelper.numFields(doc2) - DocHelper.unstored.size)
        val vector = reader.termVectors().get(0)!!.terms(DocHelper.TEXT_FIELD_2_KEY)
        assertNotNull(vector)

        reader.close()
        dir.close()
    }

    @Test
    fun testMultiTermDocs() {
        val ramDir1 = newDirectory()
        addDoc(random(), ramDir1, "test foo", true)
        val ramDir2 = newDirectory()
        addDoc(random(), ramDir2, "test blah", true)
        val ramDir3 = newDirectory()
        addDoc(random(), ramDir3, "test wow", true)

        val readers1 = arrayOf<IndexReader>(DirectoryReader.open(ramDir1), DirectoryReader.open(ramDir3))
        val readers2 = arrayOf<IndexReader>(DirectoryReader.open(ramDir1), DirectoryReader.open(ramDir2), DirectoryReader.open(ramDir3))
        val mr2 = MultiReader(*readers1)
        val mr3 = MultiReader(*readers2)

        val te2 = MultiTerms.getTerms(mr2, "body")!!.iterator()
        te2.seekCeil(BytesRef("wow"))
        var td = TestUtil.docs(random(), mr2, "body", te2.term()!!, null, 0)

        val te3 = MultiTerms.getTerms(mr3, "body")!!.iterator()
        te3.seekCeil(BytesRef("wow"))
        td = TestUtil.docs(random(), te3, td, 0)

        var ret = 0
        while (td.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) ret += td.docID()
        assertTrue(ret > 0)
        readers1[0].close()
        readers1[1].close()
        readers2[0].close()
        readers2[1].close()
        readers2[2].close()
        ramDir1.close()
        ramDir2.close()
        ramDir3.close()
    }

    private fun addDoc(random: Random, ramDir1: Directory, s: String, create: Boolean) {
        val iw =
            IndexWriter(
                ramDir1,
                newIndexWriterConfig(MockAnalyzer(random))
                    .setOpenMode(if (create) OpenMode.CREATE else OpenMode.APPEND),
            )
        val doc = Document()
        doc.add(newTextField("body", s, Field.Store.NO))
        iw.addDocument(doc)
        iw.close()
    }

    @Test
    fun testIsCurrent() {
        val d = newDirectory()
        var writer = IndexWriter(d, newIndexWriterConfig(MockAnalyzer(random())))
        addDocumentWithFields(writer)
        writer.close()
        val reader = DirectoryReader.open(d)
        assertTrue(reader.isCurrent)
        writer = IndexWriter(d, newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND))
        addDocumentWithFields(writer)
        writer.close()
        assertFalse(reader.isCurrent)
        writer = IndexWriter(d, newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.CREATE))
        addDocumentWithFields(writer)
        writer.close()
        assertFalse(reader.isCurrent)
        reader.close()
        d.close()
    }

    @Test
    fun testGetFieldNames() {
        val d = newDirectory()
        var writer = IndexWriter(d, newIndexWriterConfig(MockAnalyzer(random())))
        var doc = Document()

        val customType3 = FieldType()
        customType3.setStored(true)

        doc.add(StringField("keyword", "test1", Field.Store.YES))
        doc.add(TextField("text", "test1", Field.Store.YES))
        doc.add(Field("unindexed", "test1", customType3))
        doc.add(TextField("unstored", "test1", Field.Store.NO))
        writer.addDocument(doc)

        writer.close()
        var reader = DirectoryReader.open(d)
        var fieldInfos = FieldInfos.getMergedFieldInfos(reader)
        assertNotNull(fieldInfos.fieldInfo("keyword"))
        assertNotNull(fieldInfos.fieldInfo("text"))
        assertNotNull(fieldInfos.fieldInfo("unindexed"))
        assertNotNull(fieldInfos.fieldInfo("unstored"))
        reader.close()
        writer =
            IndexWriter(
                d,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.APPEND)
                    .setMergePolicy(newLogMergePolicy()),
            )
        val mergeFactor = (writer.config.mergePolicy as LogMergePolicy).mergeFactor
        for (i in 0..<5 * mergeFactor) {
            doc = Document()
            doc.add(StringField("keyword", "test1", Field.Store.YES))
            doc.add(TextField("text", "test1", Field.Store.YES))
            doc.add(Field("unindexed", "test1", customType3))
            doc.add(TextField("unstored", "test1", Field.Store.NO))
            writer.addDocument(doc)
        }
        for (i in 0..<5 * mergeFactor) {
            doc = Document()
            doc.add(StringField("keyword2", "test1", Field.Store.YES))
            doc.add(TextField("text2", "test1", Field.Store.YES))
            doc.add(Field("unindexed2", "test1", customType3))
            doc.add(TextField("unstored2", "test1", Field.Store.NO))
            writer.addDocument(doc)
        }

        val customType5 = FieldType(TextField.TYPE_STORED)
        customType5.setStoreTermVectors(true)
        val customType6 = FieldType(TextField.TYPE_STORED)
        customType6.setStoreTermVectors(true)
        customType6.setStoreTermVectorOffsets(true)
        val customType7 = FieldType(TextField.TYPE_STORED)
        customType7.setStoreTermVectors(true)
        customType7.setStoreTermVectorPositions(true)
        val customType8 = FieldType(TextField.TYPE_STORED)
        customType8.setStoreTermVectors(true)
        customType8.setStoreTermVectorOffsets(true)
        customType8.setStoreTermVectorPositions(true)

        for (i in 0..<5 * mergeFactor) {
            doc = Document()
            doc.add(TextField("tvnot", "tvnot", Field.Store.YES))
            doc.add(Field("termvector", "termvector", customType5))
            doc.add(Field("tvoffset", "tvoffset", customType6))
            doc.add(Field("tvposition", "tvposition", customType7))
            doc.add(Field("tvpositionoffset", "tvpositionoffset", customType8))
            writer.addDocument(doc)
        }

        writer.close()

        reader = DirectoryReader.open(d)
        fieldInfos = FieldInfos.getMergedFieldInfos(reader)

        val allFieldNames = HashSet<String>()
        val indexedFieldNames = HashSet<String>()
        val notIndexedFieldNames = HashSet<String>()
        val tvFieldNames = HashSet<String>()

        for (fieldInfo in fieldInfos) {
            val name = fieldInfo.name
            allFieldNames.add(name)
            if (fieldInfo.indexOptions != IndexOptions.NONE) {
                indexedFieldNames.add(name)
            } else {
                notIndexedFieldNames.add(name)
            }
            if (fieldInfo.hasTermVectors()) {
                tvFieldNames.add(name)
            }
        }

        assertTrue(allFieldNames.contains("keyword"))
        assertTrue(allFieldNames.contains("text"))
        assertTrue(allFieldNames.contains("unindexed"))
        assertTrue(allFieldNames.contains("unstored"))
        assertTrue(allFieldNames.contains("keyword2"))
        assertTrue(allFieldNames.contains("text2"))
        assertTrue(allFieldNames.contains("unindexed2"))
        assertTrue(allFieldNames.contains("unstored2"))
        assertTrue(allFieldNames.contains("tvnot"))
        assertTrue(allFieldNames.contains("termvector"))
        assertTrue(allFieldNames.contains("tvposition"))
        assertTrue(allFieldNames.contains("tvoffset"))
        assertTrue(allFieldNames.contains("tvpositionoffset"))

        assertEquals(11, indexedFieldNames.size)
        assertTrue(indexedFieldNames.contains("keyword"))
        assertTrue(indexedFieldNames.contains("text"))
        assertTrue(indexedFieldNames.contains("unstored"))
        assertTrue(indexedFieldNames.contains("keyword2"))
        assertTrue(indexedFieldNames.contains("text2"))
        assertTrue(indexedFieldNames.contains("unstored2"))
        assertTrue(indexedFieldNames.contains("tvnot"))
        assertTrue(indexedFieldNames.contains("termvector"))
        assertTrue(indexedFieldNames.contains("tvposition"))
        assertTrue(indexedFieldNames.contains("tvoffset"))
        assertTrue(indexedFieldNames.contains("tvpositionoffset"))

        assertEquals(2, notIndexedFieldNames.size)
        assertTrue(notIndexedFieldNames.contains("unindexed"))
        assertTrue(notIndexedFieldNames.contains("unindexed2"))

        assertEquals(4, tvFieldNames.size, tvFieldNames.toString())
        assertTrue(tvFieldNames.contains("termvector"))

        reader.close()
        d.close()
    }

    @Test
    fun testTermVectors() {
        val d = newDirectory()
        val writer =
            IndexWriter(
                d,
                newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()),
            )
        val mergeFactor = (writer.config.mergePolicy as LogMergePolicy).mergeFactor
        val customType5 = FieldType(TextField.TYPE_STORED)
        customType5.setStoreTermVectors(true)
        val customType6 = FieldType(TextField.TYPE_STORED)
        customType6.setStoreTermVectors(true)
        customType6.setStoreTermVectorOffsets(true)
        val customType7 = FieldType(TextField.TYPE_STORED)
        customType7.setStoreTermVectors(true)
        customType7.setStoreTermVectorPositions(true)
        val customType8 = FieldType(TextField.TYPE_STORED)
        customType8.setStoreTermVectors(true)
        customType8.setStoreTermVectorOffsets(true)
        customType8.setStoreTermVectorPositions(true)
        for (i in 0..<5 * mergeFactor) {
            val doc = Document()
            doc.add(TextField("tvnot", "one two two three three three", Field.Store.YES))
            doc.add(Field("termvector", "one two two three three three", customType5))
            doc.add(Field("tvoffset", "one two two three three three", customType6))
            doc.add(Field("tvposition", "one two two three three three", customType7))
            doc.add(Field("tvpositionoffset", "one two two three three three", customType8))
            writer.addDocument(doc)
        }
        writer.close()
        d.close()
    }

    fun assertTermDocsCount(msg: String, reader: IndexReader, term: Term, expected: Int) {
        val tdocs = TestUtil.docs(random(), reader, term.field(), BytesRef(term.text()), null, 0)
        var count = 0
        if (tdocs != null) {
            while (tdocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                count++
            }
        }
        assertEquals(expected, count, "$msg, count mismatch")
    }

    @Test
    fun testBinaryFields() {
        val dir = newDirectory()
        val bin1 = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val bin2 = byteArrayOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0)

        var writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()),
            )

        for (i in 0..<10) {
            addDoc(writer, "document number ${i + 1}")
            addDocumentWithFields(writer)
            addDocumentWithDifferentFields(writer)
            addDocumentWithTermVectorFields(writer)
        }
        writer.close()
        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.APPEND)
                    .setMergePolicy(newLogMergePolicy()),
            )
        val doc = Document()
        doc.add(StoredField("bin1", bin1))
        doc.add(StoredField("bin2", StoredFieldDataInput(ByteArrayDataInput(bin2))))
        doc.add(TextField("junk", "junk text", Field.Store.NO))
        writer.addDocument(doc)
        writer.close()
        var reader = DirectoryReader.open(dir)
        var doc2 = reader.storedFields().document(reader.maxDoc() - 1)
        var fields = doc2.getFields("bin1")
        assertNotNull(fields)
        assertEquals(1, fields.size)
        var b1 = fields[0]
        assertTrue(b1.binaryValue() != null)
        var bytesRef1 = b1.binaryValue()!!
        assertEquals(bin1.size, bytesRef1.length)
        for (i in bin1.indices) {
            assertEquals(bin1[i], bytesRef1.bytes[i + bytesRef1.offset])
        }
        fields = doc2.getFields("bin2")
        assertNotNull(fields)
        assertEquals(1, fields.size)
        var b2 = fields[0]
        assertTrue(b2.binaryValue() != null)
        var bytesRef2 = b2.binaryValue()!!
        assertEquals(bin2.size, bytesRef2.length)
        for (i in bin2.indices) {
            assertEquals(bin2[i], bytesRef2.bytes[i + bytesRef2.offset])
        }

        reader.close()

        writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.APPEND)
                    .setMergePolicy(newLogMergePolicy()),
            )
        writer.forceMerge(1)
        writer.close()
        reader = DirectoryReader.open(dir)
        doc2 = reader.storedFields().document(reader.maxDoc() - 1)
        fields = doc2.getFields("bin1")
        assertNotNull(fields)
        assertEquals(1, fields.size)
        b1 = fields[0]
        assertTrue(b1.binaryValue() != null)
        bytesRef1 = b1.binaryValue()!!
        assertEquals(bin1.size, bytesRef1.length)
        for (i in bin1.indices) {
            assertEquals(bin1[i], bytesRef1.bytes[i + bytesRef1.offset])
        }
        fields = doc2.getFields("bin2")
        assertNotNull(fields)
        assertEquals(1, fields.size)
        b2 = fields[0]
        bytesRef2 = b2.binaryValue()!!
        assertEquals(bin2.size, bytesRef2.length)
        for (i in bin2.indices) {
            assertEquals(bin2[i], bytesRef2.bytes[i + bytesRef2.offset])
        }
        reader.close()
        dir.close()
    }

    @Test
    fun testFilesOpenClose() {
        val dirFile = createTempDir("TestIndexReader.testFilesOpenClose").resolve("index")
        var dir = newFSDirectory(dirFile)

        var writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        addDoc(writer, "test")
        writer.close()
        dir.close()

        IOUtils.rm(dirFile)
        dir = newFSDirectory(dirFile)

        writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.CREATE))
        addDoc(writer, "test")
        writer.close()
        dir.close()

        dir = newFSDirectory(dirFile)
        val reader1 = DirectoryReader.open(dir)
        reader1.close()
        dir.close()

        IOUtils.rm(dirFile)
    }

    @Test
    fun testOpenReaderAfterDelete() {
        val dirFile = createTempDir("deletetest")
        val dir = newFSDirectory(dirFile)
        dir.checkIndexOnClose = false
        expectThrowsAnyOf(
            mutableListOf(FileNotFoundException::class, NoSuchFileException::class, IndexNotFoundException::class, IOException::class),
        ) { DirectoryReader.open(dir) }

        IOUtils.rm(dirFile)

        expectThrowsAnyOf(
            mutableListOf(FileNotFoundException::class, NoSuchFileException::class, IndexNotFoundException::class, IOException::class),
        ) { DirectoryReader.open(dir) }

        dir.close()
    }

    @Test
    fun testGetIndexCommit() {
        val d = newDirectory()
        var writer =
            IndexWriter(
                d,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(newLogMergePolicy(10)),
            )
        for (i in 0..<27) addDocumentWithFields(writer)
        writer.close()

        val sis = SegmentInfos.readLatestCommit(d)
        val r = DirectoryReader.open(d)
        val c = r.indexCommit

        assertEquals(sis.segmentsFileName, c.segmentsFileName)
        assertEquals(c, r.indexCommit)

        writer =
            IndexWriter(
                d,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.APPEND)
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(newLogMergePolicy(10)),
            )
        for (i in 0..<7) addDocumentWithFields(writer)
        writer.close()

        var r2 = DirectoryReader.openIfChanged(r)
        assertNotNull(r2)
        assertNotEquals(c, r2.indexCommit)
        assertNotEquals(1, r2.indexCommit.segmentCount)
        r2.close()

        writer = IndexWriter(d, newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND))
        writer.forceMerge(1)
        writer.close()

        r2 = DirectoryReader.openIfChanged(r)
        assertNotNull(r2)
        assertNull(DirectoryReader.openIfChanged(r2))
        assertEquals(1, r2.indexCommit.segmentCount)

        r.close()
        r2.close()
        d.close()
    }

    companion object {
        fun addDocumentWithFields(writer: IndexWriter) {
            val doc = Document()

            val customType3 = FieldType()
            customType3.setStored(true)
            doc.add(LuceneTestCase.newStringField("keyword", "test1", Field.Store.YES))
            doc.add(LuceneTestCase.newTextField("text", "test1", Field.Store.YES))
            doc.add(LuceneTestCase.newField("unindexed", "test1", customType3))
            doc.add(TextField("unstored", "test1", Field.Store.NO))
            writer.addDocument(doc)
        }

        fun addDocumentWithDifferentFields(writer: IndexWriter) {
            val doc = Document()

            val customType3 = FieldType()
            customType3.setStored(true)
            doc.add(LuceneTestCase.newStringField("keyword2", "test1", Field.Store.YES))
            doc.add(LuceneTestCase.newTextField("text2", "test1", Field.Store.YES))
            doc.add(LuceneTestCase.newField("unindexed2", "test1", customType3))
            doc.add(TextField("unstored2", "test1", Field.Store.NO))
            writer.addDocument(doc)
        }

        fun addDocumentWithTermVectorFields(writer: IndexWriter) {
            val doc = Document()
            val customType5 = FieldType(TextField.TYPE_STORED)
            customType5.setStoreTermVectors(true)
            val customType6 = FieldType(TextField.TYPE_STORED)
            customType6.setStoreTermVectors(true)
            customType6.setStoreTermVectorOffsets(true)
            val customType7 = FieldType(TextField.TYPE_STORED)
            customType7.setStoreTermVectors(true)
            customType7.setStoreTermVectorPositions(true)
            val customType8 = FieldType(TextField.TYPE_STORED)
            customType8.setStoreTermVectors(true)
            customType8.setStoreTermVectorOffsets(true)
            customType8.setStoreTermVectorPositions(true)
            doc.add(LuceneTestCase.newTextField("tvnot", "tvnot", Field.Store.YES))
            doc.add(LuceneTestCase.newField("termvector", "termvector", customType5))
            doc.add(LuceneTestCase.newField("tvoffset", "tvoffset", customType6))
            doc.add(LuceneTestCase.newField("tvposition", "tvposition", customType7))
            doc.add(LuceneTestCase.newField("tvpositionoffset", "tvpositionoffset", customType8))
            writer.addDocument(doc)
        }

        fun addDoc(writer: IndexWriter, value: String) {
            val doc = Document()
            doc.add(LuceneTestCase.newTextField("content", value, Field.Store.NO))
            writer.addDocument(doc)
        }

        fun assertIndexEquals(index1: DirectoryReader, index2: DirectoryReader) {
            assertEquals(index1.numDocs(), index2.numDocs(), "IndexReaders have different values for numDocs.")
            assertEquals(index1.maxDoc(), index2.maxDoc(), "IndexReaders have different values for maxDoc.")
            assertEquals(index1.hasDeletions(), index2.hasDeletions(), "Only one IndexReader has deletions.")
            assertEquals(index1.leaves().size == 1, index2.leaves().size == 1, "Single segment test differs.")
        }

        fun createDocument(id: String): Document {
            val doc = Document()
            val customType = FieldType(TextField.TYPE_STORED)
            customType.setTokenized(false)
            customType.setOmitNorms(true)
            doc.add(LuceneTestCase.newField("id", id, customType))
            return doc
        }
    }

    @Test
    fun testNoDir() {
        val tempDir = createTempDir("doesnotexist")
        val dir = newFSDirectory(tempDir)
        expectThrows(IndexNotFoundException::class) { DirectoryReader.open(dir) }
        dir.close()
    }

    @Test
    fun testNoDupCommitFileNames() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(2))
        writer.addDocument(createDocument("a"))
        writer.addDocument(createDocument("a"))
        writer.addDocument(createDocument("a"))
        writer.close()

        val commits = DirectoryReader.listCommits(dir)
        for (commit in commits) {
            val files = commit.fileNames
            val seen = HashSet<String>()
            for (fileName in files) {
                assertFalse(seen.contains(fileName), "file $fileName was duplicated")
                seen.add(fileName)
            }
        }

        dir.close()
    }

    @Test
    fun testUniqueTermCount() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(newTextField("field", "a b c d e f g h i j k l m n o p q r s t u v w x y z", Field.Store.NO))
        doc.add(newTextField("number", "0 1 2 3 4 5 6 7 8 9", Field.Store.NO))
        writer.addDocument(doc)
        writer.addDocument(doc)
        writer.commit()

        val r = DirectoryReader.open(dir)
        val r1 = getOnlyLeafReader(r)
        assertEquals(26, r1.terms("field")!!.size())
        assertEquals(10, r1.terms("number")!!.size())
        writer.addDocument(doc)
        writer.commit()
        val r2 = DirectoryReader.openIfChanged(r)
        assertNotNull(r2)
        r.close()

        for (s in r2.leaves()) {
            assertEquals(26, s.reader().terms("field")!!.size())
            assertEquals(10, s.reader().terms("number")!!.size())
        }
        r2.close()
        writer.close()
        dir.close()
    }

    @Test
    fun testPrepareCommitIsCurrent() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        writer.commit()
        val doc = Document()
        writer.addDocument(doc)
        val r = DirectoryReader.open(dir)
        assertTrue(r.isCurrent)
        writer.addDocument(doc)
        writer.prepareCommit()
        assertTrue(r.isCurrent)
        val r2 = DirectoryReader.openIfChanged(r)
        assertNull(r2)
        writer.commit()
        assertFalse(r.isCurrent)
        writer.close()
        r.close()
        dir.close()
    }

    @Test
    fun testListCommits() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig()
                    .setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE),
            )
        writer.addDocument(Document())
        writer.commit()
        writer.addDocument(Document())
        writer.commit()
        writer.addDocument(Document())
        writer.commit()
        writer.close()
        var currentGen = 0L
        for (ic in DirectoryReader.listCommits(dir)) {
            assertTrue(currentGen < ic.generation, "currentGen=$currentGen commitGen=${ic.generation}")
            currentGen = ic.generation
        }
        dir.close()
    }

    @Test
    fun testTotalTermFreqCached() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val d = Document()
        d.add(newTextField("f", "a a b", Field.Store.NO))
        writer.addDocument(d)
        val r = DirectoryReader.open(writer)
        writer.close()
        try {
            assumeTrue("codec must implement totalTermFreq", r.totalTermFreq(Term("f", BytesRef("b"))) != -1L)
            assertEquals(1, r.totalTermFreq(Term("f", BytesRef("b"))))
            assertEquals(2, r.totalTermFreq(Term("f", BytesRef("a"))))
            assertEquals(1, r.totalTermFreq(Term("f", BytesRef("b"))))
        } finally {
            r.close()
            dir.close()
        }
    }

    @Test
    fun testGetDocCount() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        var d = Document()
        d.add(newTextField("f", "a", Field.Store.NO))
        writer.addDocument(d)
        d = Document()
        d.add(newTextField("f", "a", Field.Store.NO))
        writer.addDocument(d)
        val r = DirectoryReader.open(writer)
        writer.close()
        try {
            assumeTrue("codec must implement getDocCount", r.getDocCount("f") != -1)
            assertEquals(2, r.getDocCount("f"))
        } finally {
            r.close()
            dir.close()
        }
    }

    @Test
    fun testGetSumDocFreq() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        var d = Document()
        d.add(newTextField("f", "a", Field.Store.NO))
        writer.addDocument(d)
        d = Document()
        d.add(newTextField("f", "b", Field.Store.NO))
        writer.addDocument(d)
        val r = DirectoryReader.open(writer)
        writer.close()
        try {
            assumeTrue("codec must implement getSumDocFreq", r.getSumDocFreq("f") != -1L)
            assertEquals(2, r.getSumDocFreq("f"))
        } finally {
            r.close()
            dir.close()
        }
    }

    @Test
    fun testGetSumTotalTermFreq() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        var d = Document()
        d.add(newTextField("f", "a b b", Field.Store.NO))
        writer.addDocument(d)
        d = Document()
        d.add(newTextField("f", "a a b", Field.Store.NO))
        writer.addDocument(d)
        val r = DirectoryReader.open(writer)
        writer.close()
        try {
            assumeTrue("codec must implement getSumTotalTermFreq", r.getSumTotalTermFreq("f") != -1L)
            assertEquals(6, r.getSumTotalTermFreq("f"))
        } finally {
            r.close()
            dir.close()
        }
    }

    @Test
    fun testReaderFinishedListener() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()),
            )
        (writer.config.mergePolicy as LogMergePolicy).mergeFactor = 3
        writer.addDocument(Document())
        writer.commit()
        writer.addDocument(Document())
        writer.commit()
        val reader = DirectoryReader.open(writer)
        val closeCount = intArrayOf(0)
        val listener = IndexReader.ClosedListener { closeCount[0]++ }

        runBlocking { reader.readerCacheHelper!!.addClosedListener(listener) }
        reader.close()
        assertEquals(1, closeCount[0])
        writer.close()

        val reader2 = DirectoryReader.open(dir)
        runBlocking { reader2.readerCacheHelper!!.addClosedListener(listener) }

        closeCount[0] = 0
        reader2.close()
        assertEquals(1, closeCount[0])
        dir.close()
    }

    @Test
    fun testOOBDocID() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        writer.addDocument(Document())
        val r = DirectoryReader.open(writer)
        writer.close()
        r.storedFields().document(0)
        expectThrows(IllegalArgumentException::class) { r.storedFields().document(1) }
        r.close()
        dir.close()
    }

    @Test
    fun testTryIncRef() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        writer.addDocument(Document())
        writer.commit()
        val r = DirectoryReader.open(dir)
        assertTrue(r.tryIncRef())
        runBlocking { r.decRef() }
        r.close()
        assertFalse(r.tryIncRef())
        writer.close()
        dir.close()
    }

    @Test
    fun testStressTryIncRef() = runBlocking {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        writer.addDocument(Document())
        writer.commit()
        val r = DirectoryReader.open(dir)
        val numThreads = atLeast(2)

        val threads = Array(numThreads) { IncThread(r, random()) }
        for (thread in threads) {
            thread.start()
        }
        delay(100)

        assertTrue(r.tryIncRef())
        r.decRef()
        r.close()

        for (thread in threads) {
            thread.join()
            assertNull(thread.failed)
        }
        assertFalse(r.tryIncRef())
        writer.close()
        dir.close()
    }

    class IncThread(private val toInc: IndexReader, private val random: Random) {
        var failed: Throwable? = null
        private var job: Job? = null

        fun start() {
            job =
                kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
                    try {
                        while (toInc.tryIncRef()) {
                            assertFalse(toInc.hasDeletions())
                            toInc.decRef()
                        }
                        assertFalse(toInc.tryIncRef())
                    } catch (e: Throwable) {
                        failed = e
                    }
                }
        }

        suspend fun join() {
            job!!.join()
        }
    }

    @Test
    fun testLoadCertainFields() {
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(newStringField("field1", "foobar", Field.Store.YES))
        doc.add(newStringField("field2", "foobaz", Field.Store.YES))
        writer.addDocument(doc)
        val r = writer.reader
        writer.close()
        val fieldsToLoad = HashSet<String>()
        assertEquals(0, r.storedFields().document(0, fieldsToLoad).getFields().size)
        fieldsToLoad.add("field1")
        val doc2 = r.storedFields().document(0, fieldsToLoad)
        assertEquals(1, doc2.getFields().size)
        assertEquals("foobar", doc2.get("field1"))
        r.close()
        dir.close()
    }

    @Test
    fun testIndexExistsOnNonExistentDirectory() {
        val tempDir = createTempDir("testIndexExistsOnNonExistentDirectory")
        val dir = newFSDirectory(tempDir)
        assertFalse(DirectoryReader.indexExists(dir))
        dir.close()
    }

    @Test
    fun testOpenWithInvalidMinCompatVersion() {
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { writer ->
                val doc = Document()
                doc.add(newStringField("field1", "foobar", Field.Store.YES))
                doc.add(newStringField("field2", "foobaz", Field.Store.YES))
                writer.addDocument(doc)
                writer.commit()
                val commit = DirectoryReader.listCommits(dir)[0]
                val leafSorter = Comparator<LeafReader> { _, _ -> 0 }
                expectThrows(IllegalArgumentException::class) {
                    DirectoryReader.open(commit, -1, leafSorter)
                }
                DirectoryReader.open(commit, random().nextInt(Version.LATEST.major + 1), leafSorter).close()
            }
        }
    }
}
