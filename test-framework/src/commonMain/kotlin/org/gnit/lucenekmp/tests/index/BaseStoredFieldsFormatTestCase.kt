package org.gnit.lucenekmp.tests.index

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.StoredFieldsFormat
import org.gnit.lucenekmp.codecs.simpletext.SimpleTextCodec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.FilterDirectoryReader
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.StoredFieldDataInput
import org.gnit.lucenekmp.index.StoredFieldVisitor
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.jdkport.toByteArray
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.MMapDirectory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.RandomStrings
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Base class aiming at testing [stored fields formats][StoredFieldsFormat]. To test a new
 * format, all you need is to register a new [Codec] which uses it and extend this class and
 * override [.getCodec].
 *
 * @lucene.experimental
 */
abstract class BaseStoredFieldsFormatTestCase : BaseIndexFileFormatTestCase() {
    override fun addRandomFields(d: Document) {
        val numValues: Int = random().nextInt(3)
        for (i in 0..<numValues) {
            d.add(StoredField("f", TestUtil.randomSimpleString(random(), 100)))
        }
    }

    @Throws(IOException::class)
    open fun testRandomStoredFields() {
        val dir: Directory = newDirectory()
        val rand: Random = random()
        val w = RandomIndexWriter(rand, dir, newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(TestUtil.nextInt(rand, 5, 20)))
        // w.w.setNoCFSRatio(0.0);
        val docCount: Int = atLeast(200)
        val fieldCount: Int = TestUtil.nextInt(rand, 1, 5)

        val fieldIDs: MutableList<Int> = mutableListOf()

        val customType = FieldType(TextField.TYPE_STORED)
        customType.setTokenized(false)
        val idField = newField("id", "", customType)

        for (i in 0..<fieldCount) {
            fieldIDs.add(i)
        }

        val docs: MutableMap<String, Document> = HashMap()

        if (VERBOSE) {
            println("TEST: build index docCount=$docCount")
        }

        val customType2 = FieldType()
        customType2.setStored(true)
        for (i in 0..<docCount) {
            val doc = Document()
            doc.add(idField)
            val id = "" + i
            idField.setStringValue(id)
            docs[id] = doc
            if (VERBOSE) {
                println("TEST: add doc id=$id")
            }

            for (field in fieldIDs) {
                if (rand.nextInt(4) != 3) {
                    val s: String = TestUtil.randomUnicodeString(rand, 1000)
                    doc.add(newField("f$field", s, customType2))
                }
            }
            w.addDocument(doc)
            if (rand.nextInt(50) == 17) {
                // mixup binding of field name -> Number every so often
                fieldIDs.shuffle(
                    random()
                )
            }
            if (rand.nextInt(5) == 3 && i > 0) {
                val delID = "" + rand.nextInt(i)
                if (VERBOSE) {
                    println("TEST: delete doc id=$delID")
                }
                w.deleteDocuments(Term("id", delID))
                docs.remove(delID)
            }
        }

        if (VERBOSE) {
            println("TEST: " + docs.size + " docs in index; now load fields")
        }
        if (docs.isNotEmpty()) {
            val idsList = docs.keys.toTypedArray()

            for (x in 0..1) {
                val r: DirectoryReader = maybeWrapWithMergingReader(w.reader)
                val s = newSearcher(r)
                val storedFields: StoredFields = r.storedFields()

                if (VERBOSE) {
                    println("TEST: cycle x=$x r=$r")
                }

                val num: Int = atLeast(100)
                for (iter in 0..<num) {
                    val testID = idsList[rand.nextInt(idsList.size)]
                    if (VERBOSE) {
                        println("TEST: test id=$testID")
                    }
                    val hits: TopDocs = s.search(TermQuery(Term("id", testID)), 1)
                    assertEquals(1, hits.totalHits.value)
                    val doc =
                        storedFields.document(hits.scoreDocs[0].doc)
                    val docExp = docs[testID]!!
                    for (i in 0..<fieldCount) {
                        assertEquals(
                            "doc $testID, field f$fieldCount is wrong",
                            docExp.get("f$i"),
                            doc.get("f$i")
                        )
                    }
                }
                r.close()
                w.forceMerge(1)
            }
        }
        w.close()
        dir.close()
    }

