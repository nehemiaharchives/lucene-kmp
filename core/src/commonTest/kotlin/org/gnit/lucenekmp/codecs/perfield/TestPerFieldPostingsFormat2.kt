package org.gnit.lucenekmp.codecs.perfield

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.index.LogDocMergePolicy
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingPostingsFormat
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

/** */
// TODO: would be better in this test to pull termsenums and instanceof or something?
// this way we can verify PFPF is doing the right thing.
// for now we do termqueries.
class TestPerFieldPostingsFormat2 : LuceneTestCase() {
    @Throws(IOException::class)
    private fun newWriter(dir: Directory, conf: IndexWriterConfig): IndexWriter {
        val logByteSizeMergePolicy = LogDocMergePolicy()
        logByteSizeMergePolicy.noCFSRatio = 0.0 // make sure we use plain
        // files
        conf.setMergePolicy(logByteSizeMergePolicy)

        val writer = IndexWriter(dir, conf)
        return writer
    }

    @Throws(IOException::class)
    private fun addDocs(writer: IndexWriter, numDocs: Int) {
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(newTextField("content", "aaa", Field.Store.NO))
            writer.addDocument(doc)
        }
    }

    @Throws(IOException::class)
    private fun addDocs2(writer: IndexWriter, numDocs: Int) {
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(newTextField("content", "bbb", Field.Store.NO))
            writer.addDocument(doc)
        }
    }

    @Throws(IOException::class)
    private fun addDocs3(writer: IndexWriter, numDocs: Int) {
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(newTextField("content", "ccc", Field.Store.NO))
            doc.add(newStringField("id", "$i", Field.Store.YES))
            writer.addDocument(doc)
        }
    }

    /*
     * Test that heterogeneous index segments are merge successfully
     */
    @Test
    @Throws(IOException::class)
    fun testMergeUnusedPerFieldCodec() {
        val dir = newDirectory()
        val iwconf =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setOpenMode(OpenMode.CREATE)
                .setCodec(MockCodec())
        val writer = newWriter(dir, iwconf)
        addDocs(writer, 10)
        writer.commit()
        addDocs3(writer, 10)
        writer.commit()
        addDocs2(writer, 10)
        writer.commit()
        assertEquals(30, writer.getDocStats().maxDoc)
        TestUtil.checkIndex(dir)
        writer.forceMerge(1)
        assertEquals(30, writer.getDocStats().maxDoc)
        writer.close()
        dir.close()
    }

    /*
     * Test that heterogeneous index segments are merged sucessfully
     */
    // TODO: not sure this test is that great, we should probably peek inside PerFieldPostingsFormat
    // or something?!
    @Test
    @Throws(IOException::class)
    fun testChangeCodecAndMerge() {
        val dir = newDirectory()
        if (VERBOSE) {
            println("TEST: make new index")
        }
        var iwconf =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setOpenMode(OpenMode.CREATE)
                .setCodec(MockCodec())
        iwconf.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        var writer = newWriter(dir, iwconf)

        addDocs(writer, 10)
        writer.commit()
        assertQuery(Term("content", "aaa"), dir, 10)
        if (VERBOSE) {
            println("TEST: addDocs3")
        }
        addDocs3(writer, 10)
        writer.commit()
        writer.close()

        assertQuery(Term("content", "ccc"), dir, 10)
        assertQuery(Term("content", "aaa"), dir, 10)
        var codec: Codec = iwconf.codec

        iwconf =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setOpenMode(OpenMode.APPEND)
                .setCodec(codec)
        iwconf.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)

        iwconf.setCodec(MockCodec()) // uses standard for field content
        writer = newWriter(dir, iwconf)
        // swap in new codec for currently written segments
        if (VERBOSE) {
            println("TEST: add docs w/ Standard codec for content field")
        }
        addDocs2(writer, 10)
        writer.commit()
        codec = iwconf.codec
        assertEquals(30, writer.getDocStats().maxDoc)
        assertQuery(Term("content", "bbb"), dir, 10)
        assertQuery(Term("content", "ccc"), dir, 10) // //
        assertQuery(Term("content", "aaa"), dir, 10)

        if (VERBOSE) {
            println("TEST: add more docs w/ new codec")
        }
        addDocs2(writer, 10)
        writer.commit()
        assertQuery(Term("content", "ccc"), dir, 10)
        assertQuery(Term("content", "bbb"), dir, 20)
        assertQuery(Term("content", "aaa"), dir, 10)
        assertEquals(40, writer.getDocStats().maxDoc)

        if (VERBOSE) {
            println("TEST: now optimize")
        }
        writer.forceMerge(1)
        assertEquals(40, writer.getDocStats().maxDoc)
        writer.close()
        assertQuery(Term("content", "ccc"), dir, 10)
        assertQuery(Term("content", "bbb"), dir, 20)
        assertQuery(Term("content", "aaa"), dir, 10)

        dir.close()
    }

    @Throws(IOException::class)
    fun assertQuery(t: Term, dir: Directory, num: Int) {
        if (VERBOSE) {
            println("\nTEST: assertQuery $t")
        }
        val reader: IndexReader = DirectoryReader.open(dir)
        val searcher: IndexSearcher = newSearcher(reader)
        val search: TopDocs = searcher.search(TermQuery(t), num + 10)
        assertEquals(num.toLong(), search.totalHits.value)
        reader.close()
    }

    class MockCodec : AssertingCodec() {
        val luceneDefault: PostingsFormat = TestUtil.getDefaultPostingsFormat()
        val direct: PostingsFormat = AssertingPostingsFormat()

        override fun getPostingsFormatForField(field: String): PostingsFormat {
            return if (field == "id") {
                direct
            } else {
                luceneDefault
            }
        }
    }

    /*
     * Test per field codec support - adding fields with random codecs
     */
    @Test
    @Throws(IOException::class)
    fun testStressPerFieldCodec() {
        val dir = newDirectory(random())
        val docsPerRound = 97
        val numRounds = atLeast(1)
        for (i in 0..<numRounds) {
            val num = TestUtil.nextInt(random(), 30, 60)
            val config = newIndexWriterConfig(random(), MockAnalyzer(random()))
            config.setOpenMode(OpenMode.CREATE_OR_APPEND)
            val writer = newWriter(dir, config)
            for (j in 0..<docsPerRound) {
                val doc = Document()
                for (k in 0..<num) {
                    val customType = FieldType(TextField.TYPE_NOT_STORED)
                    customType.setTokenized(random().nextBoolean())
                    customType.setOmitNorms(random().nextBoolean())
                    val field =
                        newField(
                            "$k",
                            TestUtil.randomRealisticUnicodeString(random(), 128),
                            customType
                        )
                    doc.add(field)
                }
                writer.addDocument(doc)
            }
            if (random().nextBoolean()) {
                writer.forceMerge(1)
            }
            writer.commit()
            assertEquals((i + 1) * docsPerRound, writer.getDocStats().maxDoc)
            writer.close()
        }
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSameCodecDifferentInstance() {
        val codec: Codec =
            object : AssertingCodec() {
                override fun getPostingsFormatForField(field: String): PostingsFormat {
                    return if ("id" == field) {
                        AssertingPostingsFormat()
                    } else if ("date" == field) {
                        AssertingPostingsFormat()
                    } else {
                        super.getPostingsFormatForField(field)
                    }
                }
            }
        doTestMixedPostings(codec)
    }

    @Test
    @Throws(Exception::class)
    fun testSameCodecDifferentParams() {
        val codec: Codec =
            object : AssertingCodec() {
                override fun getPostingsFormatForField(field: String): PostingsFormat {
                    return if ("id" == field) {
                        ParamPostingsFormatWrapper(TestUtil.getDefaultPostingsFormat(), 1)
                    } else if ("date" == field) {
                        ParamPostingsFormatWrapper(TestUtil.getDefaultPostingsFormat(), 2)
                    } else {
                        super.getPostingsFormatForField(field)
                    }
                }
            }
        doTestMixedPostings(codec)
    }

    @Throws(Exception::class)
    private fun doTestMixedPostings(codec: Codec) {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig(MockAnalyzer(random()))
        iwc.setCodec(codec)
        val iw = RandomIndexWriter(random(), dir, iwc)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        // turn on vectors for the checkindex cross-check
        ft.setStoreTermVectors(true)
        ft.setStoreTermVectorOffsets(true)
        ft.setStoreTermVectorPositions(true)
        val idField = Field("id", "", ft)
        val dateField = Field("date", "", ft)
        doc.add(idField)
        doc.add(dateField)
        for (i in 0..<100) {
            idField.setStringValue((random().nextInt(50)).toString())
            dateField.setStringValue((random().nextInt(100)).toString())
            iw.addDocument(doc)
        }
        iw.close()
        dir.close() // checkindex
    }

    @Test
    @Throws(IOException::class)
    fun testMergeCalledOnTwoFormats() {
        val pf1 = MergeRecordingPostingsFormatWrapper(TestUtil.getDefaultPostingsFormat())
        val pf2 = MergeRecordingPostingsFormatWrapper(TestUtil.getDefaultPostingsFormat())

        val iwc = IndexWriterConfig()
        iwc.setCodec(
            object : AssertingCodec() {
                override fun getPostingsFormatForField(field: String): PostingsFormat {
                    return when (field) {
                        "f1", "f2" -> pf1
                        "f3", "f4" -> pf2
                        else -> super.getPostingsFormatForField(field)
                    }
                }
            }
        )

        val directory = newDirectory()

        val iwriter = IndexWriter(directory, iwc)

        var doc = Document()
        doc.add(StringField("f1", "val1", Field.Store.NO))
        doc.add(StringField("f2", "val2", Field.Store.YES))
        doc.add(IntPoint("f3", 3)) // Points are not indexed as postings and should not appear in the merge fields
        doc.add(StringField("f4", "val4", Field.Store.NO))
        iwriter.addDocument(doc)
        iwriter.commit()

        doc = Document()
        doc.add(StringField("f1", "val5", Field.Store.NO))
        doc.add(StringField("f2", "val6", Field.Store.YES))
        doc.add(IntPoint("f3", 7))
        doc.add(StringField("f4", "val8", Field.Store.NO))
        iwriter.addDocument(doc)
        iwriter.commit()

        iwriter.forceMerge(1, true)
        iwriter.close()

        assertEquals(1, pf1.nbMergeCalls)
        assertEquals(setOf("f1", "f2"), pf1.fieldNames.toSet())
        assertEquals(1, pf2.nbMergeCalls)
        assertEquals(listOf("f4"), pf2.fieldNames)

        directory.close()
    }

    private class MergeRecordingPostingsFormatWrapper(private val delegate: PostingsFormat) : PostingsFormat(delegate.name) {
        val fieldNames: MutableList<String> = ArrayList()
        var nbMergeCalls = 0

        @Throws(IOException::class)
        override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
            val consumer = delegate.fieldsConsumer(state)
            return object : FieldsConsumer() {
                @Throws(IOException::class)
                override fun write(fields: Fields, norms: NormsProducer?) {
                    consumer.write(fields, norms)
                }

                @Throws(IOException::class)
                override fun merge(mergeState: MergeState, norms: NormsProducer?) {
                    nbMergeCalls++
                    for (fi: FieldInfo in mergeState.mergeFieldInfos!!) {
                        fieldNames.add(fi.name)
                    }
                    consumer.merge(mergeState, norms)
                }

                @Throws(IOException::class)
                override fun close() {
                    consumer.close()
                }
            }
        }

        @Throws(IOException::class)
        override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
            return delegate.fieldsProducer(state)
        }
    }

    private class ParamPostingsFormatWrapper(private val delegate: PostingsFormat, private val param: Int) : PostingsFormat(delegate.name) {
        @Throws(IOException::class)
        override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
            return delegate.fieldsConsumer(state)
        }

        @Throws(IOException::class)
        override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
            return delegate.fieldsProducer(state)
        }
    }
}
