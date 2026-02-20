package org.gnit.lucenekmp.codecs.perfield

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingDocValuesFormat
import org.gnit.lucenekmp.tests.index.BaseDocValuesFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Basic tests of PerFieldDocValuesFormat */
class TestPerFieldDocValuesFormat : BaseDocValuesFormatTestCase() {
    private lateinit var testCodec: Codec

    @BeforeTest
    @Throws(Exception::class)
    override fun setUp() {
        testCodec = TestUtil.getDefaultCodec()
        super.setUp()
    }

    override val codec: Codec
        get() = testCodec

    // just a simple trivial test
    // TODO: we should come up with a test that somehow checks that segment suffix
    // is respected by all codec apis (not just docvalues and postings)
    @Test
    @Throws(IOException::class)
    fun testTwoFieldsTwoFormats() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
        val iwc: IndexWriterConfig = newIndexWriterConfig(analyzer)
        val fast: DocValuesFormat = TestUtil.getDefaultDocValuesFormat()
        val slow: DocValuesFormat = AssertingDocValuesFormat()
        iwc.setCodec(
            object : AssertingCodec() {
                override fun getDocValuesFormatForField(field: String): DocValuesFormat {
                    return if ("dv1" == field) {
                        fast
                    } else {
                        slow
                    }
                }
            }
        )
        val iwriter = IndexWriter(directory, iwc)
        val doc = Document()
        val longTerm =
            "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm" +
                "longtermlongtermlongtermlongtermlongtermlongtermlong" +
                "termlongtermlongtermlongterm"
        val text = "This is the text to be indexed. $longTerm"
        doc.add(newTextField("fieldname", text, Field.Store.YES))
        doc.add(NumericDocValuesField("dv1", 5))
        doc.add(BinaryDocValuesField("dv2", BytesRef("hello world")))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = DirectoryReader.open(directory) // read-only=true
        val isearcher: IndexSearcher = newSearcher(ireader)

        assertEquals(1, isearcher.count(TermQuery(Term("fieldname", longTerm))))
        val query: Query = TermQuery(Term("fieldname", "text"))
        val hits: TopDocs = isearcher.search(query, 1)
        assertEquals(1, hits.totalHits.value)
        val storedFields: StoredFields = isearcher.storedFields()
        // Iterate through the results:
        for (i in hits.scoreDocs.indices) {
            val hitDocID = hits.scoreDocs[i].doc
            val hitDoc = storedFields.document(hitDocID)
            assertEquals(text, hitDoc.get("fieldname"))
            assert(ireader.leaves().size == 1)
            val dv: NumericDocValues = ireader.leaves()[0].reader().getNumericDocValues("dv1")!!
            assertEquals(hitDocID.toLong(), dv.advance(hitDocID).toLong())
            assertEquals(5, dv.longValue())

            val dv2: BinaryDocValues = ireader.leaves()[0].reader().getBinaryDocValues("dv2")!!
            assertEquals(hitDocID.toLong(), dv2.advance(hitDocID).toLong())
            val term: BytesRef? = dv2.binaryValue()
            assertEquals(BytesRef("hello world"), term)
        }