    // LUCENE-1727: make sure doc fields are stored in order
    @Throws(Throwable::class)
    open fun testStoredFieldsOrder() {
        val d: Directory = newDirectory()
        val w = IndexWriter(d, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        val customType = FieldType()
        customType.setStored(true)
        doc.add(newField("zzz", "a b c", customType))
        doc.add(newField("aaa", "a b c", customType))
        doc.add(newField("zzz", "1 2 3", customType))
        w.addDocument(doc)
        val r: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(w))
        val storedFields: StoredFields = r.storedFields()
        val doc2 = storedFields.document(0)
        val it: MutableIterator<IndexableField> = doc2.getFields().iterator()
        assertTrue(it.hasNext())
        var f = it.next() as Field
        assertEquals(f.name(), "zzz")
        assertEquals(f.stringValue(), "a b c")

        assertTrue(it.hasNext())
        f = it.next() as Field
        assertEquals(f.name(), "aaa")
        assertEquals(f.stringValue(), "a b c")

        assertTrue(it.hasNext())
        f = it.next() as Field
        assertEquals(f.name(), "zzz")
        assertEquals(f.stringValue(), "1 2 3")
        assertFalse(it.hasNext())
        r.close()
        w.close()
        d.close()
    }

    // LUCENE-1219
    @Throws(IOException::class)
    open fun testBinaryFieldOffsetLength() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        var b = ByteArray(50)
        for (i in 0..49) b[i] = (i + 77).toByte()

        val doc = Document()
        val f = StoredField("binary", b, 10, 17)
        val bx: ByteArray = f.binaryValue()!!.bytes
        assertTrue(bx != null)
        assertEquals(50, bx.size.toLong())
        assertEquals(10, f.binaryValue()!!.offset.toLong())
        assertEquals(17, f.binaryValue()!!.length.toLong())
        doc.add(f)
        w.addDocument(doc)
        w.close()

        val ir: IndexReader = DirectoryReader.open(dir)
        val storedFields: StoredFields = ir.storedFields()
        val doc2 = storedFields.document(0)
        val f2: IndexableField = doc2.getField("binary")!!
        b = f2.binaryValue()!!.bytes
        assertTrue(b != null)
        assertEquals(17f, b.size.toFloat(), 17f)
        assertEquals(87, b[0].toLong())
        ir.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testNumericField() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val numDocs: Int = atLeast(500)
        val answers = kotlin.arrayOfNulls<Number>(numDocs)
        val typeAnswers: Array<KClass<*>> = Array(numDocs)
        /*for (id in 0..<numDocs) */{ id ->
            val doc = Document()
            val nf: Field
            val answer: Number
            val typeAnswer: KClass<*>
            if (random().nextBoolean()) {
                // float/double
                if (random().nextBoolean()) {
                    val f: Float = random().nextFloat()
                    answer = f
                    nf = StoredField("nf", f)
                    typeAnswer = Float::class
                } else {
                    val d: Double =
                        random().nextDouble()
                    answer = d
                    nf = StoredField("nf", d)
                    typeAnswer = Double::class
                }
            } else {
                // int/long
                if (random().nextBoolean()) {
                    val i: Int = random().nextInt()
                    answer = i
                    nf = StoredField("nf", i)
                    typeAnswer = Int::class
                } else {
                    val l: Long = random().nextLong()
                    answer = l
                    nf = StoredField("nf", l)
                    typeAnswer = Long::class
                }
            }
            doc.add(nf)
            answers[id] = answer
            /*typeAnswers[id] = typeAnswer*/
            doc.add(StoredField("id", id))
            doc.add(IntPoint("id", id))
            doc.add(NumericDocValuesField("id", id.toLong()))
            w.addDocument(doc)

            typeAnswer
        }
        val r: DirectoryReader = maybeWrapWithMergingReader(w.reader)
        w.close()

        assertEquals(numDocs.toLong(), r.numDocs().toLong())

