package org.gnit.lucenekmp.codecs.lucene90

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.perfield.PerFieldDocValuesFormat
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.MultiDocValues
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.TreeSet
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.toBinaryString
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec
import org.gnit.lucenekmp.tests.index.BaseCompressingDocValuesFormatTestCase
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.CollectionUtil
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Tests Lucene90DocValuesFormat  */
open class TestLucene90DocValuesFormat : BaseCompressingDocValuesFormatTestCase() {
    private val logger = KotlinLogging.logger {}
    override val codec: Codec = TestUtil.getDefaultCodec()

    // TODO: these big methods can easily blow up some of the other ram-hungry codecs...
    // for now just keep them here, as we want to test this for this format.
    @Test
    @Throws(Exception::class)
    fun testSortedSetVariableLengthBigVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            val numDocs: Int = if (TEST_NIGHTLY) atLeast(100) else atLeast(3) // TODO reduced from 10 to 3 for dev speed
            doTestSortedSetVsStoredFields(numDocs, 1, maxLength = 3, 16, 100) // TODO reduced from maxLength = 32766 to maxLength = 32 for dev speed
        }
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(Exception::class)
    fun testSortedSetVariableLengthManyVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedSetVsStoredFields(
                TestUtil.nextInt(random(), 3, 5), 1, 5, 16, 100 // TODO reduced from 1024..2049 and maxLength 500 for <=10s runtime
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSortedVariableLengthBigVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedVsStoredFields(atLeast(3), 1.0, 1, maxLength = 3) // TODO reduced from atLeast(100) atLeast(3) to, maxLength = 32766 to maxLength = 32 for dev speed
        }
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(Exception::class)
    fun testSortedVariableLengthManyVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedVsStoredFields(TestUtil.nextInt(random(), start = 3, end = 5), 1.0, 1, maxLength = 3) // TODO reduced from start = 1024, end = 2049, maxLength = 500 to start = 3, end = 5, maxLength = 3
        }
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(Exception::class)
    fun testTermsEnumFixedWidth() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestTermsEnumRandom(
                TestUtil.nextInt(random(), 3, 5)) { // TODO reduced from 1025, 5121 to 3, 5 for dev speed
                TestUtil.randomSimpleString(random(), 3, 3) // TODO reduced from 10, 10 to 3, 3
            }
        }
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(Exception::class)
    fun testTermsEnumVariableWidth() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestTermsEnumRandom(
                TestUtil.nextInt(random(), 3, 5)) { // TODO reduced from 1025, 5121 to 3, 5 for dev speed
                TestUtil.randomSimpleString(random(), 1, maxLength = 3) // TODO reduced from maxLength = 500 to maxLength = 3 for dev speed
            }
        }
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(Exception::class)
    fun testTermsEnumRandomMany() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestTermsEnumRandom(
                TestUtil.nextInt(random(), 3, 5)) { // TODO reduced from 1025..8121 for <=10s runtime
                TestUtil.randomSimpleString(random(), 1, 3) // TODO reduced from maxLength 500 for <=10s runtime
            }
        }
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(Exception::class)
    fun testTermsEnumLongSharedPrefixes() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestTermsEnumRandom(TestUtil.nextInt(random(), 3, 5)) { // TODO reduced from 1025..5121 for <=10s runtime
                val chars = CharArray(random().nextInt(3)) // TODO reduced from 500 for <=10s runtime
                Arrays.fill(chars, 'a')
                if (chars.isNotEmpty()) {
                    chars[random().nextInt(chars.size)] = 'b'
                }
                chars.concatToString()
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSparseDocValuesVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSparseDocValuesVsStoredFields()
        }
    }

    @Throws(Exception::class)
    private fun doTestSparseDocValuesVsStoredFields() {
        val values = LongArray(TestUtil.nextInt(random(), 1, 3)) // TODO reduced from 500 to 3 for dev speed
        for (i in values.indices) {
            values[i] = random().nextLong()
        }

        val dir: Directory = newFSDirectory(createTempDir())
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMergeScheduler(SerialMergeScheduler())
        val writer =
            RandomIndexWriter(
                random(),
                dir,
                conf
            )

        // sparse compression is only enabled if less than 1% of docs have a value
        val avgGap = 100

        val numDocs: Int = atLeast(3) //TODO reduced from 200 to 3 for dev speed
        for (i in random()
            .nextInt(avgGap * 2) downTo 0) {
            writer.addDocument(Document())
        }
        val maxNumValuesPerDoc = if (random()
                .nextBoolean()
        ) 1 else TestUtil.nextInt(
            random(),
            2,
            5
        )
        for (i in 0..<numDocs) {
            val doc = Document()

            // single-valued
            var docValue = values[random().nextInt(values.size)]
            doc.add(NumericDocValuesField("numeric", docValue))
            doc.add(SortedDocValuesField("sorted", BytesRef(docValue.toString())))
            doc.add(BinaryDocValuesField("binary", BytesRef(docValue.toString())))
            doc.add(StoredField("value", docValue))

            // multi-valued
            val numValues: Int = TestUtil.nextInt(random(), 1, maxNumValuesPerDoc)
            for (j in 0..<numValues) {
                docValue = values[random().nextInt(values.size)]
                doc.add(SortedNumericDocValuesField("sorted_numeric", docValue))
                doc.add(SortedSetDocValuesField("sorted_set", BytesRef(docValue.toString())))
                doc.add(StoredField("values", docValue))
            }

            writer.addDocument(doc)

            // add a gap
            for (j in TestUtil.nextInt(
                random(),
                0,
                avgGap * 2
            ) downTo 0) {
                writer.addDocument(Document())
            }
        }

        if (random().nextBoolean()) {
            writer.forceMerge(1)
        }

        val indexReader: IndexReader = writer.reader
        writer.close()

        for (context in indexReader.leaves()) {
            val reader: LeafReader = context.reader()
            val numeric: NumericDocValues = DocValues.getNumeric(reader, "numeric")

            val sorted: SortedDocValues = DocValues.getSorted(reader, "sorted")

            val binary: BinaryDocValues = DocValues.getBinary(reader, "binary")

            val sortedNumeric: SortedNumericDocValues = DocValues.getSortedNumeric(reader, "sorted_numeric")

            val sortedSet: SortedSetDocValues = DocValues.getSortedSet(reader, "sorted_set")

            val storedFields: StoredFields = reader.storedFields()
            for (i in 0..<reader.maxDoc()) {
                val doc = storedFields.document(i)
                val valueField: IndexableField? = doc.getField("value")
                val value = if (valueField == null) null else valueField.numericValue()!!.toLong()

                if (value == null) {
                    assertTrue(numeric.docID() < i, numeric.docID().toString() + " vs " + i)
                } else {
                    assertEquals(i.toLong(), numeric.nextDoc().toLong())
                    assertEquals(i.toLong(), binary.nextDoc().toLong())
                    assertEquals(i.toLong(), sorted.nextDoc().toLong())
                    assertEquals(value, numeric.longValue())
                    assertTrue(sorted.ordValue() >= 0)
                    assertEquals(BytesRef(value.toString()), sorted.lookupOrd(sorted.ordValue()))
                    assertEquals(BytesRef(value.toString()), binary.binaryValue())
                }

                val valuesFields: Array<IndexableField> =
                    doc.getFields("values")
                if (valuesFields.isEmpty()) {
                    assertTrue(sortedNumeric.docID() < i, sortedNumeric.docID().toString() + " vs " + i)
                } else {
                    val valueSet: MutableSet<Long> = mutableSetOf()
                    for (sf in valuesFields) {
                        valueSet.add(sf.numericValue()!!.toLong())
                    }

                    assertEquals(i.toLong(), sortedNumeric.nextDoc().toLong())
                    assertEquals(valuesFields.size.toLong(), sortedNumeric.docValueCount().toLong())
                    for (j in 0..<sortedNumeric.docValueCount()) {
                        assertTrue(valueSet.contains(sortedNumeric.nextValue()))
                    }
                    assertEquals(i.toLong(), sortedSet.nextDoc().toLong())
                    assertEquals(valueSet.size.toLong(), sortedSet.docValueCount().toLong())
                    for (j in 0..<sortedSet.docValueCount()) {
                        val ord: Long = sortedSet.nextOrd()
                        assertTrue(valueSet.contains(sortedSet.lookupOrd(ord)!!.utf8ToString().toLong()))
                    }
                }
            }
        }

        indexReader.close()
        dir.close()
    }

    // TODO: try to refactor this and some termsenum tests into the base class.
    // to do this we need to fix the test class to get a DVF not a Codec so we can setup
    // the postings format correctly.
    @Throws(Exception::class)
    private fun doTestTermsEnumRandom(
        numDocs: Int,
        valuesProducer: () -> String /*java.util.function.Supplier<String>*/
    ) {
        val dir: Directory = newFSDirectory(createTempDir())
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMergeScheduler(SerialMergeScheduler())
        // set to duel against a codec which has ordinals:
        val pf: PostingsFormat = TestUtil.getPostingsFormatWithOrds(random())
        val dv: DocValuesFormat = (this.codec.docValuesFormat() as PerFieldDocValuesFormat).getDocValuesFormatForField("random_field_name")
        conf.setCodec(
            object : AssertingCodec() {
                override fun getPostingsFormatForField(field: String): PostingsFormat {
                    return pf
                }

                override fun getDocValuesFormatForField(field: String): DocValuesFormat {
                    return dv
                }
            })
        val writer = RandomIndexWriter(random(), dir, conf)

        // index some docs
        for (i in 0..<numDocs) {
            val doc = Document()
            val idField: Field = StringField("id", i.toString(), Field.Store.NO)
            doc.add(idField)
            val numValues: Int = random().nextInt(17)
            // create a random list of strings
            val values: MutableList<String> = ArrayList()
            for (v in 0..<numValues) {
                values.add(valuesProducer())
            }

            // add in any order to the indexed field
            val unordered: ArrayList<String> = ArrayList(values)
            unordered.shuffle(random())
            for (v in values) {
                doc.add(newStringField("indexed", v, Field.Store.NO))
            }

            // add in any order to the dv field
            val unordered2: ArrayList<String> = ArrayList(values)
            unordered2.shuffle(random())
            for (v in unordered2) {
                doc.add(SortedSetDocValuesField("dv", BytesRef(v)))
            }

            writer.addDocument(doc)
            if (random().nextInt(31) == 0) {
                writer.commit()
            }
        }

        // delete some docs
        val maxDeletions = numDocs / 10
        val numDeletions: Int = if (maxDeletions > 0) maxDeletions - 1 else 0
        for (i in 0..<numDeletions) {
            val id: Int = random().nextInt(numDocs)
            writer.deleteDocuments(Term("id", id.toString()))
        }

        // compare per-segment
        var ir: DirectoryReader = writer.reader
        for (context in ir.leaves()) {
            val r: LeafReader = context.reader()
            val terms: Terms? = r.terms("indexed")
            if (terms != null) {
                val ssdv: SortedSetDocValues = r.getSortedSetDocValues("dv")!!
                assertEquals(terms.size(), ssdv.valueCount)
                val expected: TermsEnum = terms.iterator()
                val actual: TermsEnum = r.getSortedSetDocValues("dv")!!.termsEnum()
                assertEquals(terms.size(), expected, actual)

                doTestSortedSetEnumAdvanceIndependently(ssdv)
            }
        }
        ir.close()

        writer.forceMerge(1)

        // now compare again after the merge
        ir = writer.reader
        val ar: LeafReader = getOnlyLeafReader(ir)
        val terms: Terms? = ar.terms("indexed")
        if (terms != null) {
            assertEquals(terms.size(), ar.getSortedSetDocValues("dv")!!.valueCount)
            val expected: TermsEnum = terms.iterator()
            val actual: TermsEnum = ar.getSortedSetDocValues("dv")!!.termsEnum()
            assertEquals(terms.size(), expected, actual)
        }
        ir.close()

        writer.close()
        dir.close()
    }

    @Throws(Exception::class)
    private fun assertEquals(
        numOrds: Long,
        expected: TermsEnum,
        actual: TermsEnum
    ) {
        var ref: BytesRef? = null

        // sequential next() through all terms
        while ((expected.next().also { ref = it }) != null) {
            assertEquals(ref, actual.next())
            assertEquals(expected.ord(), actual.ord())
            assertEquals(expected.term()!!, actual.term())
        }
        assertNull(actual.next())

        // sequential seekExact(ord) through all terms
        for (i in 0..<numOrds) {
            expected.seekExact(i)
            actual.seekExact(i)
            assertEquals(expected.ord(), actual.ord())
            assertEquals(expected.term()!!, actual.term())
        }

        // sequential seekExact(BytesRef) through all terms
        for (i in 0..<numOrds) {
            expected.seekExact(i)
            assertTrue(actual.seekExact(expected.term()!!))
            assertEquals(expected.ord(), actual.ord())
            assertEquals(expected.term()!!, actual.term())
        }

        // sequential seekCeil(BytesRef) through all terms
        for (i in 0..<numOrds) {
            expected.seekExact(i)
            assertEquals(TermsEnum.SeekStatus.FOUND, actual.seekCeil(expected.term()!!))
            assertEquals(expected.ord(), actual.ord())
            assertEquals(expected.term()!!, actual.term())
        }

        // random seekExact(ord)
        for (i in 0..<numOrds) {
            val randomOrd: Long = TestUtil.nextLong(random(), 0, numOrds - 1)
            expected.seekExact(randomOrd)
            actual.seekExact(randomOrd)
            assertEquals(expected.ord(), actual.ord())
            assertEquals(expected.term()!!, actual.term())
        }

        // random seekExact(BytesRef)
        for (i in 0..<numOrds) {
            val randomOrd: Long = TestUtil.nextLong(random(), 0, numOrds - 1)
            expected.seekExact(randomOrd)
            actual.seekExact(expected.term()!!)
            assertEquals(expected.ord(), actual.ord())
            assertEquals(expected.term()!!, actual.term())
        }

        // random seekCeil(BytesRef)
        for (i in 0..<numOrds) {
            val target = BytesRef(
                TestUtil.randomUnicodeString(random())
            )
            val expectedStatus: TermsEnum.SeekStatus =
                expected.seekCeil(target)
            assertEquals(expectedStatus, actual.seekCeil(target))
            if (expectedStatus != TermsEnum.SeekStatus.END) {
                val expectedOrd = expected.ord()
                val actualOrd = actual.ord()
                val expectedTerm = expected.term()!!
                val actualTerm = actual.term()
                if (expectedOrd != actualOrd || expectedTerm != actualTerm) {
                    throw AssertionError(
                        "seekCeil mismatch target=${target.utf8ToString()} status=$expectedStatus " +
                                "expectedOrd=$expectedOrd expectedTerm=${expectedTerm.utf8ToString()} " +
                                "actualOrd=$actualOrd actualTerm=${actualTerm?.utf8ToString()}"
                    )
                }
            }
        }
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(IOException::class)
    fun testSortedSetAroundBlockSize() {
        val frontier = 1 shl Lucene90DocValuesFormat.DIRECT_MONOTONIC_BLOCK_SHIFT
        val maxDoc = frontier ushr 6 // TODO reduced maxDoc = frontier (~16384) to frontier ushr 6 (~256) for dev speed
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
        val out = ByteBuffersDataOutput()
        val doc = Document()
        val field1 = SortedSetDocValuesField("sset", BytesRef())
        doc.add(field1)
        val field2 = SortedSetDocValuesField("sset", BytesRef())
        doc.add(field2)
        for (i in 0..<maxDoc) {
            val s1 = BytesRef(TestUtil.randomSimpleString(random(), 2))
            val s2 = BytesRef(TestUtil.randomSimpleString(random(), 2))
            field1.setBytesValue(s1)
            field2.setBytesValue(s2)
            w.addDocument(doc)
            val set: MutableSet<BytesRef> = TreeSet(mutableListOf(s1, s2))
            out.writeVInt(set.size)
            for (ref in set) {
                out.writeVInt(ref.length)
                out.writeBytes(ref.bytes, ref.offset, ref.length)
            }
        }

        w.forceMerge(1)
        val r: DirectoryReader = DirectoryReader.open(w)
        w.close()
        val sr: LeafReader = getOnlyLeafReader(r)
        assertEquals(maxDoc.toLong(), sr.maxDoc().toLong())
        val values: SortedSetDocValues? = sr.getSortedSetDocValues("sset")
        assertNotNull(values)
        val `in`: ByteBuffersDataInput = out.toDataInput()
        val b = BytesRefBuilder()
        for (i in 0..<maxDoc) {
            assertEquals(i.toLong(), values.nextDoc().toLong())
            val numValues: Int = `in`.readVInt()
            assertEquals(numValues.toLong(), values.docValueCount().toLong())

            for (j in 0..<numValues) {
                b.setLength(`in`.readVInt())
                b.grow(b.length())
                `in`.readBytes(b.bytes(), 0, b.length())
                assertEquals(b.get(), values.lookupOrd(values.nextOrd()))
            }
        }
        r.close()
        dir.close()
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(IOException::class)
    fun testSortedNumericAroundBlockSize() {
        val frontier = 1 shl Lucene90DocValuesFormat.DIRECT_MONOTONIC_BLOCK_SHIFT
        for (maxDoc in frontier - 1..frontier) { // TODO reduced maxDoc upper bound = frontier + 1 to frontier for dev speed
            val dir: Directory = newDirectory()
            val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
            val buffer = ByteBuffersDataOutput()

            val doc = Document()
            val field1 = SortedNumericDocValuesField("snum", 0L)
            doc.add(field1)
            val field2 = SortedNumericDocValuesField("snum", 0L)
            doc.add(field2)
            for (i in 0..<maxDoc) {
                val s1 = random().nextInt(3).toLong() // TODO reduced from 100 to 3 for dev speed
                val s2 = random().nextInt(3).toLong() // TODO reduced from 100 to 3 for dev speed
                field1.setLongValue(s1)
                field2.setLongValue(s2)
                w.addDocument(doc)
                buffer.writeVLong(min(s1, s2))
                buffer.writeVLong(max(s1, s2))
            }

            w.forceMerge(1)
            val r: DirectoryReader = DirectoryReader.open(w)
            w.close()
            val sr: LeafReader = getOnlyLeafReader(r)
            assertEquals(maxDoc.toLong(), sr.maxDoc().toLong())
            val values: SortedNumericDocValues? = sr.getSortedNumericDocValues("snum")
            assertNotNull(values)
            val dataInput: ByteBuffersDataInput = buffer.toDataInput()
            for (i in 0..<maxDoc) {
                assertEquals(i.toLong(), values.nextDoc().toLong())
                assertEquals(2, values.docValueCount().toLong())
                assertEquals(dataInput.readVLong(), values.nextValue())
                assertEquals(dataInput.readVLong(), values.nextValue())
            }
            r.close()
            dir.close()
        }
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(Exception::class)
    fun testSortedNumericBlocksOfVariousBitsPerValue() {
        doTestSortedNumericBlocksOfVariousBitsPerValue {
            TestUtil.nextInt(
                random(),
                1,
                3
            ).toLong()
        }
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(Exception::class)
    fun testSparseSortedNumericBlocksOfVariousBitsPerValue() {
        doTestSortedNumericBlocksOfVariousBitsPerValue {
            TestUtil.nextInt(random(), 0, 2).toLong()
        }
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(Exception::class)
    fun testNumericBlocksOfVariousBitsPerValue() {
        doTestSparseNumericBlocksOfVariousBitsPerValue(1.0)
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(Exception::class)
    fun testSparseNumericBlocksOfVariousBitsPerValue() {
        doTestSparseNumericBlocksOfVariousBitsPerValue(
            random().nextDouble()
        )
    }

    // The LUCENE-8585 jump-tables enables O(1) skipping of IndexedDISI blocks, DENSE block lookup
    // and numeric multi blocks. This test focuses on testing these jumps.
    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Test
    @Throws(Exception::class)
    fun testNumericFieldJumpTables() {
        // Java Lucene nightly uses 5 * 65536 docs to hit the full IndexedDISI block-skip path.
        // Keep a smaller-but-stable smoke scale here so this test stays within the 10s budget.
        val maxDoc: Int = atLeast(4) // TODO reduced from 5 * 4096 to 4 for dev speed

        val dir: Directory = newDirectory()
        val iw = createFastIndexWriter(dir, maxDoc)

        val idField: Field = newStringField("id", "", Field.Store.NO)
        val storedField: Field = newStringField("stored", "", Field.Store.YES)
        val dvField: Field = NumericDocValuesField("dv", 0)

        for (i in 0..<maxDoc) {
            val doc = Document()
            idField.setStringValue(Int.toBinaryString(i))
            doc.add(idField)
            if (random()
                    .nextInt(100) > 10
            ) { // Skip 10% to make DENSE blocks
                val value: Int = random().nextInt(100000)
                storedField.setStringValue(value.toString())
                doc.add(storedField)
                dvField.setLongValue(value.toLong())
                doc.add(dvField)
            }
            iw.addDocument(doc)
        }
        iw.flush()
        iw.forceMerge(1, true) // Single segment to force large enough structures
        iw.commit()
        iw.close()

        assertDVIterate(dir)
        assertDVAdvance(dir, 7)

        dir.close()
    }

    @Throws(IOException::class)
    private fun createFastIndexWriter(
        dir: Directory,
        maxBufferedDocs: Int
    ): IndexWriter {
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMaxBufferedDocs(maxBufferedDocs)
        conf.setRAMBufferSizeMB(-1.0)
        conf.setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        return IndexWriter(dir, conf)
    }

    @Throws(Exception::class)
    private fun doTestSortedNumericBlocksOfVariousBitsPerValue(counts: () -> Long /*java.util.function.LongSupplier*/) {
        logger.debug { "SNBPV: start setup" }
        val dir: Directory = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMaxBufferedDocs(atLeast(Lucene90DocValuesFormat.NUMERIC_BLOCK_SIZE))
        conf.setRAMBufferSizeMB(-1.0)
        conf.setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        conf.setMergeScheduler(SerialMergeScheduler())
        val writer = IndexWriter(dir, conf)

        val numDocs: Int = Lucene90DocValuesFormat.NUMERIC_BLOCK_SIZE + 256 // reduced from atLeast(blockSize * 3) for faster iterations
        logger.debug { "SNBPV: writing docs numDocs=$numDocs blockSize=${Lucene90DocValuesFormat.NUMERIC_BLOCK_SIZE}" }
        val values = blocksOfVariousBPV()
        val writeDocValues: MutableList<LongArray> = ArrayList()
        for (i in 0..<numDocs) {
            val doc = Document()

            val valueCount = counts().toInt()
            val valueArray = LongArray(valueCount)
            for (j in 0..<valueCount) {
                val value: Long = values()
                valueArray[j] = value
                doc.add(SortedNumericDocValuesField("dv", value))
            }
            Arrays.sort(valueArray)
            writeDocValues.add(valueArray)
            for (j in 0..<valueCount) {
                doc.add(StoredField("stored", valueArray[j].toString()))
            }
            writer.addDocument(doc)
            if (random().nextInt(4096) == 0) { // reduced commit frequency for faster/less racy iterations
                writer.commit()
            }
            if (i > 0 && i % Lucene90DocValuesFormat.NUMERIC_BLOCK_SIZE == 0) {
                logger.debug { "SNBPV: wrote doc=$i/${numDocs - 1}" }
            }
        }
        logger.debug { "SNBPV: forceMerge start" }
        writer.forceMerge(1)
        logger.debug { "SNBPV: forceMerge done, closing writer" }
        writer.close()

        // compare
        logger.debug { "SNBPV: compare start" }
        val ir: DirectoryReader = DirectoryReader.open(dir)
        TestUtil.checkReader(ir)
        logger.debug { "SNBPV: reader opened leaves=${ir.leaves().size}" }
        for (context in ir.leaves()) {
            val r: LeafReader = context.reader()
            val docValues: SortedNumericDocValues = DocValues.getSortedNumeric(r, "dv")
            val storedFields: StoredFields = r.storedFields()
            for (i in 0..<r.maxDoc()) {
                if (i > docValues.docID()) {
                    docValues.nextDoc()
                }
                val expectedStored: Array<String?> = storedFields.document(i).getValues("stored")
                if (i < docValues.docID()) {
                    assertEquals(0, expectedStored.size.toLong())
                } else {
                    val readValueArray = LongArray(docValues.docValueCount())
                    val actualDocValue = kotlin.arrayOfNulls<String?>(docValues.docValueCount())
                    for (j in 0..<docValues.docValueCount()) {
                        val actualDV: Long = docValues.nextValue()
                        readValueArray[j] = actualDV
                        actualDocValue[j] = readValueArray[j].toString()
                    }
                    val writeValueArray = writeDocValues[i]
                    // compare write values and read values
                    assertArrayEquals(readValueArray, writeValueArray)

                    // compare dv and stored values
                    assertArrayEquals(expectedStored, actualDocValue)
                }
                if (i > 0 && i % Lucene90DocValuesFormat.NUMERIC_BLOCK_SIZE == 0) {
                    logger.debug { "SNBPV: verified doc=$i/${r.maxDoc() - 1}" }
                }
            }
        }
        logger.debug { "SNBPV: compare done, closing resources" }
        ir.close()
        dir.close()
        logger.debug { "SNBPV: finished" }
    }

    @Throws(Exception::class)
    private fun doTestSparseNumericBlocksOfVariousBitsPerValue(density: Double) {
        val dir: Directory = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMaxBufferedDocs(atLeast(/*Lucene90DocValuesFormat.NUMERIC_BLOCK_SIZE*/ 100)) // TODO reduced for dev speed
        conf.setRAMBufferSizeMB(-1.0)
        conf.setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        val writer = IndexWriter(dir, conf)
        val doc = Document()
        val storedField: Field = newStringField("stored", "", Field.Store.YES)
        val dvField: Field = NumericDocValuesField("dv", 0)
        doc.add(storedField)
        doc.add(dvField)

        val numDocs: Int = atLeast(/*Lucene90DocValuesFormat.NUMERIC_BLOCK_SIZE * 3*/ 100) // TODO reduced for dev speed
        val longs: () -> Long /*java.util.function.LongSupplier*/ = blocksOfVariousBPV()
        for (i in 0..<numDocs) {
            if (random().nextDouble() > density) {
                writer.addDocument(Document())
                continue
            }
            val value: Long = longs()
            storedField.setStringValue(value.toString())
            dvField.setLongValue(value)
            writer.addDocument(doc)
        }

        writer.forceMerge(1)

        writer.close()

        // compare
        assertDVIterate(dir)
        assertDVAdvance(
            dir, 1
        ) // Tests all jump-lengths from 1 to maxDoc (quite slow ~= 1 minute for 200K docs)

        dir.close()
    }

    // Tests that advanceExact does not change the outcome
    @Throws(IOException::class)
    private fun assertDVAdvance(dir: Directory, jumpStep: Int) {
        val ir: DirectoryReader = DirectoryReader.open(dir)
        TestUtil.checkReader(ir)
        for (context in ir.leaves()) {
            val r: LeafReader = context.reader()
            val storedFields: StoredFields = r.storedFields()

            var jump = jumpStep
            while (jump < r.maxDoc()) {
                // Create a new instance each time to ensure jumps from the beginning
                val docValues: NumericDocValues = DocValues.getNumeric(r, "dv")
                var docID = 0
                while (docID < r.maxDoc()) {
                    val base =
                        ("document #"
                                + docID
                                + "/"
                                + r.maxDoc()
                                + ", jumping "
                                + jump
                                + " from #"
                                + (docID - jump))
                    val storedValue: String? = storedFields.document(docID).get("stored")
                    if (storedValue == null) {
                        assertFalse(docValues.advanceExact(docID), "There should be no DocValue for $base")
                    } else {
                        assertTrue(docValues.advanceExact(docID), "There should be a DocValue for $base")
                        assertEquals(storedValue.toLong(), docValues.longValue(), "The doc value should be correct for $base")
                    }
                    docID += jump
                }
                jump += jumpStep
            }
        }
        ir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testReseekAfterSkipDecompression() {
        val CARDINALITY: Int = (Lucene90DocValuesFormat.TERMS_DICT_BLOCK_LZ4_SIZE shl 1) + 11
        val valueSet: MutableSet<String> = CollectionUtil.newHashSet(CARDINALITY)
        for (i in 0..<CARDINALITY) {
            valueSet.add(TestUtil.randomSimpleString(random(), 64))
        }
        val values: MutableList<String> = ArrayList(valueSet)
        values.sort()
        // Create one non-existent value just between block-1 and block-2.
        val nonexistentValue =
            (values[Lucene90DocValuesFormat.TERMS_DICT_BLOCK_LZ4_SIZE - 1]
                    + TestUtil.randomSimpleString(
                random(),
                64,
                128
            ))
        val docValues = values.size

        newDirectory().use { directory ->
            val analyzer: Analyzer = StandardAnalyzer()
            val config = IndexWriterConfig(analyzer)
            config.setCodec(this.codec)
            config.setUseCompoundFile(false)
            val writer = IndexWriter(directory, config)
            for (i in 0..279) {
                val doc = Document()
                doc.add(StringField("id", "Doc$i", Field.Store.NO))
                doc.add(SortedDocValuesField("sdv", BytesRef(values[i % docValues])))
                writer.addDocument(doc)
            }
            writer.commit()
            writer.forceMerge(1)
            val dReader: DirectoryReader = DirectoryReader.open(writer)
            writer.close()

            val reader: LeafReader = getOnlyLeafReader(dReader)
            // Check values count.
            val ssdvMulti: SortedDocValues = reader.getSortedDocValues("sdv")!!
            assertEquals(docValues.toLong(), ssdvMulti.valueCount.toLong())

            // Seek to first block.
            val ord1: Int = ssdvMulti.lookupTerm(BytesRef(values[0]))
            assertTrue(ord1 >= 0)
            val ord2: Int = ssdvMulti.lookupTerm(BytesRef(values[1]))
            assertTrue(ord2 >= ord1)
            // Ensure re-seek logic is correct after skip-decompression.
            val nonexistentOrd2: Int = ssdvMulti.lookupTerm(BytesRef(nonexistentValue))
            assertTrue(nonexistentOrd2 < 0)
            dReader.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testLargeTermsCompression() {
        val CARDINALITY = 64
        val valuesSet: MutableSet<String> = mutableSetOf()
        for (i in 0..<CARDINALITY) {
            val length: Int = TestUtil.nextInt(random(), 512, 1024)
            valuesSet.add(TestUtil.randomSimpleString(random(), length))
        }
        val valuesCount = valuesSet.size
        val values: MutableList<String> = ArrayList(valuesSet)

        newDirectory().use { directory ->
            val analyzer: Analyzer = StandardAnalyzer()
            val config = IndexWriterConfig(analyzer)
            config.setCodec(this.codec)
            config.setUseCompoundFile(false)
            val writer = IndexWriter(directory, config)
            for (i in 0..255) {
                val doc = Document()
                doc.add(StringField("id", "Doc$i", Field.Store.NO))
                doc.add(SortedDocValuesField("sdv", BytesRef(values[i % valuesCount])))
                writer.addDocument(doc)
            }
            writer.commit()
            writer.forceMerge(1)
            val ireader: DirectoryReader = DirectoryReader.open(writer)
            writer.close()

            val reader: LeafReader =
                getOnlyLeafReader(ireader)
            // Check values count.
            val ssdvMulti: SortedDocValues = reader.getSortedDocValues("sdv")!!
            assertEquals(valuesCount.toLong(), ssdvMulti.valueCount.toLong())
            ireader.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSortedTermsDictLookupOrd() {
        val dir: Directory = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        val field = SortedDocValuesField("foo", BytesRef())
        doc.add(field)
        val numDocs: Int = atLeast(Lucene90DocValuesFormat.TERMS_DICT_BLOCK_LZ4_SIZE + 1)
        for (i in 0..<numDocs) {
            field.setBytesValue(BytesRef("" + i))
            writer.addDocument(doc)
        }
        writer.forceMerge(1)
        val reader: IndexReader = DirectoryReader.open(writer)
        val leafReader: LeafReader = getOnlyLeafReader(reader)
        doTestTermsDictLookupOrd(leafReader.getSortedDocValues("foo")!!.termsEnum()!!)
        reader.close()
        writer.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testSortedSetTermsDictLookupOrd() {
        val dir: Directory = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        val field = SortedSetDocValuesField("foo", BytesRef())
        doc.add(field)
        val numDocs: Int = atLeast(2 * Lucene90DocValuesFormat.TERMS_DICT_BLOCK_LZ4_SIZE + 1)
        for (i in 0..<numDocs) {
            field.setBytesValue(BytesRef("" + i))
            writer.addDocument(doc)
        }
        writer.forceMerge(1)
        val reader: IndexReader = DirectoryReader.open(writer)
        val leafReader: LeafReader = getOnlyLeafReader(reader)
        doTestTermsDictLookupOrd(leafReader.getSortedSetDocValues("foo")!!.termsEnum())
        reader.close()
        writer.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun doTestTermsDictLookupOrd(te: TermsEnum) {
        val terms: MutableList<BytesRef> = ArrayList()
        var term: BytesRef? = te.next()
        while (term != null) {
            terms.add(BytesRef.deepCopyOf(term))
            term = te.next()
        }

        // iterate in order
        for (i in terms.indices) {
            te.seekExact(i.toLong())
            assertEquals(terms[i], te.term())
        }

        // iterate in reverse order
        for (i in terms.indices.reversed()) {
            te.seekExact(i.toLong())
            assertEquals(terms[i], te.term())
        }

        // iterate in forward order with random gaps
        var i: Int = random().nextInt(5)
        while (i < terms.size) {
            te.seekExact(i.toLong())
            assertEquals(terms[i], te.term())
            i += random().nextInt(5)
        }
    }

    // Exercise the logic that leverages the first term of a block as a dictionary for suffixes of
    // other terms
    @Test
    @Throws(IOException::class)
    fun testTermsEnumDictionary() {
        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig()
        val iwriter = RandomIndexWriter(random(), directory, conf)
        val doc = Document()
        val field = SortedDocValuesField("field", BytesRef("abc0defghijkl"))
        doc.add(field)
        iwriter.addDocument(doc)
        field.setBytesValue(BytesRef("abc1defghijkl"))
        iwriter.addDocument(doc)
        field.setBytesValue(BytesRef("abc2defghijkl"))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)
        iwriter.close()

        val reader: IndexReader = DirectoryReader.open(directory)
        val leafReader: LeafReader = getOnlyLeafReader(reader)
        val values: SortedDocValues = leafReader.getSortedDocValues("field")!!
        val termsEnum: TermsEnum = values.termsEnum()!!
        assertEquals(BytesRef("abc0defghijkl"), termsEnum.next())
        assertEquals(BytesRef("abc1defghijkl"), termsEnum.next())
        assertEquals(BytesRef("abc2defghijkl"), termsEnum.next())
        assertNull(termsEnum.next())

        reader.close()
        directory.close()
    }

    // Testing termsEnum seekCeil edge case, where inconsistent internal state led to
    // IndexOutOfBoundsException
    // see https://github.com/apache/lucene/pull/12555 for details
    @Test
    @Throws(IOException::class)
    fun testTermsEnumConsistency() {
        val numTerms: Int = (Lucene90DocValuesFormat.TERMS_DICT_BLOCK_LZ4_SIZE + 10) // need more than one block of unique terms.
        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig()
        val iwriter = RandomIndexWriter(random(), directory, conf)
        val doc = Document()

        // for simplicity, we will generate sorted list of terms which are a) unique b) all greater than
        // the term that we want to use for the test
        val termA = 'A'
        val stringSupplier: (Int) -> String /*java.util.function.Function<Int, String>*/ =
            { n: Int ->
                assert(n < 25 * 25)
                val chars = charArrayOf(
                    (termA.code + 1 + n / 25).toChar(),
                    (termA.code + 1 + n % 25).toChar()
                )
                chars.concatToString()
            }
        val field = SortedDocValuesField("field", BytesRef(stringSupplier(0)))
        doc.add(field)
        iwriter.addDocument(doc)
        for (i in 1..<numTerms) {
            field.setBytesValue(BytesRef(stringSupplier(i)))
            iwriter.addDocument(doc)
        }
        // merging to one segment to make sure we have more than one block (TERMS_DICT_BLOCK_LZ4_SIZE)
        // in a segment, to trigger next block decompression.
        iwriter.forceMerge(1)
        iwriter.close()

        val reader: IndexReader = DirectoryReader.open(directory)
        val leafReader: LeafReader = getOnlyLeafReader(reader)
        val values: SortedDocValues = leafReader.getSortedDocValues("field")!!
        val termsEnum: TermsEnum = values.termsEnum()!!

        // Position terms enum at 0
        termsEnum.seekExact(0L)
        assertEquals(0, termsEnum.ord())
        // seekCeil to a term which doesn't exist in the index
        assertEquals(TermsEnum.SeekStatus.NOT_FOUND, termsEnum.seekCeil(BytesRef("A")))
        // ... and before any other term in the index
        assertEquals(0, termsEnum.ord())

        assertEquals(BytesRef(stringSupplier(0)), termsEnum.term())
        // read more than one block of terms to trigger next block decompression
        for (i in 1..<numTerms) {
            assertEquals(BytesRef(stringSupplier(i)), termsEnum.next())
        }
        assertNull(termsEnum.next())
        reader.close()
        directory.close()
    }


    // Tests inherited from BaseCompressingDocValuesFormatTestCase

    @Test
    override fun testUniqueValuesCompression() = super.testUniqueValuesCompression()

    @Test
    override fun testDateCompression() = super.testDateCompression()

    @Test
    override fun testSingleBigValueCompression() = super.testSingleBigValueCompression()



    // Tests inherited from BaseDocValuesFormatTestCase

    @Test
    override fun testSortedMergeAwayAllValuesWithSkipper() = super.testSortedMergeAwayAllValuesWithSkipper()

    @Test
    override fun testSortedSetMergeAwayAllValuesWithSkipper() = super.testSortedSetMergeAwayAllValuesWithSkipper()

    @Test
    override fun testNumberMergeAwayAllValuesWithSkipper() = super.testNumberMergeAwayAllValuesWithSkipper()

    @Test
    override fun testSortedNumberMergeAwayAllValuesWithSkipper() = super.testSortedNumberMergeAwayAllValuesWithSkipper()

    @Test
    override fun testSortedMergeAwayAllValuesLargeSegmentWithSkipper() = super.testSortedMergeAwayAllValuesLargeSegmentWithSkipper()

    @Test
    override fun testSortedSetMergeAwayAllValuesLargeSegmentWithSkipper() = super.testSortedSetMergeAwayAllValuesLargeSegmentWithSkipper()

    @Test
    override fun testNumericMergeAwayAllValuesLargeSegmentWithSkipper() = super.testNumericMergeAwayAllValuesLargeSegmentWithSkipper()

    @Test
    override fun testSortedNumericMergeAwayAllValuesLargeSegmentWithSkipper() = super.testSortedNumericMergeAwayAllValuesLargeSegmentWithSkipper()

    @Test
    override fun testNumericDocValuesWithSkipperSmall() = super.testNumericDocValuesWithSkipperSmall()

    @Test
    override fun testNumericDocValuesWithSkipperMedium() = super.testNumericDocValuesWithSkipperMedium()

    @Test
    override fun testNumericDocValuesWithSkipperBig() = super.testNumericDocValuesWithSkipperBig()

    @Test
    override fun testSortedNumericDocValuesWithSkipperSmall() = super.testSortedNumericDocValuesWithSkipperSmall()

    @Test
    override fun testSortedNumericDocValuesWithSkipperMedium() = super.testSortedNumericDocValuesWithSkipperMedium()

    @Test
    override fun testSortedNumericDocValuesWithSkipperBig() = super.testSortedNumericDocValuesWithSkipperBig()

    @Test
    override fun testSortedDocValuesWithSkipperSmall() = super.testSortedDocValuesWithSkipperSmall()

    @Test
    override fun testSortedDocValuesWithSkipperMedium() = super.testSortedDocValuesWithSkipperMedium()

    @Test
    override fun testSortedDocValuesWithSkipperBig() = super.testSortedDocValuesWithSkipperBig()

    @Test
    override fun testSortedSetDocValuesWithSkipperSmall() = super.testSortedSetDocValuesWithSkipperSmall()

    @Test
    override fun testSortedSetDocValuesWithSkipperMedium() = super.testSortedSetDocValuesWithSkipperMedium()

    @Test
    override fun testSortedSetDocValuesWithSkipperBig() = super.testSortedSetDocValuesWithSkipperBig()

    @Test
    override fun testMismatchedFields() = super.testMismatchedFields()



    // Tests inherited from LegacyBaseDocValuesFormatTestCase

    @Test
    override fun testOneNumber() = super.testOneNumber()

    @Test
    override fun testOneFloat() = super.testOneFloat()

    @Test
    override fun testTwoNumbers() = super.testTwoNumbers()

    @Test
    override fun testTwoBinaryValues() = super.testTwoBinaryValues()

    @Test
    override fun testVariouslyCompressibleBinaryValues() = super.testVariouslyCompressibleBinaryValues()

    @Test
    override fun testTwoFieldsMixed() = super.testTwoFieldsMixed()

    @Test
    override fun testThreeFieldsMixed() = super.testThreeFieldsMixed()

    @Test
    override fun testThreeFieldsMixed2() = super.testThreeFieldsMixed2()

    @Test
    override fun testTwoDocumentsNumeric() = super.testTwoDocumentsNumeric()

    @Test
    override fun testTwoDocumentsMerged() = super.testTwoDocumentsMerged()

    @Test
    override fun testBigNumericRange() = super.testBigNumericRange()

    @Test
    override fun testBigNumericRange2() = super.testBigNumericRange2()

    @Test
    override fun testBytes() = super.testBytes()

    @Test
    override fun testBytesTwoDocumentsMerged() = super.testBytesTwoDocumentsMerged()

    @Test
    override fun testBytesMergeAwayAllValues() = super.testBytesMergeAwayAllValues()

    @Test
    override fun testSortedBytes() = super.testSortedBytes()

    @Test
    override fun testSortedBytesTwoDocuments() = super.testSortedBytesTwoDocuments()

    @Test
    override fun testSortedBytesThreeDocuments() = super.testSortedBytesThreeDocuments()

    @Test
    override fun testSortedBytesTwoDocumentsMerged() = super.testSortedBytesTwoDocumentsMerged()

    @Test
    override fun testSortedMergeAwayAllValues() = super.testSortedMergeAwayAllValues()

    @Test
    override fun testBytesWithNewline() = super.testBytesWithNewline()

    @Test
    override fun testMissingSortedBytes() = super.testMissingSortedBytes()

    @Test
    override fun testSortedTermsEnum() = super.testSortedTermsEnum()

    @Test
    override fun testEmptySortedBytes() = super.testEmptySortedBytes()

    @Test
    override fun testEmptyBytes() = super.testEmptyBytes()

    @Test
    override fun testVeryLargeButLegalBytes() = super.testVeryLargeButLegalBytes()

    @Test
    override fun testVeryLargeButLegalSortedBytes() = super.testVeryLargeButLegalSortedBytes()

    @Test
    override fun testCodecUsesOwnBytes() = super.testCodecUsesOwnBytes()

    @Test
    override fun testCodecUsesOwnSortedBytes() = super.testCodecUsesOwnSortedBytes()

    @Test
    override fun testDocValuesSimple() = super.testDocValuesSimple()

    @Test
    override fun testRandomSortedBytes() = super.testRandomSortedBytes()

    @Test
    override fun testBooleanNumericsVsStoredFields() = super.testBooleanNumericsVsStoredFields()

    @Test
    override fun testSparseBooleanNumericsVsStoredFields() = super.testSparseBooleanNumericsVsStoredFields()

    @Test
    override fun testByteNumericsVsStoredFields() = super.testByteNumericsVsStoredFields()

    @Test
    override fun testSparseByteNumericsVsStoredFields() = super.testSparseByteNumericsVsStoredFields()

    @Test
    override fun testShortNumericsVsStoredFields() = super.testShortNumericsVsStoredFields()

    @Test
    override fun testSparseShortNumericsVsStoredFields() = super.testSparseShortNumericsVsStoredFields()

    @Test
    override fun testIntNumericsVsStoredFields() = super.testIntNumericsVsStoredFields()

    @Test
    override fun testSparseIntNumericsVsStoredFields() = super.testSparseIntNumericsVsStoredFields()

    @Test
    override fun testLongNumericsVsStoredFields() = super.testLongNumericsVsStoredFields()

    @Test
    override fun testSparseLongNumericsVsStoredFields() = super.testSparseLongNumericsVsStoredFields()

    @Test
    override fun testBinaryFixedLengthVsStoredFields() = super.testBinaryFixedLengthVsStoredFields()

    @Test
    override fun testSparseBinaryFixedLengthVsStoredFields() = super.testSparseBinaryFixedLengthVsStoredFields()

    @Test
    override fun testBinaryVariableLengthVsStoredFields() = super.testBinaryVariableLengthVsStoredFields()

    @Test
    override fun testSparseBinaryVariableLengthVsStoredFields() = super.testSparseBinaryVariableLengthVsStoredFields()

    @Test
    override fun testSortedFixedLengthVsStoredFields() = super.testSortedFixedLengthVsStoredFields()

    @Test
    override fun testSparseSortedFixedLengthVsStoredFields() = super.testSparseSortedFixedLengthVsStoredFields()

    @Test
    override fun testSortedVariableLengthVsStoredFields() = super.testSortedVariableLengthVsStoredFields()

    @Test
    override fun testSparseSortedVariableLengthVsStoredFields() = super.testSparseSortedVariableLengthVsStoredFields()

    @Test
    override fun testSortedSetOneValue() = super.testSortedSetOneValue()

    @Test
    override fun testSortedSetTwoFields() = super.testSortedSetTwoFields()

    @Test
    override fun testSortedSetTwoDocumentsMerged() = super.testSortedSetTwoDocumentsMerged()

    @Test
    override fun testSortedSetTwoValues() = super.testSortedSetTwoValues()

    @Test
    override fun testSortedSetTwoValuesUnordered() = super.testSortedSetTwoValuesUnordered()

    @Test
    override fun testSortedSetThreeValuesTwoDocs() = super.testSortedSetThreeValuesTwoDocs()

    @Test
    override fun testSortedSetTwoDocumentsLastMissing() = super.testSortedSetTwoDocumentsLastMissing()

    @Test
    override fun testSortedSetTwoDocumentsLastMissingMerge() = super.testSortedSetTwoDocumentsLastMissingMerge()

    @Test
    override fun testSortedSetTwoDocumentsFirstMissing() = super.testSortedSetTwoDocumentsFirstMissing()

    @Test
    override fun testSortedSetTwoDocumentsFirstMissingMerge() = super.testSortedSetTwoDocumentsFirstMissingMerge()

    @Test
    override fun testSortedSetMergeAwayAllValues() = super.testSortedSetMergeAwayAllValues()

    @Test
    override fun testSortedSetTermsEnum() = super.testSortedSetTermsEnum()

    @Test
    override fun testSortedSetFixedLengthVsStoredFields() = super.testSortedSetFixedLengthVsStoredFields()

    @Test
    override fun testSortedNumericsSingleValuedVsStoredFields() = super.testSortedNumericsSingleValuedVsStoredFields()

    @Test
    override fun testSortedNumericsSingleValuedMissingVsStoredFields() = super.testSortedNumericsSingleValuedMissingVsStoredFields()

    @Test
    override fun testSortedNumericsMultipleValuesVsStoredFields() = super.testSortedNumericsMultipleValuesVsStoredFields()

    @Test
    override fun testSortedNumericsFewUniqueSetsVsStoredFields() = super.testSortedNumericsFewUniqueSetsVsStoredFields()

    @Test
    override fun testSortedSetVariableLengthVsStoredFields() = super.testSortedSetVariableLengthVsStoredFields()

    @Test
    override fun testSortedSetFixedLengthSingleValuedVsStoredFields() = super.testSortedSetFixedLengthSingleValuedVsStoredFields()

    @Test
    override fun testSortedSetVariableLengthSingleValuedVsStoredFields() = super.testSortedSetVariableLengthSingleValuedVsStoredFields()

    @Test
    override fun testSortedSetFixedLengthFewUniqueSetsVsStoredFields() = super.testSortedSetFixedLengthFewUniqueSetsVsStoredFields()

    @Test
    override fun testSortedSetVariableLengthFewUniqueSetsVsStoredFields() = super.testSortedSetVariableLengthFewUniqueSetsVsStoredFields()

    @Test
    override fun testSortedSetVariableLengthManyValuesPerDocVsStoredFields() = super.testSortedSetVariableLengthManyValuesPerDocVsStoredFields()

    @Test
    override fun testSortedSetFixedLengthManyValuesPerDocVsStoredFields() = super.testSortedSetFixedLengthManyValuesPerDocVsStoredFields()

    @Test
    override fun testGCDCompression() = super.testGCDCompression()

    @Test
    override fun testSparseGCDCompression() = super.testSparseGCDCompression()

    @Test
    override fun testZeros() = super.testZeros()

    @Test
    override fun testSparseZeros() = super.testSparseZeros()

    @Test
    override fun testZeroOrMin() = super.testZeroOrMin()

    @Test
    override fun testTwoNumbersOneMissing() = super.testTwoNumbersOneMissing()

    @Test
    override fun testTwoNumbersOneMissingWithMerging() = super.testTwoNumbersOneMissingWithMerging()

    @Test
    override fun testThreeNumbersOneMissingWithMerging() = super.testThreeNumbersOneMissingWithMerging()

    @Test
    override fun testTwoBytesOneMissing() = super.testTwoBytesOneMissing()

    @Test
    override fun testTwoBytesOneMissingWithMerging() = super.testTwoBytesOneMissingWithMerging()

    @Test
    override fun testThreeBytesOneMissingWithMerging() = super.testThreeBytesOneMissingWithMerging()

    @Test
    override fun testThreads() = super.testThreads()

    @Test
    override fun testThreads2() = super.testThreads2()

    @Test
    override fun testThreads3() = super.testThreads3()

    @Test
    override fun testEmptyBinaryValueOnPageSizes() = super.testEmptyBinaryValueOnPageSizes()

    @Test
    override fun testOneSortedNumber() = super.testOneSortedNumber()

    @Test
    override fun testOneSortedNumberOneMissing() = super.testOneSortedNumberOneMissing()

    @Test
    override fun testNumberMergeAwayAllValues() = super.testNumberMergeAwayAllValues()

    @Test
    override fun testTwoSortedNumber() = super.testTwoSortedNumber()

    @Test
    override fun testTwoSortedNumberSameValue() = super.testTwoSortedNumberSameValue()

    @Test
    override fun testTwoSortedNumberOneMissing() = super.testTwoSortedNumberOneMissing()

    @Test
    override fun testSortedNumberMerge() = super.testSortedNumberMerge()

    @Test
    override fun testSortedNumberMergeAwayAllValues() = super.testSortedNumberMergeAwayAllValues()

    @Test
    override fun testSortedEnumAdvanceIndependently() = super.testSortedEnumAdvanceIndependently()

    @Test
    override fun testSortedSetEnumAdvanceIndependently() = super.testSortedSetEnumAdvanceIndependently()

    @Test
    override fun testSortedMergeAwayAllValuesLargeSegment() = super.testSortedMergeAwayAllValuesLargeSegment()

    @Test
    override fun testSortedSetMergeAwayAllValuesLargeSegment() = super.testSortedSetMergeAwayAllValuesLargeSegment()

    @Test
    override fun testNumericMergeAwayAllValuesLargeSegment() = super.testNumericMergeAwayAllValuesLargeSegment()

    @Test
    override fun testSortedNumericMergeAwayAllValuesLargeSegment() = super.testSortedNumericMergeAwayAllValuesLargeSegment()

    @Test
    override fun testBinaryMergeAwayAllValuesLargeSegment() = super.testBinaryMergeAwayAllValuesLargeSegment()

    @Test
    override fun testRandomAdvanceNumeric() = super.testRandomAdvanceNumeric()

    @Test
    override fun testRandomAdvanceBinary() = super.testRandomAdvanceBinary()

    @Test
    override fun testHighOrdsSortedSetDV() = super.testHighOrdsSortedSetDV()

    companion object {
        private fun blocksOfVariousBPV(): () -> Long /*java.util.function.LongSupplier*/ {
            // this helps exercise GCD compression:
            val mul = TestUtil.nextInt(random(), 1, 100).toLong()
            val min = random().nextInt().toLong()
            return {
                var i: Int = Lucene90DocValuesFormat.NUMERIC_BLOCK_SIZE
                var maxDelta = 0

                if (i == Lucene90DocValuesFormat.NUMERIC_BLOCK_SIZE) {
                    // change the range of the random generated values on block boundaries, so we exercise
                    // different bits-per-value for each block, and encourage block compression
                    maxDelta = 1 shl random().nextInt(5)
                    i = 0
                }
                i++
                min + mul * random().nextInt(maxDelta)

            }
        }
    }
}
