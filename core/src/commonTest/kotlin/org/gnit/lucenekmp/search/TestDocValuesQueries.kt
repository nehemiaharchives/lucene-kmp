package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TestDocValuesQueries : LuceneTestCase() {
    private fun getCodec(): Codec {
        return TestUtil.alwaysDocValuesFormat(Lucene90DocValuesFormat(random().nextInt(4, 16)))
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointRangeSortedNumericRangeQuery() {
        doTestDuelPointRangeNumericRangeQuery(true, 1, false)
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointRangeSortedNumericRangeWithSlipperQuery() {
        doTestDuelPointRangeNumericRangeQuery(true, 1, true)
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointRangeMultivaluedSortedNumericRangeQuery() {
        doTestDuelPointRangeNumericRangeQuery(true, 3, false)
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointRangeMultivaluedSortedNumericRangeWithSkipperQuery() {
        doTestDuelPointRangeNumericRangeQuery(true, 3, true)
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointRangeNumericRangeQuery() {
        doTestDuelPointRangeNumericRangeQuery(false, 1, false)
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointRangeNumericRangeWithSkipperQuery() {
        doTestDuelPointRangeNumericRangeQuery(false, 1, true)
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointNumericSortedWithSkipperRangeQuery() {
        val dir = newDirectory()
        val config = IndexWriterConfig().setCodec(getCodec())
        config.setIndexSort(Sort(SortField("dv", SortField.Type.LONG, random().nextBoolean())))
        val iw = RandomIndexWriter(random(), dir, config)
        val numDocs = atLeast(1000)
        for (i in 0..<numDocs) {
            val doc = Document()
            val value = TestUtil.nextLong(random(), -100, 10000)
            doc.add(NumericDocValuesField.indexedField("dv", value))
            doc.add(LongPoint("idx", value))
            iw.addDocument(doc)
        }

        val reader = iw.getReader(random().nextBoolean(), false)
        val searcher = newSearcher(reader, false)
        iw.close()

        for (i in 0..<100) {
            val min = if (random().nextBoolean()) Long.MIN_VALUE else TestUtil.nextLong(random(), -100, 10000)
            val max = if (random().nextBoolean()) Long.MAX_VALUE else TestUtil.nextLong(random(), -100, 10000)
            val q1 = LongPoint.newRangeQuery("idx", min, max)
            val q2 = NumericDocValuesField.newSlowRangeQuery("dv", min, max)
            assertSameMatches(searcher, q1, q2, false)
        }
        reader.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun doTestDuelPointRangeNumericRangeQuery(
        sortedNumeric: Boolean,
        maxValuesPerDoc: Int,
        skypper: Boolean
    ) {
        val iters = atLeast(10)
        for (iter in 0..<iters) {
            val dir = newDirectory()
            val iw = if (sortedNumeric || random().nextBoolean()) {
                RandomIndexWriter(random(), dir)
            } else {
                val config = IndexWriterConfig().setCodec(getCodec())
                config.setIndexSort(Sort(SortField("dv", SortField.Type.LONG, random().nextBoolean())))
                RandomIndexWriter(random(), dir, config)
            }
            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                val doc = Document()
                val numValues = TestUtil.nextInt(random(), 0, maxValuesPerDoc)
                for (j in 0..<numValues) {
                    val value = TestUtil.nextLong(random(), -100, 10000)
                    if (sortedNumeric) {
                        if (skypper) {
                            doc.add(SortedNumericDocValuesField.indexedField("dv", value))
                        } else {
                            doc.add(SortedNumericDocValuesField("dv", value))
                        }
                    } else {
                        if (skypper) {
                            doc.add(NumericDocValuesField.indexedField("dv", value))
                        } else {
                            doc.add(NumericDocValuesField("dv", value))
                        }
                    }
                    doc.add(LongPoint("idx", value))
                }
                iw.addDocument(doc)
            }
            if (random().nextBoolean()) {
                iw.deleteDocuments(LongPoint.newRangeQuery("idx", 0L, 10L))
            }
            val reader = iw.getReader(random().nextBoolean(), false)
            val searcher = newSearcher(reader, false)
            iw.close()

            for (i in 0..<100) {
                val min = if (random().nextBoolean()) Long.MIN_VALUE else TestUtil.nextLong(random(), -100, 10000)
                val max = if (random().nextBoolean()) Long.MAX_VALUE else TestUtil.nextLong(random(), -100, 10000)
                val q1 = LongPoint.newRangeQuery("idx", min, max)
                val q2 = if (sortedNumeric) {
                    SortedNumericDocValuesField.newSlowRangeQuery("dv", min, max)
                } else {
                    NumericDocValuesField.newSlowRangeQuery("dv", min, max)
                }
                assertSameMatches(searcher, q1, q2, false)
            }

            reader.close()
            dir.close()
        }
    }

    @Throws(IOException::class)
    private fun doTestDuelPointRangeSortedRangeQuery(
        sortedSet: Boolean,
        maxValuesPerDoc: Int,
        skypper: Boolean
    ) {
        val iters = atLeast(10)
        for (iter in 0..<iters) {
            val dir = newDirectory()
            val iw = if (sortedSet || random().nextBoolean()) {
                RandomIndexWriter(random(), dir)
            } else {
                val config = IndexWriterConfig().setCodec(getCodec())
                config.setIndexSort(Sort(SortField("dv", SortField.Type.STRING, random().nextBoolean())))
                RandomIndexWriter(random(), dir, config)
            }
            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                val doc = Document()
                val numValues = TestUtil.nextInt(random(), 0, maxValuesPerDoc)
                for (j in 0..<numValues) {
                    val value = TestUtil.nextLong(random(), -100, 10000)
                    val encoded = ByteArray(Long.SIZE_BYTES)
                    LongPoint.encodeDimension(value, encoded, 0)
                    if (sortedSet) {
                        if (skypper) {
                            doc.add(SortedSetDocValuesField.indexedField("dv", newBytesRef(encoded)))
                        } else {
                            doc.add(SortedSetDocValuesField("dv", newBytesRef(encoded)))
                        }
                    } else {
                        if (skypper) {
                            doc.add(SortedDocValuesField.indexedField("dv", newBytesRef(encoded)))
                        } else {
                            doc.add(SortedDocValuesField("dv", newBytesRef(encoded)))
                        }
                    }
                    doc.add(LongPoint("idx", value))
                }
                iw.addDocument(doc)
            }
            if (random().nextBoolean()) {
                iw.deleteDocuments(LongPoint.newRangeQuery("idx", 0L, 10L))
            }
            val reader = iw.getReader(random().nextBoolean(), false)
            val searcher = newSearcher(reader, false)
            iw.close()

            for (i in 0..<100) {
                var min = if (random().nextBoolean()) Long.MIN_VALUE else TestUtil.nextLong(random(), -100, 10000)
                var max = if (random().nextBoolean()) Long.MAX_VALUE else TestUtil.nextLong(random(), -100, 10000)
                val encodedMin = ByteArray(Long.SIZE_BYTES)
                val encodedMax = ByteArray(Long.SIZE_BYTES)
                LongPoint.encodeDimension(min, encodedMin, 0)
                LongPoint.encodeDimension(max, encodedMax, 0)
                var includeMin = true
                var includeMax = true
                if (random().nextBoolean()) {
                    includeMin = false
                    min++
                }
                if (random().nextBoolean()) {
                    includeMax = false
                    max--
                }
                val q1 = LongPoint.newRangeQuery("idx", min, max)
                val q2 = if (sortedSet) {
                    SortedSetDocValuesField.newSlowRangeQuery(
                        "dv",
                        if (min == Long.MIN_VALUE && random().nextBoolean()) null else newBytesRef(encodedMin),
                        if (max == Long.MAX_VALUE && random().nextBoolean()) null else newBytesRef(encodedMax),
                        includeMin,
                        includeMax
                    )
                } else {
                    SortedDocValuesField.newSlowRangeQuery(
                        "dv",
                        if (min == Long.MIN_VALUE && random().nextBoolean()) null else newBytesRef(encodedMin),
                        if (max == Long.MAX_VALUE && random().nextBoolean()) null else newBytesRef(encodedMax),
                        includeMin,
                        includeMax
                    )
                }
                assertSameMatches(searcher, q1, q2, false)
            }

            reader.close()
            dir.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointRangeSortedSetRangeQuery() {
        doTestDuelPointRangeSortedRangeQuery(true, 1, false)
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointRangeSortedSetRangeSkipperQuery() {
        doTestDuelPointRangeSortedRangeQuery(true, 1, true)
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointRangeMultivaluedSortedSetRangeQuery() {
        doTestDuelPointRangeSortedRangeQuery(true, 3, false)
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointRangeMultivaluedSortedSetRangeSkipperQuery() {
        doTestDuelPointRangeSortedRangeQuery(true, 3, true)
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointRangeSortedRangeQuery() {
        doTestDuelPointRangeSortedRangeQuery(false, 1, false)
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointRangeSortedRangeSkipperQuery() {
        doTestDuelPointRangeSortedRangeQuery(false, 1, true)
    }

    @Test
    @Throws(IOException::class)
    fun testDuelPointSortedSetSortedWithSkipperRangeQuery() {
        val dir = newDirectory()
        val config = IndexWriterConfig().setCodec(getCodec())
        config.setIndexSort(Sort(SortField("dv", SortField.Type.STRING, random().nextBoolean())))
        val iw = RandomIndexWriter(random(), dir, config)
        val numDocs = atLeast(1000)
        for (i in 0..<numDocs) {
            val doc = Document()
            val value = TestUtil.nextLong(random(), -100, 10000)
            val encoded = ByteArray(Long.SIZE_BYTES)
            LongPoint.encodeDimension(value, encoded, 0)
            doc.add(SortedDocValuesField.indexedField("dv", newBytesRef(encoded)))
            doc.add(LongPoint("idx", value))
            iw.addDocument(doc)
        }

        val reader = iw.getReader(random().nextBoolean(), false)
        val searcher = newSearcher(reader, false)
        iw.close()

        for (i in 0..<100) {
            var min = if (random().nextBoolean()) Long.MIN_VALUE else TestUtil.nextLong(random(), -100, 10000)
            var max = if (random().nextBoolean()) Long.MAX_VALUE else TestUtil.nextLong(random(), -100, 10000)
            val encodedMin = ByteArray(Long.SIZE_BYTES)
            val encodedMax = ByteArray(Long.SIZE_BYTES)
            LongPoint.encodeDimension(min, encodedMin, 0)
            LongPoint.encodeDimension(max, encodedMax, 0)
            var includeMin = true
            var includeMax = true
            if (random().nextBoolean()) {
                includeMin = false
                min++
            }
            if (random().nextBoolean()) {
                includeMax = false
                max--
            }
            val q1 = LongPoint.newRangeQuery("idx", min, max)
            val q2 = SortedDocValuesField.newSlowRangeQuery(
                "dv",
                if (min == Long.MIN_VALUE && random().nextBoolean()) null else newBytesRef(encodedMin),
                if (max == Long.MAX_VALUE && random().nextBoolean()) null else newBytesRef(encodedMax),
                includeMin,
                includeMax
            )
            assertSameMatches(searcher, q1, q2, false)
        }
        reader.close()
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

    @Test
    fun testEquals() {
        val q1 = SortedNumericDocValuesField.newSlowRangeQuery("foo", 3, 5)
        QueryUtils.checkEqual(q1, SortedNumericDocValuesField.newSlowRangeQuery("foo", 3, 5))
        QueryUtils.checkUnequal(q1, SortedNumericDocValuesField.newSlowRangeQuery("foo", 3, 6))
        QueryUtils.checkUnequal(q1, SortedNumericDocValuesField.newSlowRangeQuery("foo", 4, 5))
        QueryUtils.checkUnequal(q1, SortedNumericDocValuesField.newSlowRangeQuery("bar", 3, 5))

        val q2 = SortedSetDocValuesField.newSlowRangeQuery("foo", newBytesRef("bar"), newBytesRef("baz"), true, true)
        QueryUtils.checkEqual(q2, SortedSetDocValuesField.newSlowRangeQuery("foo", newBytesRef("bar"), newBytesRef("baz"), true, true))
        QueryUtils.checkUnequal(q2, SortedSetDocValuesField.newSlowRangeQuery("foo", newBytesRef("baz"), newBytesRef("baz"), true, true))
        QueryUtils.checkUnequal(q2, SortedSetDocValuesField.newSlowRangeQuery("foo", newBytesRef("bar"), newBytesRef("bar"), true, true))
        QueryUtils.checkUnequal(q2, SortedSetDocValuesField.newSlowRangeQuery("quux", newBytesRef("bar"), newBytesRef("baz"), true, true))
    }

    @Test
    fun testToString() {
        val q1 = SortedNumericDocValuesField.newSlowRangeQuery("foo", 3, 5)
        assertEquals("foo:[3 TO 5]", q1.toString())
        assertEquals("[3 TO 5]", q1.toString("foo"))
        assertEquals("foo:[3 TO 5]", q1.toString("bar"))

        var q2 = SortedSetDocValuesField.newSlowRangeQuery("foo", newBytesRef("bar"), newBytesRef("baz"), true, true)
        assertEquals("foo:[[62 61 72] TO [62 61 7a]]", q2.toString())
        q2 = SortedSetDocValuesField.newSlowRangeQuery("foo", newBytesRef("bar"), newBytesRef("baz"), false, true)
        assertEquals("foo:{[62 61 72] TO [62 61 7a]]", q2.toString())
        q2 = SortedSetDocValuesField.newSlowRangeQuery("foo", newBytesRef("bar"), newBytesRef("baz"), false, false)
        assertEquals("foo:{[62 61 72] TO [62 61 7a]}", q2.toString())
        q2 = SortedSetDocValuesField.newSlowRangeQuery("foo", newBytesRef("bar"), null, true, true)
        assertEquals("foo:[[62 61 72] TO *}", q2.toString())
        q2 = SortedSetDocValuesField.newSlowRangeQuery("foo", null, newBytesRef("baz"), true, true)
        assertEquals("foo:{* TO [62 61 7a]]", q2.toString())
        assertEquals("{* TO [62 61 7a]]", q2.toString("foo"))
        assertEquals("foo:{* TO [62 61 7a]]", q2.toString("bar"))
    }

    @Test
    @Throws(IOException::class)
    fun testMissingField() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        iw.addDocument(Document())
        val reader = iw.getReader(random().nextBoolean(), false)
        iw.close()
        val searcher = newSearcher(reader)
        for (query in listOf(
            NumericDocValuesField.newSlowRangeQuery("foo", 2, 4),
            SortedNumericDocValuesField.newSlowRangeQuery("foo", 2, 4),
            SortedDocValuesField.newSlowRangeQuery("foo", newBytesRef("abc"), newBytesRef("bcd"), random().nextBoolean(), random().nextBoolean()),
            SortedSetDocValuesField.newSlowRangeQuery("foo", newBytesRef("abc"), newBytesRef("bcd"), random().nextBoolean(), random().nextBoolean())
        )) {
            val w = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE, 1f)
            assertNull(w.scorer(searcher.indexReader.leaves()[0]))
        }
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testSlowRangeQueryRewrite() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val reader = iw.getReader(random().nextBoolean(), false)
        iw.close()
        val searcher = newSearcher(reader)

        QueryUtils.checkEqual(NumericDocValuesField.newSlowRangeQuery("foo", 10, 1).rewrite(searcher), MatchNoDocsQuery())
        QueryUtils.checkEqual(
            NumericDocValuesField.newSlowRangeQuery("foo", Long.MIN_VALUE, Long.MAX_VALUE).rewrite(searcher),
            FieldExistsQuery("foo")
        )
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testSortedNumericNPE() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val nums = doubleArrayOf(
            -1.7147449030215377E-208,
            -1.6887024655302576E-11,
            1.534911516604164E113,
            0.0,
            2.6947996404505155E-166,
            -2.649722021970773E306,
            6.138239235731689E-198,
            2.3967090122610808E111,
        )
        for (i in nums.indices) {
            val doc = Document()
            doc.add(SortedNumericDocValuesField("dv", NumericUtils.doubleToSortableLong(nums[i])))
            iw.addDocument(doc)
        }
        iw.commit()
        val reader = iw.getReader(random().nextBoolean(), false)
        val searcher = newSearcher(reader)
        iw.close()

        val lo = NumericUtils.doubleToSortableLong(8.701032080293731E-226)
        val hi = NumericUtils.doubleToSortableLong(2.0801416404385346E-41)

        var query: Query = SortedNumericDocValuesField.newSlowRangeQuery("dv", lo, hi)
        searcher.search(query, searcher.reader.maxDoc(), Sort.INDEXORDER)

        query = SortedNumericDocValuesField.newSlowRangeQuery("dv", hi, lo)
        searcher.search(query, searcher.reader.maxDoc(), Sort.INDEXORDER)

        reader.close()
        dir.close()
    }

    @Test
    fun testSetEquals() {
        assertEquals(NumericDocValuesField.newSlowSetQuery("field", 17L, 42L), NumericDocValuesField.newSlowSetQuery("field", 17L, 42L))
        assertEquals(
            NumericDocValuesField.newSlowSetQuery("field", 17L, 42L, 32416190071L),
            NumericDocValuesField.newSlowSetQuery("field", 17L, 32416190071L, 42L)
        )
        assertFalse(NumericDocValuesField.newSlowSetQuery("field", 42L) == NumericDocValuesField.newSlowSetQuery("field2", 42L))
        assertFalse(NumericDocValuesField.newSlowSetQuery("field", 17L, 42L) == NumericDocValuesField.newSlowSetQuery("field", 17L, 32416190071L))
    }

    @Test
    @Throws(IOException::class)
    fun testDuelSetVsTermsQuery() {
        val iters = atLeast(2)
        for (iter in 0..<iters) {
            val allNumbers = mutableListOf<Long>()
            val numNumbers = TestUtil.nextInt(random(), 1, 1 shl TestUtil.nextInt(random(), 1, 10))
            for (i in 0..<numNumbers) {
                allNumbers.add(random().nextLong())
            }
            val dir = newDirectory()
            val iw = RandomIndexWriter(random(), dir)
            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                val doc = Document()
                val number = allNumbers[random().nextInt(allNumbers.size)]
                doc.add(StringField("text", number.toString(), Field.Store.NO))
                doc.add(NumericDocValuesField("long", number))
                doc.add(SortedNumericDocValuesField("twolongs", number))
                doc.add(SortedNumericDocValuesField("twolongs", number * 2))
                iw.addDocument(doc)
            }
            if (numNumbers > 1 && random().nextBoolean()) {
                iw.deleteDocuments(TermQuery(Term("text", allNumbers[0].toString())))
            }
            iw.commit()
            val reader = iw.getReader(random().nextBoolean(), false)
            val searcher = newSearcher(reader)
            iw.close()

            if (reader.numDocs() == 0) {
                IOUtils.close(reader, dir)
                continue
            }

            for (i in 0..<100) {
                val boost = random().nextFloat() * 10
                val numQueryNumbers = TestUtil.nextInt(random(), 1, 1 shl TestUtil.nextInt(random(), 1, 8))
                val queryNumbers = linkedSetOf<Long>()
                val queryNumbersX2 = linkedSetOf<Long>()
                for (j in 0..<numQueryNumbers) {
                    val number = allNumbers[random().nextInt(allNumbers.size)]
                    queryNumbers.add(number)
                    queryNumbersX2.add(2 * number)
                }
                val queryNumbersArray = queryNumbers.toLongArray()
                val queryNumbersX2Array = queryNumbersX2.toLongArray()
                val bq = BooleanQuery.Builder()
                for (number in queryNumbers) {
                    bq.add(TermQuery(Term("text", number.toString())), BooleanClause.Occur.SHOULD)
                }
                val q1: Query = BoostQuery(ConstantScoreQuery(bq.build()), boost)

                val q2: Query = BoostQuery(NumericDocValuesField.newSlowSetQuery("long", *queryNumbersArray), boost)
                assertSameMatches(searcher, q1, q2, true)

                val q3: Query = BoostQuery(SortedNumericDocValuesField.newSlowSetQuery("twolongs", *queryNumbersArray), boost)
                assertSameMatches(searcher, q1, q3, true)

                val q4: Query = BoostQuery(SortedNumericDocValuesField.newSlowSetQuery("twolongs", *queryNumbersX2Array), boost)
                assertSameMatches(searcher, q1, q4, true)
            }

            reader.close()
            dir.close()
        }
    }
}