        for (ctx in r.leaves()) {
            val sub: LeafReader = ctx.reader()
            val ids: NumericDocValues = DocValues.getNumeric(sub, "id")
            val storedFields: StoredFields = sub.storedFields()
            for (docID in 0..<sub.numDocs()) {
                val doc = storedFields.document(docID)
                val f = doc.getField("nf") as Field
                assertTrue(f is StoredField, "got f=$f")
                assertEquals(docID.toLong(), ids.nextDoc().toLong())
                assertEquals(answers[ids.longValue().toInt()], f.numericValue())
                assertEquals(
                    typeAnswers[ids.longValue().toInt()],
                    f.numericValue()!!::class
                )
            }
        }
        r.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testIndexedBit() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val onlyStored = FieldType()
        onlyStored.setStored(true)
        doc.add(Field("field", "value", onlyStored))
        doc.add(StringField("field2", "value",Field.Store.YES))
        w.addDocument(doc)
        val r: IndexReader = maybeWrapWithMergingReader(w.reader)
        val storedFields: StoredFields = r.storedFields()
        w.close()
        assertEquals(
            IndexOptions.NONE,
            storedFields.document(0).getField("field")!!.fieldType().indexOptions()
        )
        assertNotNull(storedFields.document(0).getField("field2")!!.fieldType().indexOptions())
        r.close()
        dir.close()
    }

    @Throws(IOException::class)
    open fun testReadSkip() {
        val dir: Directory = newDirectory()
        val iwConf = newIndexWriterConfig(MockAnalyzer(random()))
        iwConf.setMaxBufferedDocs(RandomNumbers.randomIntBetween(random(), 2, 30))
        val iw = RandomIndexWriter(random(), dir, iwConf)

        val ft = FieldType()
        ft.setStored(true)
        ft.freeze()

        val string: String = TestUtil.randomSimpleString(random(), 50)
        val bytes: ByteArray = string.toByteArray(StandardCharsets.UTF_8)
        val l = if (random().nextBoolean()) random().nextInt(42).toLong() else random().nextLong()
        val i: Int = if (random().nextBoolean()
        ) random()
            .nextInt(42) else random().nextInt()
        val f: Float = random().nextFloat()
        val d: Double = random().nextDouble()

        val fields: MutableList<Field> =
            mutableListOf(
                Field("bytes", bytes, ft),
                Field("string", string, ft),
                StoredField("long", l),
                StoredField("int", i),
                StoredField("float", f),
                StoredField("double", d)
            )

        for (k in 0..99) {
            val doc = Document()
            for (fld in fields) {
                doc.add(fld)
            }
            iw.w.addDocument(doc)
        }
        iw.commit()

        val reader: DirectoryReader = maybeWrapWithMergingReader(DirectoryReader.open(dir))
        val storedFields: StoredFields = reader.storedFields()
        val docID: Int = random().nextInt(100)
        for (fld in fields) {
            val fldName: String = fld.name()
            val sDoc = storedFields.document(docID, mutableSetOf(fldName))
            val sField: IndexableField = sDoc.getField(fldName)!!
            if (Field::class == fld::class) {
                assertEquals(fld.binaryValue(), sField.binaryValue())
                assertEquals(fld.stringValue(), sField.stringValue())
            } else {
                assertEquals(fld.numericValue(), sField.numericValue())
            }
        }
        reader.close()
        iw.close()
        dir.close()
    }

    @Throws(IOException::class)
    open fun testEmptyDocs() {
        val dir: Directory = newDirectory()
        val iwConf = newIndexWriterConfig(MockAnalyzer(random()))
        iwConf.setMaxBufferedDocs(RandomNumbers.randomIntBetween(random(), 2, 30))
        val iw = RandomIndexWriter(random(), dir, iwConf)

        // make sure that the fact that documents might be empty is not a problem
        val emptyDoc = Document()
        val numDocs = if (random()
                .nextBoolean()
        ) 1 else atLeast(1000)
        for (i in 0..<numDocs) {
            iw.addDocument(emptyDoc)
        }
        iw.commit()
        val rd: DirectoryReader =
            maybeWrapWithMergingReader(DirectoryReader.open(dir))
        val storedFields: StoredFields = rd.storedFields()
        for (i in 0..<numDocs) {
            val doc = storedFields.document(i)
            assertNotNull(doc)
            assertTrue(doc.getFields().isEmpty())
        }
        rd.close()

        iw.close()
        dir.close()
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Throws(Exception::class)
    open fun testConcurrentReads() {
        val dir: Directory = newDirectory()
        val iwConf = newIndexWriterConfig(MockAnalyzer(random()))
        iwConf.setMaxBufferedDocs(RandomNumbers.randomIntBetween(random(), 2, 30))
        val iw = RandomIndexWriter(random(), dir, iwConf)

        // make sure the readers are properly cloned
        val doc = Document()
        val field = StringField("fld", "", Field.Store.YES)
        doc.add(field)
        val numDocs: Int = atLeast(1000)
        for (i in 0..<numDocs) {
            field.setStringValue("" + i)
            iw.addDocument(doc)
        }
        iw.commit()

        val rd: DirectoryReader = maybeWrapWithMergingReader(DirectoryReader.open(dir))
        val searcher = IndexSearcher(rd)
        val concurrentReads: Int = atLeast(5)
        val readsPerThread: Int = atLeast(50)
        val readJobs: MutableList<Job> = ArrayList()
        val ex: AtomicReference<Exception?> = AtomicReference(null)
        runBlocking {
            for (i in 0..<concurrentReads) {
                val queries = IntArray(readsPerThread)
                for (j in queries.indices) {
                    queries[j] = random().nextInt(numDocs)
                }
                readJobs.add(
                    launch(Dispatchers.Default) {
                        for (q in queries) {
                            val query: Query = TermQuery(Term("fld", "" + q))
                            try {
                                val storedFields: StoredFields = rd.storedFields()
                                val topDocs: TopDocs = searcher.search(query, 1)
                                check(topDocs.totalHits.value == 1L) { "Expected 1 hit, got " + topDocs.totalHits.value }
                                val sdoc = storedFields.document(topDocs.scoreDocs[0].doc)
                                check(sdoc.get("fld") != null) { "Could not find document $q" }
                                check(q.toString() == sdoc.get("fld")) { "Expected $q, but got " + sdoc.get("fld") }
                            } catch (e: Exception) {
                                ex.compareAndSet(null, e)
                            }
                        }
                    }
                )
            }
            for (job in readJobs) {
                job.join()
            }
        }
        rd.close()
        val firstException = ex.load()
        if (firstException != null) {
            throw firstException
        }

        iw.close()
        dir.close()
    }

    private fun randomByteArray(length: Int, max: Int): ByteArray {
        val result = ByteArray(length)
        for (i in 0..<length) {
            result[i] = random().nextInt(max).toByte()
        }
        return result
    }

    @Throws(IOException::class)
    open fun testWriteReadMerge() {
        // get another codec, other than the default: so we are merging segments across different codecs
        val otherCodec: Codec
        if ("SimpleText" == Codec.default.name) {
            otherCodec = TestUtil.getDefaultCodec()
        } else {
            otherCodec = SimpleTextCodec()
        }
        val dir: Directory = newDirectory()
        var iwConf = newIndexWriterConfig(MockAnalyzer(random()))
        iwConf.setMaxBufferedDocs(RandomNumbers.randomIntBetween(random(), 2, 30))
        var iw = RandomIndexWriter(random(), dir, iwConf)

        val docCount: Int = atLeast(200)
        val data = kotlin.arrayOfNulls<Array<ByteArray>>(docCount)
        for (i in 0..<docCount) {
            val fieldCount: Int =
                if (rarely())
                    RandomNumbers.randomIntBetween(random(), 1, 500)
                else
                    RandomNumbers.randomIntBetween(random(), 1, 5)
            data[i] = Array(fieldCount)
            /*for (j in 0..<fieldCount)*/{
                val length: Int =
                    if (rarely()) random()
                        .nextInt(1000) else random()
                        .nextInt(10)
                val max = if (rarely()) 256 else 2
                /*data[i]!![j] =*/ randomByteArray(length, max)
            }
        }

        val type = FieldType(StringField.TYPE_STORED)
        type.setIndexOptions(IndexOptions.NONE)
        type.freeze()
        val id = IntPoint("id", 0)
        val idStored = StoredField("id", 0)
        for (i in data.indices) {
            val doc = Document()
            doc.add(id)
            doc.add(idStored)
            id.setIntValue(i)
            idStored.setIntValue(i)
            for (j in data[i]!!.indices) {
                val f = Field("bytes$j", data[i]!![j], type)
                doc.add(f)
            }
            iw.w.addDocument(doc)
            if (random().nextBoolean() && (i % (data.size / 10) == 0)) {
                iw.w.close()
                val iwConfNew =
                    newIndexWriterConfig(
                        MockAnalyzer(random())
                    )
                // test merging against a non-compressing codec
                if (iwConf.codec === otherCodec) {
                    iwConfNew.setCodec(Codec.default)
                } else {
                    iwConfNew.setCodec(otherCodec)
                }
                iwConf = iwConfNew
                iw = RandomIndexWriter(random(), dir, iwConf)
            }
        }

        for (i in 0..9) {
            val min: Int = random().nextInt(data.size)
            val max: Int = min + random().nextInt(20)
            iw.deleteDocuments(IntPoint.newRangeQuery("id", min, max - 1))
        }

        iw.forceMerge(2) // force merges with deletions

        iw.commit()

        val ir: DirectoryReader =
            maybeWrapWithMergingReader(DirectoryReader.open(dir))
        val storedFields: StoredFields = ir.storedFields()
        assertTrue(ir.numDocs() > 0)
        var numDocs = 0
        for (i in 0..<ir.maxDoc()) {
            val doc = storedFields.document(i)
            if (doc == null) {
                continue
            }
            ++numDocs
            val docId = doc.getField("id")!!.numericValue()!!.toInt()
            assertEquals(
                (data[docId]!!.size + 1).toLong(),
                doc.getFields().size.toLong()
            )
            for (j in data[docId]!!.indices) {
                val arr = data[docId]!![j]
                val arr2Ref = doc.getBinaryValue("bytes$j")!!
                val arr2: ByteArray = BytesRef.deepCopyOf(arr2Ref).bytes
                assertArrayEquals(arr, arr2)
            }
        }
        assertTrue(ir.numDocs() <= numDocs)
        ir.close()

        iw.deleteAll()
        iw.commit()
        iw.forceMerge(1)

        iw.close()
        dir.close()
    }

    /** A dummy filter reader that reverse the order of documents in stored fields.  */
    private class DummyFilterLeafReader(`in`: LeafReader) :
        FilterLeafReader(`in`) {
        @Throws(IOException::class)
        override fun storedFields(): StoredFields {
            val orig: StoredFields = `in`.storedFields()
            return object : StoredFields() {
                @Throws(IOException::class)
                override fun document(
                    docID: Int,
                    visitor: StoredFieldVisitor
                ) {
                    orig.document(maxDoc() - 1 - docID, visitor)
                }
            }
        }

        override val coreCacheHelper: CacheHelper?
            get() = null

        override val readerCacheHelper: CacheHelper?
            get() = null
    }

    private class DummyFilterDirectoryReader(`in`: DirectoryReader) :
        FilterDirectoryReader(
            `in`,
            object : SubReaderWrapper() {
                override fun wrap(reader: LeafReader): LeafReader {
                    return DummyFilterLeafReader(reader)
                }
            }) {
        @Throws(IOException::class)
        override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
            return DummyFilterDirectoryReader(`in`)
        }

        override val readerCacheHelper: CacheHelper?
            get() = null
    }

    @Throws(IOException::class)
    open fun testMergeFilterReader() {
        val dir: Directory = newDirectory()
        var w = RandomIndexWriter(random(),dir)
        val numDocs: Int = atLeast(200)
        val stringValues = Array(10)
        /*for (i in stringValues.indices) */{
            /*stringValues[i] = */RandomStrings.randomRealisticUnicodeOfLength(
                random(),
                10
            )
        }
        val docs: Array<Document> = Array(numDocs)
        /*for (i in 0..<numDocs)*/{ i ->
            val doc = Document()
            doc.add(StringField("to_delete", if (random().nextBoolean()) "yes" else "no", Field.Store.NO))
            doc.add(StoredField("id", i))
            doc.add(StoredField("i", random().nextInt(50)))
            doc.add(StoredField("l", random().nextLong()))
            doc.add(StoredField("d", random().nextDouble()))
            doc.add(StoredField("f", random().nextFloat()))
            doc.add(StoredField("s", RandomPicks.randomFrom(random(), stringValues)))
            val value = BytesRef(RandomPicks.randomFrom(random(), stringValues))
            doc.add(StoredField("b", value))
            doc.add(StoredField("b2", StoredFieldDataInput(ByteArrayDataInput(value.bytes, value.offset, value.length))))
            w.addDocument(doc)
            /*docs[i] =*/ doc
        }
        if (random().nextBoolean()) {
            w.deleteDocuments(Term("to_delete", "yes"))
        }
        w.commit()
        w.close()

        var reader: DirectoryReader = DummyFilterDirectoryReader(maybeWrapWithMergingReader(DirectoryReader.open(dir)))

        val dir2: Directory = newDirectory()
        w = RandomIndexWriter(random(), dir2)
        TestUtil.addIndexesSlowly(w.w, reader)
        reader.close()
        dir.close()

        reader = maybeWrapWithMergingReader(w.reader)
        val storedFields: StoredFields = reader.storedFields()
        for (i in 0..<reader.maxDoc()) {
            val doc = storedFields.document(i)
            val id = doc.getField("id")!!.numericValue()!!.toInt()
            val expected = docs[id]
            assertEquals(expected.get("s"), doc.get("s"))
            assertEquals(expected.getField("i")!!.numericValue(), doc.getField("i")!!.numericValue())
            assertEquals(expected.getField("l")!!.numericValue(), doc.getField("l")!!.numericValue())
            assertEquals(expected.getField("d")!!.numericValue(), doc.getField("d")!!.numericValue())
            assertEquals(expected.getField("f")!!.numericValue(), doc.getField("f")!!.numericValue())
            assertEquals(expected.getField("b")!!.binaryValue(), doc.getField("b")!!.binaryValue())
            // The value is the same for fields "b" and "b2". Read the expected value from "b" as "b2" was
            // consumed during indexing
            assertEquals(expected.getField("b")!!.binaryValue(), doc.getField("b2")!!.binaryValue())
        }

        reader.close()
        w.close()
        TestUtil.checkIndex(dir2)
        dir2.close()
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(IOException::class)
    open fun testBigDocuments() {
        // "big" as "much bigger than the chunk size"
        // for this test we force an FS dir
        // we can't just use newFSDirectory, because this test doesn't really index anything.
        // so if we get NRTCachingDir+SimpleText, we make massive stored fields and OOM (LUCENE-4484)
        val dir: Directory =
            MockDirectoryWrapper(random(), MMapDirectory(createTempDir("testBigDocuments")))
        val iwConf = newIndexWriterConfig(MockAnalyzer(random()))
        iwConf.setMaxBufferedDocs(RandomNumbers.randomIntBetween(random(), 2, 30))
        val iw = RandomIndexWriter(random(), dir, iwConf)

        if (dir is MockDirectoryWrapper) {
            dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
        }

        val emptyDoc = Document() // emptyDoc
        val bigDoc1 = Document() // lot of small fields
        val bigDoc2 = Document() // 1 very big field

        val idField = StringField("id", "", Field.Store.NO)
        emptyDoc.add(idField)
        bigDoc1.add(idField)
        bigDoc2.add(idField)

        val onlyStored = FieldType(StringField.TYPE_STORED)
        onlyStored.setIndexOptions(IndexOptions.NONE)

        val smallField = Field("fld", randomByteArray(random().nextInt(10), 256), onlyStored)
        val numFields: Int = RandomNumbers.randomIntBetween(random(), 500000, 1000000)
        for (i in 0..<numFields) {
            bigDoc1.add(smallField)
        }

        val bigField = Field("fld", randomByteArray(RandomNumbers.randomIntBetween(random(), 1000000, 5000000), 2), onlyStored)
        bigDoc2.add(bigField)

        val numDocs: Int = atLeast(5)
        val docs: Array<Document> = Array(numDocs)
        /*for (i in 0..<numDocs)*/{
            /*docs[i] =*/
                RandomPicks.randomFrom(random(), mutableListOf(emptyDoc, bigDoc1, bigDoc2))
        }
        for (i in 0..<numDocs) {
            idField.setStringValue("" + i)
            iw.addDocument(docs[i])
            if (random().nextInt(numDocs) == 0) {
                iw.commit()
            }
        }
        iw.commit()
        iw.forceMerge(1) // look at what happens when big docs are merged
        val rd: DirectoryReader = maybeWrapWithMergingReader(DirectoryReader.open(dir))
        val searcher = IndexSearcher(rd)
        val storedFields: StoredFields = rd.storedFields()
        for (i in 0..<numDocs) {
            val query: Query = TermQuery(Term("id", "" + i))
            val topDocs: TopDocs = searcher.search(query, 1)
            assertEquals(1, topDocs.totalHits.value, "" + i)
            val doc = storedFields.document(topDocs.scoreDocs[0].doc)
            assertNotNull(doc)
            val fieldValues: Array<IndexableField> = doc.getFields("fld")
            assertEquals(docs[i].getFields("fld").size.toLong(), fieldValues.size.toLong())
            if (fieldValues.isNotEmpty()) {
                assertEquals(docs[i].getFields("fld")[0].binaryValue(), fieldValues[0].binaryValue())
            }
        }
        rd.close()
        iw.close()
        dir.close()
    }

    @Throws(IOException::class)
    open fun testBulkMergeWithDeletes() {
        val numDocs: Int = atLeast(200)
        val dir: Directory = newDirectory()
        var w = RandomIndexWriter(random(), dir, newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE))
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.YES))
            doc.add(StoredField("f", TestUtil.randomSimpleString(random())))
            w.addDocument(doc)
        }
        val deleteCount: Int = TestUtil.nextInt(
            random(),
            5,
            numDocs
        )
        for (i in 0..<deleteCount) {
            val id: Int = random().nextInt(numDocs)
            w.deleteDocuments(Term("id", id.toString()))
        }
        w.commit()
        w.close()
        w = RandomIndexWriter(random(), dir)
        w.forceMerge(TestUtil.nextInt(random(), 1, 3))
        w.commit()
        w.close()
        TestUtil.checkIndex(dir)
        dir.close()
    }

    /** mix up field numbers, merge, and check that data is correct  */
    @Throws(Exception::class)
    open fun testMismatchedFields() {
        val dirs: Array<Directory> = Array(10)
        /*for (i in dirs.indices)*/{
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(/*null*/)
            val iw = IndexWriter(dir, iwc)
            val doc = Document()
            for (j in 0..9) {
                // add fields where name=value (e.g. 3=3) so we can detect if stuff gets screwed up.
                doc.add(StringField(j.toString(), j.toString(), Field.Store.YES))
            }
            for (j in 0..9) {
                iw.addDocument(doc)
            }

            var reader: DirectoryReader = maybeWrapWithMergingReader(DirectoryReader.open(iw))
            // mix up fields explicitly
            if (random().nextBoolean()) {
                reader = MismatchedDirectoryReader(
                    reader,
                    random()
                )
            }
            val dirForArray /*dirs[i]*/ = newDirectory()
            val adder = IndexWriter(
                dirForArray/*dirs[i]*/,
                IndexWriterConfig(/*null*/)
            )
            TestUtil.addIndexesSlowly(adder, reader)
            adder.commit()
            adder.close()

            IOUtils.close(reader, iw, dir)

            dirForArray
        }

        val everything: Directory = newDirectory()
        val iw = IndexWriter(everything, IndexWriterConfig(/*null*/))
        iw.addIndexes(*dirs)
        iw.forceMerge(1)

        val ir: LeafReader = getOnlyLeafReader(DirectoryReader.open(iw))
        val storedFields: StoredFields = ir.storedFields()
        for (i in 0..<ir.maxDoc()) {
            val doc = storedFields.document(i)
            assertEquals(10, doc.getFields().size.toLong())
            for (j in 0..9) {
                assertEquals(j.toString(), doc.get(j.toString()))
            }
        }

        IOUtils.close(iw, ir, everything)
        IOUtils.close(*dirs)
    }

    @Throws(Exception::class)
    open fun testRandomStoredFieldsWithIndexSort() {
        val sortFields: Array<SortField>
        if (random().nextBoolean()) {
            sortFields =
                arrayOf(
                    SortField("sort-1", SortField.Type.LONG),
                    SortField("sort-2", SortField.Type.INT)
                )
        } else {
            sortFields = arrayOf(SortField("sort-1", SortField.Type.LONG))
        }
        val storedFields: MutableList<String> = mutableListOf()
        val numFields: Int = TestUtil.nextInt(random(), 1, 10)
        for (i in 0..<numFields) {
            storedFields.add("f-$i")
        }
        val storeType =
            FieldType(TextField.TYPE_STORED)
        storeType.setStored(true)
        val documentFactory: (String) -> Document /*java.util.function.Function<String, Document>*/ =
            /*java.util.function.Function*/{ id: String ->
                val doc = Document()
                doc.add(StringField("id", id, if (random().nextBoolean()) Field.Store.YES else Field.Store.NO))
                if (random().nextInt(100) <= 5) {
                    storedFields.shuffle(random())
                }
                for (fieldName in storedFields) {
                    if (random().nextBoolean()) {
                        val s: String = TestUtil.randomUnicodeString(random(), 100)
                        doc.add(newField(fieldName, s, storeType))
                    }
                }
                for (sortField in sortFields) {
                    doc.add(NumericDocValuesField(sortField.field!!, TestUtil.nextInt(random(), 0, 1000).toLong()))
                }
                doc
            }

        val docs: MutableMap<String, Document> = HashMap()
        val numDocs: Int = atLeast(100)
        for (i in 0..<numDocs) {
            val id = i.toString()
            docs[id] = documentFactory(id)
        }

        val dir: Directory = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setMaxBufferedDocs(TestUtil.nextInt(random(), 5, 20))
        iwc.setIndexSort(Sort(*sortFields))
        val iw = RandomIndexWriter(random(), dir, iwc)
        val addedIds: MutableList<String> = mutableListOf()
        val verifyStoreFields: () -> Unit =
            verifyStoreFields@{
                if (addedIds.isEmpty()) {
                    return@verifyStoreFields
                }
                try {
                    maybeWrapWithMergingReader(iw.reader).use { reader ->
                        val actualStoredFields: StoredFields = reader.storedFields()
                        val searcher =
                            IndexSearcher(reader)
                        val iters: Int = TestUtil.nextInt(random(), 1, 10)
                        for (i in 0..<iters) {
                            val testID = addedIds[random().nextInt(addedIds.size)]
                            if (VERBOSE) {
                                println("TEST: test id=$testID")
                            }
                            val hits: TopDocs = searcher.search(TermQuery(Term("id", testID)), 1)
                            assertEquals(1, hits.totalHits.value)
                            val expectedFields: MutableList<IndexableField> =
                                docs[testID]!!.getFields().filter { f: IndexableField ->
                                    f.fieldType().stored()
                                }.toMutableList()
                            val actualDoc = actualStoredFields.document(hits.scoreDocs[0].doc)
                            assertEquals(expectedFields.size.toLong(), actualDoc.getFields().size.toLong())
                            for (expectedField in expectedFields) {
                                val actualFields: Array<IndexableField> = actualDoc.getFields(expectedField.name())
                                assertEquals(1, actualFields.size.toLong())
                                assertEquals(expectedField.stringValue(), actualFields[0].stringValue())
                            }
                        }
                    }
                } catch (e: IOException) {
                    throw UncheckedIOException(e)
                }
            }
        val ids: MutableList<String> = ArrayList(docs.keys)
        ids.shuffle(random())
        for (id in ids) {
            if (random().nextInt(100) < 5) {
                // add via foreign reader
                val otherIwc = newIndexWriterConfig()
                otherIwc.setIndexSort(Sort(*sortFields))
                newDirectory().use { otherDir ->
                    RandomIndexWriter(random(), otherDir,otherIwc)
                        .use { otherIw ->
                        otherIw.addDocument(docs[id]!!)
                        otherIw.reader.use { otherReader ->
                            TestUtil.addIndexesSlowly(iw.w, otherReader)
                        }
                    }
                }
            } else {
                // add normally
                iw.addDocument(docs[id]!!)
            }
            addedIds.add(id)
            if (random().nextInt(100) < 5) {
                val deletingId = addedIds.removeAt(
                    random().nextInt(addedIds.size)
                )
                if (random().nextBoolean()) {
                    iw.deleteDocuments(TermQuery(Term("id", deletingId)))
                    addedIds.remove(deletingId)
                } else {
                    val newDoc = documentFactory(deletingId)
                    docs[deletingId] = newDoc
                    iw.updateDocument(Term("id", deletingId), newDoc)
                }
            }
            if (random().nextInt(100) < 5) {
                verifyStoreFields()
            }
            if (random().nextInt(100) < 2) {
                iw.forceMerge(TestUtil.nextInt(random(), 1, 3))
            }
        }
        verifyStoreFields()
        iw.forceMerge(TestUtil.nextInt(random(), 1, 3))
        verifyStoreFields()
        IOUtils.close(iw, dir)
    }

    /** Test realistic data, which typically compresses better than random data.  */
    @Throws(IOException::class)
    open fun testLineFileDocs() {
        // Use an FS dir and a non-randomized IWC to not slow down indexing
        newFSDirectory(createTempDir())
            .use { dir ->
                LineFileDocs(random())
                    .use { docs ->
                        IndexWriter(dir, IndexWriterConfig())
                            .use { w ->
                            val numDocs: Int =
                                atLeast(10000)
                            for (i in 0..<numDocs) {
                                // Only keep stored fields
                                val doc = docs.nextDoc()
                                val storedDoc = Document()
                                for (field in doc.getFields()) {
                                    if (field.fieldType().stored()) {
                                        var storedField: IndexableField = field
                                        if (field.stringValue() != null) {
                                            // Disable indexing
                                            storedField = StoredField(field.name(), field.stringValue()!!)
                                        }
                                        storedDoc.add(storedField)
                                    }
                                }

                                w.addDocument(storedDoc)
                            }
                            w.forceMerge(1)
                        }
                    }
                TestUtil.checkIndex(dir)
            }
    }
}
