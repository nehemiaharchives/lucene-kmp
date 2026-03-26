package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.InvertableType
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StoredValue
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A silly test showing how to index documents without using Lucene's core
 * Document nor Field class.
 */
class TestIndexableField : LuceneTestCase() {
    private class MyField(private val counter: Int) : IndexableField {
        private val fieldType: IndexableFieldType =
            object : IndexableFieldType {
                override fun stored(): Boolean {
                    return counter % 2 == 0 || counter % 10 == 3
                }

                override fun tokenized(): Boolean {
                    return true
                }

                override fun storeTermVectors(): Boolean {
                    return indexOptions() != IndexOptions.NONE && counter % 2 == 1 && counter % 10 != 9
                }

                override fun storeTermVectorOffsets(): Boolean {
                    return storeTermVectors() && counter % 10 != 9
                }

                override fun storeTermVectorPositions(): Boolean {
                    return storeTermVectors() && counter % 10 != 9
                }

                override fun storeTermVectorPayloads(): Boolean {
                    return storeTermVectors() && counter % 10 != 9
                }

                override fun omitNorms(): Boolean {
                    return false
                }

                override fun indexOptions(): IndexOptions {
                    return if (counter % 10 == 3) {
                        IndexOptions.NONE
                    } else {
                        IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
                    }
                }

                override fun docValuesType(): DocValuesType {
                    return DocValuesType.NONE
                }

                override fun docValuesSkipIndexType(): DocValuesSkipIndexType {
                    return DocValuesSkipIndexType.NONE
                }

                override fun pointDimensionCount(): Int {
                    return 0
                }

                override fun pointIndexDimensionCount(): Int {
                    return 0
                }

                override fun pointNumBytes(): Int {
                    return 0
                }

                override fun vectorDimension(): Int {
                    return 0
                }

                override fun vectorEncoding(): VectorEncoding {
                    return VectorEncoding.FLOAT32
                }

                override fun vectorSimilarityFunction(): VectorSimilarityFunction {
                    return VectorSimilarityFunction.EUCLIDEAN
                }

                override val attributes: MutableMap<String, String>?
                    get() = null
            }

        override fun name(): String {
            return "f$counter"
        }

        override fun binaryValue(): BytesRef? {
            if (counter % 10 == 3) {
                val bytes = ByteArray(10)
                for (idx in bytes.indices) {
                    bytes[idx] = (counter + idx).toByte()
                }
                return newBytesRef(bytes, 0, bytes.size)
            }
            return null
        }

        override fun stringValue(): String? {
            val fieldID = counter % 10
            return if (fieldID != 3 && fieldID != 7) {
                "text $counter"
            } else {
                null
            }
        }

        override fun readerValue(): Reader? {
            return if (counter % 10 == 7) {
                StringReader("text $counter")
            } else {
                null
            }
        }

        override fun numericValue(): Number? {
            return null
        }

        override fun fieldType(): IndexableFieldType {
            return fieldType
        }

        override fun tokenStream(analyzer: Analyzer, reuse: TokenStream?): TokenStream? {
            val reader = readerValue()
            return if (reader != null) {
                analyzer.tokenStream(name(), reader)
            } else {
                analyzer.tokenStream(name(), StringReader(stringValue()!!))
            }
        }

        override fun storedValue(): StoredValue? {
            return when {
                stringValue() != null -> StoredValue(stringValue()!!)
                binaryValue() != null -> StoredValue(binaryValue()!!)
                else -> null
            }
        }

        override fun invertableType(): InvertableType {
            return InvertableType.TOKEN_STREAM
        }
    }

    /**
     * Silly test showing how to index documents w/o using Lucene's core
     * Document nor Field class
     */
    @Test
    fun testArbitraryFields() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val NUM_DOCS = atLeast(27)
        if (VERBOSE) {
            println("TEST: $NUM_DOCS docs")
        }
        val fieldsPerDoc = IntArray(NUM_DOCS)
        var baseCount = 0

        for (docCount in 0 until NUM_DOCS) {
            val fieldCount = TestUtil.nextInt(random(), 1, 17)
            fieldsPerDoc[docCount] = fieldCount - 1

            if (VERBOSE) {
                println("TEST: $fieldCount fields in doc $docCount")
            }

            val finalDocCount = docCount
            val finalBaseCount = baseCount
            baseCount += fieldCount - 1

            val d: Iterable<IndexableField> =
                object : Iterable<IndexableField> {
                    override fun iterator(): Iterator<IndexableField> {
                        return object : Iterator<IndexableField> {
                            var fieldUpto = 0

                            override fun hasNext(): Boolean {
                                return fieldUpto < fieldCount
                            }

                            override fun next(): IndexableField {
                                assert(fieldUpto < fieldCount)
                                return if (fieldUpto == 0) {
                                    fieldUpto = 1
                                    newStringField("id", "$finalDocCount", Field.Store.YES)
                                } else {
                                    MyField(finalBaseCount + (fieldUpto++ - 1))
                                }
                            }
                        }
                    }
                }
            w.addDocument(d)
        }