        ireader.close()
        directory.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMergeCalledOnTwoFormats() {
        val dvf1 = MergeRecordingDocValueFormatWrapper(TestUtil.getDefaultDocValuesFormat())
        val dvf2 = MergeRecordingDocValueFormatWrapper(TestUtil.getDefaultDocValuesFormat())

        val iwc = IndexWriterConfig()
        iwc.setCodec(
            object : AssertingCodec() {
                override fun getDocValuesFormatForField(field: String): DocValuesFormat {
                    return when (field) {
                        "dv1", "dv2" -> dvf1
                        "dv3" -> dvf2
                        else -> super.getDocValuesFormatForField(field)
                    }
                }
            }
        )

        val directory = newDirectory()

        val iwriter = IndexWriter(directory, iwc)

        var doc = Document()
        doc.add(NumericDocValuesField("dv1", 5))
        doc.add(NumericDocValuesField("dv2", 42))
        doc.add(BinaryDocValuesField("dv3", BytesRef("hello world")))
        iwriter.addDocument(doc)
        iwriter.commit()

        doc = Document()
        doc.add(NumericDocValuesField("dv1", 8))
        doc.add(NumericDocValuesField("dv2", 45))
        doc.add(BinaryDocValuesField("dv3", BytesRef("goodbye world")))
        iwriter.addDocument(doc)
        iwriter.commit()

        iwriter.forceMerge(1, true)
        iwriter.close()

        assertEquals(1, dvf1.nbMergeCalls)
        assertEquals(setOf("dv1", "dv2"), dvf1.fieldNames.toSet())
        assertEquals(1, dvf2.nbMergeCalls)
        assertEquals(listOf("dv3"), dvf2.fieldNames)

        directory.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDocValuesMergeWithIndexedFields() {
        val docValuesFormat = MergeRecordingDocValueFormatWrapper(TestUtil.getDefaultDocValuesFormat())

        val iwc = IndexWriterConfig()
        iwc.setCodec(
            object : AssertingCodec() {
                override fun getDocValuesFormatForField(field: String): DocValuesFormat {
                    return docValuesFormat
                }
            }
        )

        val directory = newDirectory()

        val iwriter = IndexWriter(directory, iwc)

        var doc = Document()
        doc.add(NumericDocValuesField("dv1", 5))
        doc.add(TextField("normalField", "not a doc value", Field.Store.NO))
        iwriter.addDocument(doc)
        iwriter.commit()

        doc = Document()
        doc.add(TextField("anotherField", "again no doc values here", Field.Store.NO))
        doc.add(TextField("normalField", "my document without doc values", Field.Store.NO))
        iwriter.addDocument(doc)
        iwriter.commit()

        iwriter.forceMerge(1, true)
        iwriter.close()

        // "normalField" and "anotherField" are ignored when merging doc values.
        assertEquals(1, docValuesFormat.nbMergeCalls)
        assertEquals(listOf("dv1"), docValuesFormat.fieldNames)
        directory.close()
    }

    private class MergeRecordingDocValueFormatWrapper(private val delegate: DocValuesFormat) : DocValuesFormat(delegate.name) {
        val fieldNames: MutableList<String> = ArrayList()
        
        var nbMergeCalls = 0

        @Throws(IOException::class)
        override fun fieldsConsumer(state: SegmentWriteState): DocValuesConsumer {
            val consumer = delegate.fieldsConsumer(state)
            return object : DocValuesConsumer() {
                @Throws(IOException::class)
                override fun addNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
                    consumer.addNumericField(field, valuesProducer)
                }

                @Throws(IOException::class)
                override fun addBinaryField(field: FieldInfo, valuesProducer: DocValuesProducer) {
                    consumer.addBinaryField(field, valuesProducer)
                }

                @Throws(IOException::class)
                override fun addSortedField(field: FieldInfo, valuesProducer: DocValuesProducer) {
                    consumer.addSortedField(field, valuesProducer)
                }

                @Throws(IOException::class)
                override fun addSortedNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
                    consumer.addSortedNumericField(field, valuesProducer)
                }

                @Throws(IOException::class)
                override fun addSortedSetField(field: FieldInfo, valuesProducer: DocValuesProducer) {
                    consumer.addSortedSetField(field, valuesProducer)
                }

                @Throws(IOException::class)
                override fun merge(mergeState: MergeState) {
                    nbMergeCalls++
                    for (fi in mergeState.mergeFieldInfos!!) {
                        fieldNames.add(fi.name)
                    }
                    consumer.merge(mergeState)
                }

                @Throws(IOException::class)
                override fun close() {
                    consumer.close()
                }
            }
        }

        @Throws(IOException::class)
        override fun fieldsProducer(state: SegmentReadState): DocValuesProducer {
            return delegate.fieldsProducer(state)
        }
    }
}
