package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.jdkport.longBitsToDouble
import org.gnit.lucenekmp.jdkport.toByteArray
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


// sanity check some basics of fields
class TestField : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testDoublePoint() {
        val field: Field =
            DoublePoint("foo", 5.0)

        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        field.setDoubleValue(6.0) // ok
        trySetIntValue(field)
        trySetFloatValue(field)
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(6.0, field.numericValue()!!.toDouble(), 0.0)
        assertEquals("DoublePoint <foo:6.0>", field.toString())
    }

    @Test
    @Throws(Exception::class)
    fun testDoublePoint2D() {
        val field: DoublePoint =
            DoublePoint("foo", 5.0, 4.0)

        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        field.setDoubleValues(6.0, 7.0) // ok
        trySetIntValue(field)
        trySetFloatValue(field)
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        val expected: IllegalStateException =
            expectThrows<IllegalStateException>(
                IllegalStateException::class,
                {
                    field.numericValue()
                })
        assertTrue(expected.message!!.contains("cannot convert to a single numeric value"))
        assertEquals("DoublePoint <foo:6.0,7.0>", field.toString())
    }

    @Test
    @Throws(Exception::class)
    fun testDoubleDocValuesField() {
        val field = DoubleDocValuesField("foo", 5.0)

        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        field.setDoubleValue(6.0) // ok
        trySetIntValue(field)
        trySetFloatValue(field)
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(
            6.0,
            Double.longBitsToDouble(field.numericValue()!!.toLong()),
            0.0
        )
    }

    @Test
    @Throws(Exception::class)
    fun testFloatDocValuesField() {
        val field = FloatDocValuesField("foo", 5f)

        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        trySetIntValue(field)
        field.setFloatValue(6f) // ok
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(
            6f,
            Float.intBitsToFloat(field.numericValue()!!.toInt()),
            0.0f
        )
    }

    @Test
    @Throws(Exception::class)
    fun testFloatPoint() {
        val field: Field =
            FloatPoint("foo", 5f)

        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        trySetIntValue(field)
        field.setFloatValue(6f) // ok
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(6f, field.numericValue()!!.toFloat(), 0.0f)
        assertEquals("FloatPoint <foo:6.0>", field.toString())
    }

    @Test
    @Throws(Exception::class)
    fun testFloatPoint2D() {
        val field = FloatPoint("foo", 5f, 4f)

        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        trySetIntValue(field)
        trySetFloatValue(field)
        field.setFloatValues(6f, 7f) // ok
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        val expected: IllegalStateException =
            expectThrows<IllegalStateException>(
                IllegalStateException::class,
                {
                    field.numericValue()
                })
        assertTrue(expected.message!!.contains("cannot convert to a single numeric value"))
        assertEquals("FloatPoint <foo:6.0,7.0>", field.toString())
    }

    @Test
    @Throws(Exception::class)
    fun testIntPoint() {
        val field: Field = IntPoint("foo", 5)

        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        field.setIntValue(6) // ok
        trySetFloatValue(field)
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(6, field.numericValue()!!.toInt().toLong())
        assertEquals("IntPoint <foo:6>", field.toString())
    }

    @Test
    @Throws(Exception::class)
    fun testIntPoint2D() {
        val field: IntPoint =
            IntPoint("foo", 5, 4)

        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        trySetIntValue(field)
        field.setIntValues(6, 7) // ok
        trySetFloatValue(field)
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        val expected: IllegalStateException =
            expectThrows<IllegalStateException>(
                IllegalStateException::class,
                {
                    field.numericValue()
                })
        assertTrue(expected.message!!.contains("cannot convert to a single numeric value"))
        assertEquals("IntPoint <foo:6,7>", field.toString())
    }

    @Test
    @Throws(Exception::class)
    fun testIntField() {
        val fields: Array<Field> =
            arrayOf(
                IntField("foo", 12, Field.Store.NO),
                IntField("foo", 12, Field.Store.YES),
            )

        for (field in fields) {
            trySetByteValue(field)
            trySetBytesValue(field)
            trySetBytesRefValue(field)
            trySetDoubleValue(field)
            field.setIntValue(6)
            trySetLongValue(field)
            trySetFloatValue(field)
            trySetLongValue(field)
            trySetReaderValue(field)
            trySetShortValue(field)
            trySetStringValue(field)
            trySetTokenStreamValue(field)

            assertEquals(6, field.numericValue()!!.toInt().toLong())
            assertEquals(
                6,
                NumericUtils.sortableBytesToInt(field.binaryValue()!!.bytes, 0)
                    .toLong()
            )
            assertEquals("IntField <foo:6>", field.toString())
            if (field.fieldType().stored()) {
                assertEquals(6, field.storedValue()!!.getIntValue().toLong())
            } else {
                assertNull(field.storedValue())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testLongField() {
        val fields: Array<Field> =
            arrayOf(
                LongField(
                    "foo",
                    12,
                    Field.Store.NO
                ),
                LongField(
                    "foo",
                    12,
                    Field.Store.YES
                ),
            )

        for (field in fields) {
            trySetByteValue(field)
            trySetBytesValue(field)
            trySetBytesRefValue(field)
            trySetDoubleValue(field)
            trySetIntValue(field)
            field.setLongValue(6)
            trySetFloatValue(field)
            trySetReaderValue(field)
            trySetShortValue(field)
            trySetStringValue(field)
            trySetTokenStreamValue(field)

            assertEquals(6L, field.numericValue()!!.toLong())
            assertEquals(
                6L,
                NumericUtils.sortableBytesToLong(
                    field.binaryValue()!!.bytes,
                    0
                )
            )
            assertEquals("LongField <foo:6>", field.toString())
            if (field.fieldType().stored()) {
                assertEquals(6, field.storedValue()!!.getLongValue())
            } else {
                assertNull(field.storedValue())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFloatField() {
        val fields: Array<Field> =
            arrayOf(
                FloatField(
                    "foo",
                    12.6f,
                    Field.Store.NO
                ),
                FloatField(
                    "foo",
                    12.6f,
                    Field.Store.YES
                ),
            )

        for (field in fields) {
            assertEquals(
                12.6f,
                NumericUtils.sortableIntToFloat(
                    field.numericValue()!!.toInt()
                ),
                0.0f
            )
            assertEquals(
                12.6f,
                FloatPoint.decodeDimension(field.binaryValue()!!.bytes, 0),
                0.0f
            )
            assertEquals("FloatField <foo:12.6>", field.toString())

            trySetByteValue(field)
            trySetBytesValue(field)
            trySetBytesRefValue(field)
            trySetDoubleValue(field)
            trySetIntValue(field)
            trySetLongValue(field)
            field.setFloatValue(-28.8f)
            trySetReaderValue(field)
            trySetShortValue(field)
            trySetStringValue(field)
            trySetTokenStreamValue(field)

            assertEquals(
                -28.8f,
                NumericUtils.sortableIntToFloat(
                    field.numericValue()!!.toInt()
                ),
                0.0f
            )
            assertEquals(
                -28.8f,
                FloatPoint.decodeDimension(field.binaryValue()!!.bytes, 0),
                0.0f
            )
            assertEquals("FloatField <foo:-28.8>", field.toString())
            if (field.fieldType().stored()) {
                assertEquals(-28.8f, field.storedValue()!!.getFloatValue(), 0f)
            } else {
                assertNull(field.storedValue())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDoubleField() {
        val fields: Array<Field> =
            arrayOf(
                DoubleField(
                    "foo",
                    12.7,
                    Field.Store.NO
                ),
                DoubleField(
                    "foo",
                    12.7,
                    Field.Store.YES
                ),
            )

        for (field in fields) {
            assertEquals(
                12.7,
                NumericUtils.sortableLongToDouble(
                    field.numericValue()!!.toLong()
                ),
                0.0
            )
            assertEquals(
                12.7,
                DoublePoint.decodeDimension(
                    field.binaryValue()!!.bytes,
                    0
                ),
                0.0
            )
            assertEquals("DoubleField <foo:12.7>", field.toString())

            trySetByteValue(field)
            trySetBytesValue(field)
            trySetBytesRefValue(field)
            trySetIntValue(field)
            trySetLongValue(field)
            trySetFloatValue(field)
            field.setDoubleValue(-28.8)
            trySetReaderValue(field)
            trySetShortValue(field)
            trySetStringValue(field)
            trySetTokenStreamValue(field)

            assertEquals(
                -28.8,
                NumericUtils.sortableLongToDouble(
                    field.numericValue()!!.toLong()
                ),
                0.0
            )
            assertEquals(
                -28.8,
                DoublePoint.decodeDimension(
                    field.binaryValue()!!.bytes,
                    0
                ),
                0.0
            )
            assertEquals("DoubleField <foo:-28.8>", field.toString())
            if (field.fieldType().stored()) {
                assertEquals(-28.8, field.storedValue()!!.getDoubleValue(), 0.0)
            } else {
                assertNull(field.storedValue())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testNumericDocValuesField() {
        val field = NumericDocValuesField("foo", 5L)

        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        trySetIntValue(field)
        trySetFloatValue(field)
        field.setLongValue(6) // ok
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(6L, field.numericValue()!!.toLong())
    }

    @Test
    @Throws(Exception::class)
    fun testLongPoint() {
        val field: Field = LongPoint("foo", 5)

        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        trySetIntValue(field)
        trySetFloatValue(field)
        field.setLongValue(6) // ok
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(6, field.numericValue()!!.toInt().toLong())
        assertEquals("LongPoint <foo:6>", field.toString())
    }

    @Test
    @Throws(Exception::class)
    fun testLongPoint2D() {
        val field = LongPoint("foo", 5, 4)

        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        trySetIntValue(field)
        trySetFloatValue(field)
        trySetLongValue(field)
        field.setLongValues(6, 7) // ok
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        val expected: IllegalStateException =
            expectThrows<IllegalStateException>(
                IllegalStateException::class,
                {
                    field.numericValue()
                })
        assertTrue(expected.message!!.contains("cannot convert to a single numeric value"))
        assertEquals("LongPoint <foo:6,7>", field.toString())
    }

    @Test
    @Throws(Exception::class)
    fun testSortedBytesDocValuesField() {
        val field = SortedDocValuesField(
                "foo",
                newBytesRef("bar")
            )

        trySetByteValue(field)
        field.setBytesValue("fubar".toByteArray(StandardCharsets.UTF_8))
        field.setBytesValue(newBytesRef("baz"))
        trySetDoubleValue(field)
        trySetIntValue(field)
        trySetFloatValue(field)
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(
            newBytesRef("baz"),
            field.binaryValue()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testBinaryDocValuesField() {
        val field = BinaryDocValuesField(
                "foo",
                newBytesRef("bar")
            )

        trySetByteValue(field)
        field.setBytesValue("fubar".toByteArray(StandardCharsets.UTF_8))
        field.setBytesValue(newBytesRef("baz"))
        trySetDoubleValue(field)
        trySetIntValue(field)
        trySetFloatValue(field)
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(
            newBytesRef("baz"),
            field.binaryValue()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testStringField() {
        val fields: Array<Field> =
            arrayOf(
                StringField(
                    "foo",
                    "bar",
                    Field.Store.NO
                ),
                StringField(
                    "foo",
                    "bar",
                    Field.Store.YES
                )
            )

        for (field in fields) {
            trySetByteValue(field)
            trySetBytesValue(field)
            trySetBytesRefValue(field)
            trySetDoubleValue(field)
            trySetIntValue(field)
            trySetFloatValue(field)
            trySetLongValue(field)
            trySetReaderValue(field)
            trySetShortValue(field)
            field.setStringValue("baz")
            trySetTokenStreamValue(field)

            assertEquals("baz", field.stringValue())
            if (field.fieldType().stored()) {
                assertEquals("baz", field.storedValue()!!.stringValue)
            } else {
                assertNull(field.storedValue())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBinaryStringField() {
        val fields: Array<Field> =
            arrayOf(
                StringField(
                    "foo",
                    BytesRef("bar"),
                    Field.Store.NO
                ),
                StringField(
                    "foo",
                    BytesRef("bar"),
                    Field.Store.YES
                )
            )

        for (field in fields) {
            trySetByteValue(field)
            field.setBytesValue("baz".toByteArray(StandardCharsets.UTF_8))
            assertEquals(
                BytesRef("baz"),
                field.binaryValue()
            )
            field.setBytesValue(BytesRef("baz"))
            trySetDoubleValue(field)
            trySetIntValue(field)
            trySetFloatValue(field)
            trySetLongValue(field)
            trySetReaderValue(field)
            trySetShortValue(field)
            trySetStringValue(field)
            trySetTokenStreamValue(field)

            assertEquals(
                BytesRef("baz"),
                field.binaryValue()
            )
            if (field.fieldType().stored()) {
                assertEquals(
                    BytesRef("baz"),
                    field.storedValue()!!.binaryValue
                )
            } else {
                assertNull(field.storedValue())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testTextFieldString() {
        val fields: Array<Field> =
            arrayOf(
                TextField(
                    "foo",
                    "bar",
                    Field.Store.NO
                ),
                TextField(
                    "foo",
                    "bar",
                    Field.Store.YES
                )
            )

        for (field in fields) {
            trySetByteValue(field)
            trySetBytesValue(field)
            trySetBytesRefValue(field)
            trySetDoubleValue(field)
            trySetIntValue(field)
            trySetFloatValue(field)
            trySetLongValue(field)
            trySetReaderValue(field)
            trySetShortValue(field)
            field.setStringValue("baz")
            trySetTokenStreamValue(field)

            assertEquals("baz", field.stringValue())
            if (field.fieldType().stored()) {
                assertEquals("baz", field.storedValue()!!.stringValue)
            } else {
                assertNull(field.storedValue())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testTextFieldReader() {
        val field: Field =
            TextField("foo", StringReader("bar"))

        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        trySetIntValue(field)
        trySetFloatValue(field)
        trySetLongValue(field)
        field.setReaderValue(StringReader("foobar"))
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertNotNull(field.readerValue())
        assertNull(field.storedValue())
    }

    /* TODO: this is pretty expert and crazy
   * see if we can fix it up later
  public void testTextFieldTokenStream() throws Exception {
  }
  */
    @Test
    @Throws(Exception::class)
    fun testStoredFieldBytes() {
        val fields: Array<Field> =
            arrayOf(
                StoredField(
                    "foo",
                    "bar".toByteArray(StandardCharsets.UTF_8)
                ),
                StoredField(
                    "foo",
                    "bar".toByteArray(StandardCharsets.UTF_8),
                    0,
                    3
                ),
                StoredField(
                    "foo",
                    newBytesRef("bar")
                ),
            )

        for (field in fields) {
            trySetByteValue(field)
            field.setBytesValue("baz".toByteArray(StandardCharsets.UTF_8))
            field.setBytesValue(newBytesRef("baz"))
            trySetDoubleValue(field)
            trySetIntValue(field)
            trySetFloatValue(field)
            trySetLongValue(field)
            trySetReaderValue(field)
            trySetShortValue(field)
            trySetStringValue(field)
            trySetTokenStreamValue(field)

            assertEquals(
                newBytesRef("baz"),
                field.binaryValue()
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testStoredFieldString() {
        val field: Field =
            StoredField("foo", "bar")
        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        trySetIntValue(field)
        trySetFloatValue(field)
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        field.setStringValue("baz")
        trySetTokenStreamValue(field)

        assertEquals("baz", field.stringValue())
    }

    @Test
    @Throws(Exception::class)
    fun testStoredFieldInt() {
        val field: Field =
            StoredField("foo", 1)
        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        field.setIntValue(5)
        trySetFloatValue(field)
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(5, field.numericValue()!!.toInt().toLong())
    }

    @Test
    @Throws(Exception::class)
    fun testStoredFieldDouble() {
        val field: Field =
            StoredField("foo", 1.0)
        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        field.setDoubleValue(5.0)
        trySetIntValue(field)
        trySetFloatValue(field)
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(5.0, field.numericValue()!!.toDouble(), 0.0)
    }

    @Test
    @Throws(Exception::class)
    fun testStoredFieldFloat() {
        val field: Field =
            StoredField("foo", 1f)
        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        trySetIntValue(field)
        field.setFloatValue(5f)
        trySetLongValue(field)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(5f, field.numericValue()!!.toFloat(), 0.0f)
    }

    @Test
    @Throws(Exception::class)
    fun testStoredFieldLong() {
        val field: Field =
            StoredField("foo", 1L)
        trySetByteValue(field)
        trySetBytesValue(field)
        trySetBytesRefValue(field)
        trySetDoubleValue(field)
        trySetIntValue(field)
        trySetFloatValue(field)
        field.setLongValue(5)
        trySetReaderValue(field)
        trySetShortValue(field)
        trySetStringValue(field)
        trySetTokenStreamValue(field)

        assertEquals(5L, field.numericValue()!!.toLong())
    }

    @Test
    @Throws(Exception::class)
    fun testIndexedBinaryField() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val br: BytesRef = newBytesRef(ByteArray(5))
        val field: Field = StringField("binary", br, Field.Store.YES)
        assertEquals(br, field.binaryValue())
        doc.add(field)
        w.addDocument(doc)
        val r: IndexReader = w.reader

        val s: IndexSearcher = newSearcher(r)
        val hits: TopDocs = s.search(
            TermQuery(Term("binary", br)),
            1
        )
        assertEquals(1, hits.totalHits.value)
        val storedDoc: Document = s.storedFields().document(hits.scoreDocs[0].doc)
        assertEquals(br, storedDoc.getField("binary")!!.binaryValue())

        r.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testKnnVectorField() {
        if (Codec.default.name == "SimpleText") {
            return
        }
        newDirectory().use { dir ->
            IndexWriter(
                dir,
                newIndexWriterConfig()
            ).use { w ->
                val doc = Document()
                val b = ByteArray(5)
                val field = KnnByteVectorField(
                        "binary",
                        b,
                        VectorSimilarityFunction.EUCLIDEAN
                    )
                assertNull(field.binaryValue())
                assertArrayEquals(b, field.vectorValue())
                expectThrows(
                    IllegalArgumentException::class,
                    {
                        KnnFloatVectorField(
                            "bogus",
                            floatArrayOf(1f),
                            field.fieldType() as FieldType
                        )
                    })
                val vector = floatArrayOf(1f, 2f)
                val field2: Field =
                    KnnFloatVectorField("float", vector)
                assertNull(field2.binaryValue())
                doc.add(field)
                doc.add(field2)
                w.addDocument(doc)
                DirectoryReader.open(w).use { r ->
                    val binary: ByteVectorValues = r.leaves()[0].reader().getByteVectorValues("binary")!!
                    assertEquals(1, binary.size().toLong())
                    val iterator: KnnVectorValues.DocIndexIterator =
                        binary.iterator()
                    assertNotEquals(
                        DocIdSetIterator.NO_MORE_DOCS.toLong(),
                        iterator.nextDoc().toLong()
                    )
                    assertNotNull(binary.vectorValue(0))
                    assertArrayEquals(b, binary.vectorValue(0))
                    assertEquals(
                        DocIdSetIterator.NO_MORE_DOCS.toLong(),
                        iterator.nextDoc().toLong()
                    )
                    expectThrows<IOException>(
                        IOException::class,
                        {
                            binary.vectorValue(1)
                        })

                    val floatValues: FloatVectorValues =
                        r.leaves().get(0).reader().getFloatVectorValues("float")!!
                    assertEquals(1, floatValues.size().toLong())
                    val iterator1: KnnVectorValues.DocIndexIterator =
                        floatValues.iterator()
                    assertNotEquals(
                        DocIdSetIterator.NO_MORE_DOCS.toLong(),
                        iterator1.nextDoc().toLong()
                    )
                    assertEquals(
                        vector.size.toLong(),
                        floatValues.vectorValue(0).size.toLong()
                    )
                    assertEquals(vector[0], floatValues.vectorValue(0)[0], 0f)
                    assertEquals(
                        DocIdSetIterator.NO_MORE_DOCS.toLong(),
                        iterator1.nextDoc().toLong()
                    )
                    expectThrows<IOException>(
                        IOException::class,
                        {
                            floatValues.vectorValue(1)
                        })
                }
            }
        }
    }

    private fun trySetByteValue(f: Field) {
        expectThrows(
            IllegalArgumentException::class,
            {
                f.setByteValue(10.toByte())
            })
    }

    private fun trySetBytesValue(f: Field) {
        expectThrows(
            IllegalArgumentException::class,
            {
                f.setBytesValue(byteArrayOf(5, 5))
            })
    }

    private fun trySetBytesRefValue(f: Field) {
        expectThrows(
            IllegalArgumentException::class,
            {
                f.setBytesValue(newBytesRef("bogus"))
            })
    }

    private fun trySetDoubleValue(f: Field) {
        expectThrows(
            IllegalArgumentException::class,
            {
                f.setDoubleValue(Double.Companion.MAX_VALUE)
            })
    }

    private fun trySetIntValue(f: Field) {
        expectThrows(
            IllegalArgumentException::class,
            {
                f.setIntValue(Int.Companion.MAX_VALUE)
            })
    }

    private fun trySetLongValue(f: Field) {
        expectThrows(
            IllegalArgumentException::class,
            {
                f.setLongValue(Long.Companion.MAX_VALUE)
            })
    }

    private fun trySetFloatValue(f: Field) {
        expectThrows(
            IllegalArgumentException::class,
            {
                f.setFloatValue(Float.Companion.MAX_VALUE)
            })
    }

    private fun trySetReaderValue(f: Field) {
        expectThrows(
            IllegalArgumentException::class,
            {
                f.setReaderValue(StringReader("BOO!"))
            })
    }

    private fun trySetShortValue(f: Field) {
        expectThrows(
            IllegalArgumentException::class,
            {
                f.setShortValue(Short.Companion.MAX_VALUE)
            })
    }

    private fun trySetStringValue(f: Field) {
        expectThrows(
            IllegalArgumentException::class,
            {
                f.setStringValue("BOO!")
            })
    }

    private fun trySetTokenStreamValue(f: Field) {
        expectThrows(
            IllegalArgumentException::class,
            {
                f.setTokenStream(
                    CannedTokenStream(
                        Token("foo", 0, 3)
                    )
                )
            })
    }

    @Test
    fun testDisabledField() {
        // neither indexed nor stored
        val ft: FieldType = FieldType()
        expectThrows(
            IllegalArgumentException::class,
            {
                Field("name", "", ft)
            })
    }

    @Test
    fun testTokenizedBinaryField() {
        val ft: FieldType = FieldType()
        ft.setTokenized(true)
        ft.setIndexOptions(IndexOptions.DOCS)
        expectThrows(
            IllegalArgumentException::class,
            {
                Field(
                    "name",
                    BytesRef(),
                    ft
                )
            })
    }

    @Test
    fun testOffsetsBinaryField() {
        val ft: FieldType = FieldType()
        ft.setTokenized(false)
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        expectThrows(
            IllegalArgumentException::class,
            {
                Field(
                    "name",
                    BytesRef(),
                    ft
                )
            })
    }

    @Test
    fun testTermVectorsOffsetsBinaryField() {
        val ft: FieldType = FieldType()
        ft.setTokenized(false)
        ft.setStoreTermVectors(true)
        ft.setStoreTermVectorOffsets(true)
        expectThrows(
            IllegalArgumentException::class,
            {
                Field(
                    "name",
                    BytesRef(),
                    ft
                )
            })
    }
}