        val r = w.reader
        w.close()

        val s = newSearcher(r)
        val storedFields = s.storedFields()
        val termVectors = r.termVectors()
        var counter = 0
        for (id in 0 until NUM_DOCS) {
            if (VERBOSE) {
                println(
                    "TEST: verify doc id=$id (${fieldsPerDoc[id]} fields) counter=$counter"
                )
            }

            val hits: TopDocs = s.search(TermQuery(Term("id", "$id")), 1)
            assertEquals(1L, hits.totalHits.value)
            val docID = hits.scoreDocs[0].doc
            val doc: Document = storedFields.document(docID)
            val endCounter = counter + fieldsPerDoc[id]
            while (counter < endCounter) {
                val name = "f$counter"
                val fieldID = counter % 10

                val stored = counter % 2 == 0 || fieldID == 3
                val binary = fieldID == 3
                val indexed = fieldID != 3

                val stringValue: String? =
                    if (fieldID != 3 && fieldID != 9) {
                        "text $counter"
                    } else {
                        null
                    }

                if (stored) {
                    val f: IndexableField? = doc.getField(name)
                    assertNotNull(f)
                    if (binary) {
                        val b = f.binaryValue()
                        assertNotNull(b)
                        assertEquals(10, b.length)
                        for (idx in 0 until 10) {
                            assertEquals((idx + counter).toByte(), b.bytes[b.offset + idx])
                        }
                    } else {
                        assert(stringValue != null)
                        assertEquals(stringValue, f.stringValue())
                    }
                }

                if (indexed) {
                    val tv = counter % 2 == 1 && fieldID != 9
                    if (tv) {
                        val vectors = termVectors.get(docID)
                        val tfv = vectors?.terms(name)
                        assertNotNull(tfv)
                        val termsEnum = tfv.iterator()
                        assertEquals(newBytesRef("$counter"), termsEnum.next())
                        assertEquals(1, termsEnum.totalTermFreq())
                        var dpEnum = termsEnum.postings(null, PostingsEnum.ALL.toInt())!!
                        assertTrue(dpEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
                        assertEquals(1, dpEnum.freq())
                        assertEquals(1, dpEnum.nextPosition())

                        assertEquals(newBytesRef("text"), termsEnum.next())
                        assertEquals(1, termsEnum.totalTermFreq())
                        dpEnum = termsEnum.postings(dpEnum, PostingsEnum.ALL.toInt())!!
                        assertTrue(dpEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
                        assertEquals(1, dpEnum.freq())
                        assertEquals(0, dpEnum.nextPosition())

                        assertNull(termsEnum.next())

                        // TODO: offsets
                    } else {
                        val vectors = termVectors.get(docID)
                        assertTrue(vectors == null || vectors.terms(name) == null)
                    }

                    var bq = BooleanQuery.Builder()
                    bq.add(TermQuery(Term("id", "$id")), BooleanClause.Occur.MUST)
                    bq.add(TermQuery(Term(name, "text")), BooleanClause.Occur.MUST)
                    val hits2 = s.search(bq.build(), 1)
                    assertEquals(1L, hits2.totalHits.value)
                    assertEquals(docID, hits2.scoreDocs[0].doc)

                    bq = BooleanQuery.Builder()
                    bq.add(TermQuery(Term("id", "$id")), BooleanClause.Occur.MUST)
                    bq.add(TermQuery(Term(name, "$counter")), BooleanClause.Occur.MUST)
                    val hits3 = s.search(bq.build(), 1)
                    assertEquals(1L, hits3.totalHits.value)
                    assertEquals(docID, hits3.scoreDocs[0].doc)
                }

                counter++
            }
        }

        r.close()
        dir.close()
    }

    private class CustomField : IndexableField {
        override fun binaryValue(): BytesRef? {
            return null
        }

        override fun stringValue(): String {
            return "foobar"
        }

        override fun readerValue(): Reader? {
            return null
        }

        override fun numericValue(): Number? {
            return null
        }

        override fun name(): String {
            return "field"
        }

        override fun tokenStream(analyzer: Analyzer, reuse: TokenStream?): TokenStream? {
            return null
        }

        override fun fieldType(): IndexableFieldType {
            val ft = FieldType(StoredField.TYPE)
            ft.setStoreTermVectors(true)
            ft.freeze()
            return ft
        }

        override fun storedValue(): StoredValue? {
            return null
        }

        override fun invertableType(): InvertableType {
            return InvertableType.TOKEN_STREAM
        }
    }

    // LUCENE-5611
    @Test
    fun testNotIndexedTermVectors() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        expectThrows(
            IllegalArgumentException::class,
            { w.addDocument(listOf(CustomField())) },
        )
        w.close()
        dir.close()
    }
}
