package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.BinaryPoint
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoubleDocValuesField
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.VectorUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class TestFieldExistsQuery : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testDocValuesRewriteWithTermsPresent() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val numDocs = atLeast(100)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(DoubleDocValuesField("f", 2.0))
            doc.add(StringField("f", if (random().nextBoolean()) "yes" else "no", Store.NO))
            iw.addDocument(doc)
        }
        iw.commit()
        val reader = iw.getReader(true, false)
        iw.close()

        assertTrue(FieldExistsQuery("f").rewrite(newSearcher(reader)) is MatchAllDocsQuery)
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDocValuesRewriteWithPointValuesPresent() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val numDocs = atLeast(100)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(BinaryPoint("dim", arrayOf(ByteArray(4), ByteArray(4))))
            doc.add(DoubleDocValuesField("dim", 2.0))
            iw.addDocument(doc)
        }
        iw.commit()
        val reader = iw.getReader(true, false)
        iw.close()

        assertTrue(FieldExistsQuery("dim").rewrite(newSearcher(reader)) is MatchAllDocsQuery)
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDocValuesNoRewrite() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val numDocs = atLeast(100)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(DoubleDocValuesField("dim", 2.0))
            doc.add(BinaryPoint("dim", arrayOf(ByteArray(4), ByteArray(4))))
            iw.addDocument(doc)
        }
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(DoubleDocValuesField("f", 2.0))
            doc.add(StringField("f", if (random().nextBoolean()) "yes" else "no", Store.NO))
            iw.addDocument(doc)
        }
        iw.commit()
        val reader = iw.getReader(true, false)
        iw.close()
        val searcher = newSearcher(reader)

        assertFalse(FieldExistsQuery("dim").rewrite(searcher) is MatchAllDocsQuery)
        assertFalse(FieldExistsQuery("f").rewrite(searcher) is MatchAllDocsQuery)
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDocValuesNoRewriteWithDocValues() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val numDocs = atLeast(100)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(NumericDocValuesField("dv1", 1))
            doc.add(SortedNumericDocValuesField("dv2", 1))
            doc.add(SortedNumericDocValuesField("dv2", 2))
            iw.addDocument(doc)
        }
        iw.commit()
        val reader = iw.getReader(true, false)
        iw.close()
        val searcher = newSearcher(reader)

        assertFalse(FieldExistsQuery("dv1").rewrite(searcher) is MatchAllDocsQuery)
        assertFalse(FieldExistsQuery("dv2").rewrite(searcher) is MatchAllDocsQuery)
        assertFalse(FieldExistsQuery("dv3").rewrite(searcher) is MatchAllDocsQuery)
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDocValuesRandom() {
        val iters = atLeast(10)
        for (iter in 0..<iters) {
            val dir = newDirectory()
            val iw = RandomIndexWriter(random(), dir)
            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                val doc = Document()
                val hasValue = random().nextBoolean()
                if (hasValue) {
                    doc.add(NumericDocValuesField("dv1", 1))
                    doc.add(SortedNumericDocValuesField("dv2", 1))
                    doc.add(SortedNumericDocValuesField("dv2", 2))
                    doc.add(StringField("has_value", "yes", Store.NO))
                }
                doc.add(StringField("f", if (random().nextBoolean()) "yes" else "no", Store.NO))
                iw.addDocument(doc)
            }
            if (random().nextBoolean()) {
                iw.deleteDocuments(TermQuery(Term("f", "no")))
            }
            iw.commit()
            val reader = iw.getReader(true, false)
            val searcher = newSearcher(reader)
            iw.close()

            assertSameMatches(searcher, TermQuery(Term("has_value", "yes")), FieldExistsQuery("dv1"), false)
            assertSameMatches(searcher, TermQuery(Term("has_value", "yes")), FieldExistsQuery("dv2"), false)

            reader.close()
            dir.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testDocValuesApproximation() {
        val iters = atLeast(10)
        for (iter in 0..<iters) {
            val dir = newDirectory()
            val iw = RandomIndexWriter(random(), dir)
            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                val doc = Document()
                val hasValue = random().nextBoolean()
                if (hasValue) {
                    doc.add(NumericDocValuesField("dv1", 1))
                    doc.add(SortedNumericDocValuesField("dv2", 1))
                    doc.add(SortedNumericDocValuesField("dv2", 2))
                    doc.add(StringField("has_value", "yes", Store.NO))
                }
                doc.add(StringField("f", if (random().nextBoolean()) "yes" else "no", Store.NO))
                iw.addDocument(doc)
            }
            if (random().nextBoolean()) {
                iw.deleteDocuments(TermQuery(Term("f", "no")))
            }
            iw.commit()
            val reader = iw.getReader(true, false)
            val searcher = newSearcher(reader)
            iw.close()

            val ref = BooleanQuery.Builder()
            ref.add(TermQuery(Term("f", "yes")), Occur.MUST)
            ref.add(TermQuery(Term("has_value", "yes")), Occur.FILTER)

            val bq1 = BooleanQuery.Builder()
            bq1.add(TermQuery(Term("f", "yes")), Occur.MUST)
            bq1.add(FieldExistsQuery("dv1"), Occur.FILTER)
            assertSameMatches(searcher, ref.build(), bq1.build(), true)

            val bq2 = BooleanQuery.Builder()
            bq2.add(TermQuery(Term("f", "yes")), Occur.MUST)
            bq2.add(FieldExistsQuery("dv2"), Occur.FILTER)
            assertSameMatches(searcher, ref.build(), bq2.build(), true)

            reader.close()
            dir.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testDocValuesScore() {
        val iters = atLeast(10)
        for (iter in 0..<iters) {
            val dir = newDirectory()
            val iw = RandomIndexWriter(random(), dir)
            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                val doc = Document()
                val hasValue = random().nextBoolean()
                if (hasValue) {
                    doc.add(NumericDocValuesField("dv1", 1))
                    doc.add(SortedNumericDocValuesField("dv2", 1))
                    doc.add(SortedNumericDocValuesField("dv2", 2))
                    doc.add(StringField("has_value", "yes", Store.NO))
                }
                doc.add(StringField("f", if (random().nextBoolean()) "yes" else "no", Store.NO))
                iw.addDocument(doc)
            }
            if (random().nextBoolean()) {
                iw.deleteDocuments(TermQuery(Term("f", "no")))
            }
            iw.commit()
            val reader = iw.getReader(true, false)
            val searcher = newSearcher(reader)
            iw.close()

            val boost = random().nextFloat() * 10
            val ref = BoostQuery(ConstantScoreQuery(TermQuery(Term("has_value", "yes"))), boost)

            val q1 = BoostQuery(FieldExistsQuery("dv1"), boost)
            assertSameMatches(searcher, ref, q1, true)

            val q2 = BoostQuery(FieldExistsQuery("dv2"), boost)
            assertSameMatches(searcher, ref, q2, true)

            reader.close()
            dir.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testDocValuesMissingField() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        iw.addDocument(Document())
        iw.commit()
        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        iw.close()
        assertEquals(0, searcher.count(FieldExistsQuery("f")))
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDocValuesAllDocsHaveField() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(NumericDocValuesField("f", 1))
        iw.addDocument(doc)
        iw.commit()
        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        iw.close()
        assertEquals(1, searcher.count(FieldExistsQuery("f")))
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDocValuesFieldExistsButNoDocsHaveField() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(NumericDocValuesField("f", 1))
        iw.addDocument(doc)
        iw.commit()
        iw.addDocument(Document())
        iw.commit()
        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        iw.close()
        assertEquals(1, searcher.count(FieldExistsQuery("f")))
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDocValuesQueryMatchesCount() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val randomNumDocs = TestUtil.nextInt(random(), 11, 100)
        var numMatchingDocs = 0

        for (i in 0..<randomNumDocs) {
            val doc = Document()
            if (i == 0 || i == 10 || random().nextBoolean()) {
                doc.add(LongPoint("long", i.toLong()))
                doc.add(NumericDocValuesField("long", i.toLong()))
                doc.add(StringField("string", "value", Store.NO))
                doc.add(SortedDocValuesField("string", newBytesRef("value")))
                numMatchingDocs++
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)

        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)

        assertSameCount(reader, searcher, "long", numMatchingDocs)
        assertSameCount(reader, searcher, "string", numMatchingDocs)
        assertSameCount(reader, searcher, "doesNotExist", 0)

        w.w.config.mergePolicy = NoMergePolicy.INSTANCE
        w.deleteDocuments(LongPoint.newRangeQuery("long", 0L, 9L))
        val reader2 = w.getReader(true, false)
        val searcher2 = IndexSearcher(reader2)
        val testQuery = FieldExistsQuery("long")
        val weight2 = searcher2.createWeight(testQuery, ScoreMode.COMPLETE, 1f)
        assertEquals(-1, weight2.count(reader2.leaves()[0]))

        IOUtils.close(reader, reader2, w, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testNormsRandom() {
        val iters = atLeast(10)
        for (iter in 0..<iters) {
            val dir = newDirectory()
            val iw = RandomIndexWriter(random(), dir)
            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                val doc = Document()
                val hasValue = random().nextBoolean()
                if (hasValue) {
                    doc.add(TextField("text1", "value", Store.NO))
                    doc.add(StringField("has_value", "yes", Store.NO))
                }
                doc.add(StringField("f", if (random().nextBoolean()) "yes" else "no", Store.NO))
                iw.addDocument(doc)
            }
            if (random().nextBoolean()) {
                iw.deleteDocuments(TermQuery(Term("f", "no")))
            }
            iw.commit()
            val reader = iw.getReader(true, false)
            val searcher = newSearcher(reader)
            iw.close()

            assertSameMatches(searcher, TermQuery(Term("has_value", "yes")), FieldExistsQuery("text1"), false)

            reader.close()
            dir.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testNormsApproximation() {
        val iters = atLeast(10)
        for (iter in 0..<iters) {
            val dir = newDirectory()
            val iw = RandomIndexWriter(random(), dir)
            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                val doc = Document()
                val hasValue = random().nextBoolean()
                if (hasValue) {
                    doc.add(TextField("text1", "value", Store.NO))
                    doc.add(StringField("has_value", "yes", Store.NO))
                }
                doc.add(StringField("f", if (random().nextBoolean()) "yes" else "no", Store.NO))
                iw.addDocument(doc)
            }
            if (random().nextBoolean()) {
                iw.deleteDocuments(TermQuery(Term("f", "no")))
            }
            iw.commit()
            val reader = iw.getReader(true, false)
            val searcher = newSearcher(reader)
            iw.close()

            val ref = BooleanQuery.Builder()
            ref.add(TermQuery(Term("f", "yes")), Occur.MUST)
            ref.add(TermQuery(Term("has_value", "yes")), Occur.FILTER)

            val bq1 = BooleanQuery.Builder()
            bq1.add(TermQuery(Term("f", "yes")), Occur.MUST)
            bq1.add(FieldExistsQuery("text1"), Occur.FILTER)
            assertSameMatches(searcher, ref.build(), bq1.build(), true)

            reader.close()
            dir.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testNormsScore() {
        val iters = atLeast(10)
        for (iter in 0..<iters) {
            val dir = newDirectory()
            val iw = RandomIndexWriter(random(), dir)
            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                val doc = Document()
                val hasValue = random().nextBoolean()
                if (hasValue) {
                    doc.add(TextField("text1", "value", Store.NO))
                    doc.add(StringField("has_value", "yes", Store.NO))
                }
                doc.add(StringField("f", if (random().nextBoolean()) "yes" else "no", Store.NO))
                iw.addDocument(doc)
            }
            if (random().nextBoolean()) {
                iw.deleteDocuments(TermQuery(Term("f", "no")))
            }
            iw.commit()
            val reader = iw.getReader(true, false)
            val searcher = newSearcher(reader)
            iw.close()

            val boost = random().nextFloat() * 10
            val ref = BoostQuery(ConstantScoreQuery(TermQuery(Term("has_value", "yes"))), boost)

            val q1 = BoostQuery(FieldExistsQuery("text1"), boost)
            assertSameMatches(searcher, ref, q1, true)

            reader.close()
            dir.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testNormsMissingField() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        iw.addDocument(Document())
        iw.commit()
        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        iw.close()
        assertEquals(0, searcher.count(FieldExistsQuery("f")))
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testNormsAllDocsHaveField() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(TextField("f", "value", Store.NO))
        iw.addDocument(doc)
        iw.commit()
        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        iw.close()
        assertEquals(1, searcher.count(FieldExistsQuery("f")))
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testNormsFieldExistsButNoDocsHaveField() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(TextField("f", "value", Store.NO))
        iw.addDocument(doc)
        iw.commit()
        iw.addDocument(Document())
        iw.commit()
        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        iw.close()
        assertEquals(1, searcher.count(FieldExistsQuery("f")))
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testNormsQueryMatchesCount() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val randomNumDocs = TestUtil.nextInt(random(), 10, 100)

        val noNormsFieldType = FieldType()
        noNormsFieldType.setOmitNorms(true)
        noNormsFieldType.setIndexOptions(IndexOptions.DOCS)

        val doc = Document()
        doc.add(TextField("text", "always here", Store.NO))
        doc.add(TextField("text_s", "", Store.NO))
        doc.add(Field("text_n", "always here", noNormsFieldType))
        w.addDocument(doc)

        for (i in 1..<randomNumDocs) {
            doc.clear()
            doc.add(TextField("text", "some text", Store.NO))
            doc.add(TextField("text_s", "some text", Store.NO))
            doc.add(Field("text_n", "some here", noNormsFieldType))
            w.addDocument(doc)
        }
        w.forceMerge(1)

        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)

        assertNormsCountWithShortcut(searcher, "text", randomNumDocs)
        assertNormsCountWithShortcut(searcher, "doesNotExist", 0)
        try {
            searcher.count(FieldExistsQuery("text_n"))
            fail("expected IllegalStateException")
        } catch (_: IllegalStateException) {
        }

        assertNormsCountWithoutShortcut(searcher, "text_s", randomNumDocs)

        w.w.config.mergePolicy = NoMergePolicy.INSTANCE
        w.deleteDocuments(Term("text", "text"))
        val reader2 = w.getReader(true, false)
        val searcher2 = IndexSearcher(reader2)
        assertNormsCountWithShortcut(searcher2, "text", 1)

        IOUtils.close(reader, reader2, w, dir)
    }

    @Throws(IOException::class)
    private fun assertNormsCountWithoutShortcut(searcher: IndexSearcher, field: String, expectedCount: Int) {
        val q = FieldExistsQuery(field)
        val weight = searcher.createWeight(q, ScoreMode.COMPLETE, 1f)
        assertEquals(-1, weight.count(searcher.reader.leaves()[0]))
        assertEquals(expectedCount, searcher.count(q))
    }

    @Throws(IOException::class)
    private fun assertNormsCountWithShortcut(searcher: IndexSearcher, field: String, numMatchingDocs: Int) {
        val testQuery = FieldExistsQuery(field)
        assertEquals(numMatchingDocs, searcher.count(testQuery))
        val weight = searcher.createWeight(testQuery, ScoreMode.COMPLETE, 1f)
        assertEquals(numMatchingDocs, weight.count(searcher.reader.leaves()[0]))
    }

    @Test
    @Throws(IOException::class)
    fun testKnnVectorRandom() {
        val iters = atLeast(10)
        for (iter in 0..<iters) {
            val dir = newDirectory()
            val iw = RandomIndexWriter(random(), dir)
            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                val doc = Document()
                val hasValue = random().nextBoolean()
                if (hasValue) {
                    doc.add(KnnFloatVectorField("vector", randomVector(5)))
                    doc.add(StringField("has_value", "yes", Store.NO))
                }
                doc.add(StringField("field", "value", Store.NO))
                iw.addDocument(doc)
            }
            if (random().nextBoolean()) {
                iw.deleteDocuments(TermQuery(Term("f", "no")))
            }
            iw.commit()

            val reader = iw.getReader(true, false)
            val searcher = newSearcher(reader)

            assertSameMatches(searcher, TermQuery(Term("has_value", "yes")), FieldExistsQuery("vector"), false)

            val boost = random().nextFloat() * 10
            assertSameMatches(
                searcher,
                BoostQuery(ConstantScoreQuery(TermQuery(Term("has_value", "yes"))), boost),
                BoostQuery(FieldExistsQuery("vector"), boost),
                true
            )
            reader.close()
            iw.close()
            dir.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testKnnVectorMissingField() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        iw.addDocument(Document())
        iw.commit()
        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        assertEquals(0, searcher.count(FieldExistsQuery("f")))
        reader.close()
        iw.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testKnnVectorAllDocsHaveField() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        for (i in 0..<100) {
            val doc = Document()
            doc.add(KnnFloatVectorField("vector", randomVector(5)))
            iw.addDocument(doc)
        }
        iw.commit()

        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        val query: Query = FieldExistsQuery("vector")
        assertTrue(searcher.rewrite(query) is MatchAllDocsQuery)
        assertEquals(100, searcher.count(query))
        reader.close()
        iw.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDeleteKnnVector() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val numDocs = atLeast(100)

        val allDocsHaveVector = random().nextBoolean()
        val docWithVector: BitSet = FixedBitSet(numDocs)
        for (i in 0..<numDocs) {
            val doc = Document()
            if (allDocsHaveVector || random().nextBoolean()) {
                doc.add(KnnFloatVectorField("vector", randomVector(5)))
                docWithVector.set(i)
            }
            doc.add(StringField("id", i.toString(), Store.NO))
            iw.addDocument(doc)
        }
        if (random().nextBoolean()) {
            val numDeleted = random().nextInt(numDocs) + 1
            for (i in 0..<numDeleted) {
                iw.deleteDocuments(Term("id", i.toString()))
                docWithVector.clear(i)
            }
        }

        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)

        val count = searcher.count(FieldExistsQuery("vector"))
        assertEquals(docWithVector.cardinality(), count)
        reader.close()
        iw.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testKnnVectorConjunction() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val numDocs = atLeast(100)
        var numVectors = 0

        val allDocsHaveVector = random().nextBoolean()
        for (i in 0..<numDocs) {
            val doc = Document()
            if (allDocsHaveVector || random().nextBoolean()) {
                doc.add(KnnFloatVectorField("vector", randomVector(5)))
                numVectors++
            }
            doc.add(StringField("field", "value${i % 2}", Store.NO))
            iw.addDocument(doc)
        }
        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        val occur = if (random().nextBoolean()) Occur.MUST else Occur.FILTER
        val booleanQuery = BooleanQuery.Builder()
            .add(TermQuery(Term("field", "value1")), occur)
            .add(FieldExistsQuery("vector"), Occur.FILTER)
            .build()

        val count = searcher.count(booleanQuery)
        assertTrue(count <= numVectors)
        if (allDocsHaveVector) {
            assertEquals(numDocs / 2, count)
        }
        reader.close()
        iw.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testKnnVectorFieldExistsButNoDocsHaveField() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(KnnFloatVectorField("vector", randomVector(3)))
        iw.addDocument(doc)
        iw.commit()
        iw.addDocument(Document())
        iw.commit()
        val reader = iw.getReader(true, false)
        val searcher = newSearcher(reader)
        assertEquals(1, searcher.count(FieldExistsQuery("vector")))
        reader.close()
        iw.close()
        dir.close()
    }

    private fun randomVector(dim: Int): FloatArray {
        val v = FloatArray(dim)
        for (i in 0..<dim) {
            v[i] = random().nextFloat()
        }
        VectorUtil.l2normalize(v)
        return v
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteAllPointDocs() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)

        val doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        doc.add(LongPoint("long", 17))
        doc.add(NumericDocValuesField("long", 17))
        iw.addDocument(doc)
        iw.addDocument(Document())
        iw.flush()
        iw.addDocument(Document())
        iw.commit()

        iw.deleteDocuments(Term("id", "0"))
        iw.forceMerge(1)

        val reader = iw.getReader(true, false)
        assertTrue(reader.leaves().size == 1 && !reader.hasDeletions())
        val searcher = newSearcher(reader)
        assertEquals(0, searcher.count(FieldExistsQuery("long")))
        reader.close()
        iw.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteAllTermDocs() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)

        val doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        doc.add(StringField("str", "foo", Store.NO))
        doc.add(SortedDocValuesField("str", newBytesRef("foo")))
        iw.addDocument(doc)
        iw.addDocument(Document())
        iw.flush()
        iw.addDocument(Document())
        iw.commit()

        iw.deleteDocuments(Term("id", "0"))
        iw.forceMerge(1)

        val reader = iw.getReader(true, false)
        assertTrue(reader.leaves().size == 1 && !reader.hasDeletions())
        val searcher = newSearcher(reader)
        assertEquals(0, searcher.count(FieldExistsQuery("str")))
        reader.close()
        iw.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun assertSameMatches(searcher: IndexSearcher, q1: Query, q2: Query, scores: Boolean) {
        val maxDoc = searcher.indexReader.maxDoc()
        val td1 = searcher.search(q1, maxDoc, if (scores) Sort.RELEVANCE else Sort.INDEXORDER)
        val td2 = searcher.search(q2, maxDoc, if (scores) Sort.RELEVANCE else Sort.INDEXORDER)
        assertEquals(td1.totalHits.value, td2.totalHits.value)
        for (i in td1.scoreDocs.indices) {
            assertEquals(td1.scoreDocs[i].doc, td2.scoreDocs[i].doc)
            if (scores) {
                assertEquals(td1.scoreDocs[i].score, td2.scoreDocs[i].score, 10e-7f)
            }
        }
    }

    @Throws(IOException::class)
    private fun assertSameCount(reader: IndexReader, searcher: IndexSearcher, field: String, numMatchingDocs: Int) {
        val testQuery = FieldExistsQuery(field)
        assertEquals(numMatchingDocs, searcher.count(testQuery))
        val weight = searcher.createWeight(testQuery, ScoreMode.COMPLETE, 1f)
        assertEquals(numMatchingDocs, weight.count(reader.leaves()[0]))
    }
}
