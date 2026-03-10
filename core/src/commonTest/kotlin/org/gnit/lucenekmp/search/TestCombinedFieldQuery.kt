package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.FieldInvertState
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.similarities.BM25Similarity
import org.gnit.lucenekmp.search.similarities.BooleanSimilarity
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.search.similarities.LMDirichletSimilarity
import org.gnit.lucenekmp.search.similarities.LMJelinekMercerSimilarity
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.MMapDirectory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomBoolean
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomIntBetween
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestCombinedFieldQuery : LuceneTestCase() {
    @Test
    fun testInvalid() {
        val builder = CombinedFieldQuery.Builder("foo")
        val exc = expectThrows(IllegalArgumentException::class) { builder.addField("foo", 0.5f) }
        assertEquals("weight must be greater or equal to 1", exc.message)
    }

    @Test
    @Throws(IOException::class)
    fun testRewrite() {
        val builder = CombinedFieldQuery.Builder("foo")
        val reader: IndexReader = MultiReader()
        val searcher = IndexSearcher(reader)
        var actual = searcher.rewrite(builder.build())
        assertEquals(MatchNoDocsQuery(), actual)
        builder.addField("field", 1f)
        val query: Query = builder.build()
        actual = searcher.rewrite(builder.build())
        assertEquals(query, actual)
        reader.close()
    }

    @Test
    fun testEqualsAndHashCode() {
        val query1 = CombinedFieldQuery.Builder("value").addField("field1").addField("field2").build()

        val query2 = CombinedFieldQuery.Builder("value").addField("field1").addField("field2", 1.3f).build()
        assertNotEquals(query1, query2)
        assertNotEquals(query1.hashCode(), query2.hashCode())

        val query3 = CombinedFieldQuery.Builder("value").addField("field3").addField("field4").build()
        assertNotEquals(query1, query3)
        assertNotEquals(query1.hashCode(), query2.hashCode())

        val duplicateQuery1 = CombinedFieldQuery.Builder("value").addField("field1").addField("field2").build()
        assertEquals(query1, duplicateQuery1)
        assertEquals(query1.hashCode(), duplicateQuery1.hashCode())
    }

    @Test
    fun testToString() {
        val builder = CombinedFieldQuery.Builder("bar")
        assertEquals("CombinedFieldQuery(()(bar))", builder.build().toString())
        builder.addField("foo", 1f)
        assertEquals("CombinedFieldQuery((foo)(bar))", builder.build().toString())
        builder.addField("title", 3f)
        assertEquals("CombinedFieldQuery((foo title^3.0)(bar))", builder.build().toString())
    }

    @Test
    @Throws(IOException::class)
    fun testSameScore() {
        val dir: Directory = newDirectory()
        val similarity = randomCompatibleSimilarity()

        val iwc = IndexWriterConfig()
        iwc.similarity = similarity
        val w = RandomIndexWriter(random(), dir, iwc)

        var doc = Document()
        doc.add(StringField("f", "a", Store.NO))
        w.addDocument(doc)

        doc = Document()
        doc.add(StringField("g", "a", Store.NO))
        for (i in 0..<10) {
            w.addDocument(doc)
        }

        val reader = w.reader
        val searcher = newSearcher(reader)
        searcher.similarity = similarity
        val query = CombinedFieldQuery.Builder("a").addField("f", 1f).addField("g", 1f).build()
        val collectorManager = TopScoreDocCollectorManager(min(reader.numDocs(), Int.MAX_VALUE), Int.MAX_VALUE)
        val topDocs = searcher.search(query, collectorManager)
        assertEquals(TotalHits(11, TotalHits.Relation.EQUAL_TO), topDocs.totalHits)
        // All docs must have the same score
        for (i in topDocs.scoreDocs.indices) {
            assertEquals(topDocs.scoreDocs[0].score, topDocs.scoreDocs[i].score, 0.0f)
        }

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testScoringWithMultipleFieldTermsMatch() {
        val numMatchDoc = randomIntBetween(100, 500)
        val numHits = randomIntBetween(1, 100)
        val boost1 = max(1, random().nextInt(5))
        val boost2 = max(1, random().nextInt(5))

        val dir: Directory = newDirectory()
        val similarity = randomCompatibleSimilarity()

        val iwc = IndexWriterConfig()
        iwc.similarity = similarity
        val w = RandomIndexWriter(random(), dir, iwc)

        // adding potentially matching doc
        for (i in 0..<numMatchDoc) {
            val doc = Document()

            var freqA = random().nextInt(20) + 1
            for (j in 0..<freqA) {
                doc.add(TextField("a", "foo", Store.NO))
            }

            freqA = random().nextInt(20) + 1
            if (randomBoolean()) {
                for (j in 0..<freqA) {
                    doc.add(TextField("a", "foo$j", Store.NO))
                }
            }

            freqA = random().nextInt(20) + 1
            for (j in 0..<freqA) {
                doc.add(TextField("a", "zoo", Store.NO))
            }

            var freqB = random().nextInt(20) + 1
            for (j in 0..<freqB) {
                doc.add(TextField("b", "zoo", Store.NO))
            }

            freqB = random().nextInt(20) + 1
            if (randomBoolean()) {
                for (j in 0..<freqB) {
                    doc.add(TextField("b", "zoo$j", Store.NO))
                }
            }

            val freqC = random().nextInt(20) + 1
            for (j in 0..<freqC) {
                doc.add(TextField("c", "bla$j", Store.NO))
            }
            w.addDocument(doc)
        }

        val reader = w.reader
        val searcher = newSearcher(reader)
        searcher.similarity = similarity

        val query =
            CombinedFieldQuery.Builder("foo")
                .addField("a", boost1.toFloat())
                .addField("b", boost2.toFloat())
                .build()

        val completeManager: CollectorManager<TopScoreDocCollector, TopDocs> =
            TopScoreDocCollectorManager(numHits, Int.MAX_VALUE)

        searcher.search(query, completeManager)

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testNormsDisabled() {
        val dir: Directory = newDirectory()
        val similarity = randomCompatibleSimilarity()

        val iwc = IndexWriterConfig()
        iwc.similarity = similarity
        val w = RandomIndexWriter(random(), dir, iwc)

        var doc = Document()
        doc.add(StringField("a", "value", Store.NO))
        doc.add(StringField("b", "value", Store.NO))
        doc.add(TextField("c", "value", Store.NO))
        w.addDocument(doc)
        w.commit()

        doc = Document()
        doc.add(StringField("a", "value", Store.NO))
        doc.add(TextField("c", "value", Store.NO))
        w.addDocument(doc)

        val reader = w.reader
        val searcher = newSearcher(reader)

        val searchSimilarity = randomCompatibleSimilarity()
        searcher.similarity = searchSimilarity
        val collectorManager = TopScoreDocCollectorManager(10, 10)

        val query = CombinedFieldQuery.Builder("value").addField("a", 1.0f).addField("b", 1.0f).build()
        val topDocs = searcher.search(query, collectorManager)
        assertEquals(TotalHits(2, TotalHits.Relation.EQUAL_TO), topDocs.totalHits)

        val invalidQuery = CombinedFieldQuery.Builder("value").addField("b", 1.0f).addField("c", 1.0f).build()
        val e = expectThrows(IllegalArgumentException::class) { searcher.search(invalidQuery, collectorManager) }
        assertTrue(requireNotNull(e.message).contains("requires norms to be consistent across fields"))

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testCopyField() {
        val dir: Directory = newDirectory()
        val similarity = randomCompatibleSimilarity()

        val iwc = IndexWriterConfig()
        iwc.similarity = similarity
        val w = RandomIndexWriter(random(), dir, iwc)

        val numMatch = atLeast(10)
        val boost1 = max(1, random().nextInt(5))
        val boost2 = max(1, random().nextInt(5))
        for (i in 0..<numMatch) {
            val doc = Document()
            if (random().nextBoolean()) {
                doc.add(TextField("a", "baz", Store.NO))
                doc.add(TextField("b", "baz", Store.NO))
                for (k in 0..<(boost1 + boost2)) {
                    doc.add(TextField("ab", "baz", Store.NO))
                }
                w.addDocument(doc)
                doc.clear()
            }
            val freqA = random().nextInt(5) + 1
            for (j in 0..<freqA) {
                doc.add(TextField("a", "foo", Store.NO))
            }
            val freqB = random().nextInt(5) + 1
            for (j in 0..<freqB) {
                doc.add(TextField("b", "foo", Store.NO))
            }
            val freqAB = freqA * boost1 + freqB * boost2
            for (j in 0..<freqAB) {
                doc.add(TextField("ab", "foo", Store.NO))
            }
            w.addDocument(doc)
        }
        val reader = w.reader
        val searcher = newSearcher(reader)

        searcher.similarity = similarity
        val query =
            CombinedFieldQuery.Builder("foo")
                .addField("a", boost1.toFloat())
                .addField("b", boost2.toFloat())
                .build()

        checkExpectedHits(searcher, numMatch, query, TermQuery(Term("ab", "foo")))

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testCopyFieldWithSingleField() {
        val dir: Directory = MMapDirectory(createTempDir())
        val similarity = randomCompatibleSimilarity()

        val iwc = IndexWriterConfig()
        iwc.similarity = similarity
        val w = RandomIndexWriter(random(), dir, iwc)

        val boost = max(1, random().nextInt(5))
        val numMatch = atLeast(10)
        for (i in 0..<numMatch) {
            val doc = Document()
            val freqA = random().nextInt(5) + 1
            for (j in 0..<freqA) {
                doc.add(TextField("a", "foo", Store.NO))
            }

            val freqB = freqA * boost
            for (j in 0..<freqB) {
                doc.add(TextField("b", "foo", Store.NO))
            }

            w.addDocument(doc)
        }

        val reader = w.reader
        val searcher = newSearcher(reader)
        searcher.similarity = similarity
        val query = CombinedFieldQuery.Builder("foo").addField("a", boost.toFloat()).build()

        checkExpectedHits(searcher, numMatch, query, TermQuery(Term("b", "foo")))

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testCopyFieldWithMissingFields() {
        val dir: Directory = MMapDirectory(createTempDir())
        val similarity = randomCompatibleSimilarity()

        val iwc = IndexWriterConfig()
        iwc.similarity = similarity
        val w = RandomIndexWriter(random(), dir, iwc)

        val boost1 = max(1, random().nextInt(5))
        val boost2 = max(1, random().nextInt(5))
        val numMatch = atLeast(10)
        for (i in 0..<numMatch) {
            val doc = Document()
            val freqA = random().nextInt(5) + 1
            for (j in 0..<freqA) {
                doc.add(TextField("a", "foo", Store.NO))
            }

            // Choose frequencies such that sometimes we don't add field B
            val freqB = random().nextInt(3)
            for (j in 0..<freqB) {
                doc.add(TextField("b", "foo", Store.NO))
            }

            val freqAB = freqA * boost1 + freqB * boost2
            for (j in 0..<freqAB) {
                doc.add(TextField("ab", "foo", Store.NO))
            }

            w.addDocument(doc)
        }

        val reader = w.reader
        val searcher = newSearcher(reader)
        searcher.similarity = similarity
        val query =
            CombinedFieldQuery.Builder("foo")
                .addField("a", boost1.toFloat())
                .addField("b", boost2.toFloat())
                .build()

        checkExpectedHits(searcher, numMatch, query, TermQuery(Term("ab", "foo")))

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDocWithNegativeNorms() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig()
        iwc.similarity = NegativeNormSimilarity()
        val w = RandomIndexWriter(random(), dir, iwc)

        val queryString = "foo"

        val doc = Document()
        // both fields must contain tokens that match the query string "foo"
        doc.add(TextField("f", "foo", Store.NO))
        doc.add(TextField("g", "foo baz", Store.NO))
        w.addDocument(doc)

        val reader = w.reader
        val searcher = newSearcher(reader)
        searcher.similarity = BM25Similarity()
        val query = CombinedFieldQuery.Builder(queryString).addField("f").addField("g").build()
        val topDocs = searcher.search(query, 10)
        CheckHits.checkDocIds("queried docs do not match", intArrayOf(0), topDocs.scoreDocs)

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMultipleDocsNegativeNorms() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig()
        iwc.similarity = NegativeNormSimilarity()
        val w = RandomIndexWriter(random(), dir, iwc)

        val queryString = "foo"

        val doc0 = Document()
        doc0.add(TextField("f", "foo", Store.NO))
        doc0.add(TextField("g", "foo baz", Store.NO))
        w.addDocument(doc0)

        val doc1 = Document()
        // add another match on the query string to the second doc
        doc1.add(TextField("f", "foo is foo", Store.NO))
        doc1.add(TextField("g", "foo baz", Store.NO))
        w.addDocument(doc1)

        val reader = w.reader
        val searcher = newSearcher(reader)
        searcher.similarity = BM25Similarity()
        val query = CombinedFieldQuery.Builder(queryString).addField("f").addField("g").build()
        val topDocs = searcher.search(query, 10)
        // Return doc1 ahead of doc0 since its tf is higher
        CheckHits.checkDocIds("queried docs do not match", intArrayOf(1, 0), topDocs.scoreDocs)

        reader.close()
        w.close()
        dir.close()
    }

    companion object {
        private fun randomCompatibleSimilarity(): Similarity {
            return RandomPicks.randomFrom(
                random(),
                mutableListOf(
                    BM25Similarity(),
                    BooleanSimilarity(),
                    ClassicSimilarity(),
                    LMDirichletSimilarity(),
                    LMJelinekMercerSimilarity(0.1f),
                ),
            )
        }
    }

    private fun checkExpectedHits(searcher: IndexSearcher, numHits: Int, firstQuery: Query, secondQuery: Query) {
        var collectorManager = TopScoreDocCollectorManager(numHits, Int.MAX_VALUE)

        val firstTopDocs = searcher.search(firstQuery, collectorManager)
        assertEquals(numHits.toLong(), firstTopDocs.totalHits.value)

        collectorManager = TopScoreDocCollectorManager(numHits, Int.MAX_VALUE)
        val secondTopDocs = searcher.search(secondQuery, collectorManager)
        CheckHits.checkEqual(firstQuery, secondTopDocs.scoreDocs, firstTopDocs.scoreDocs)
    }

    @Test
    @Throws(IOException::class)
    fun testOverrideCollectionStatistics() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig()
        val similarity = randomCompatibleSimilarity()
        iwc.similarity = similarity
        val w = RandomIndexWriter(random(), dir, iwc)

        val numMatch = atLeast(10)
        for (i in 0..<numMatch) {
            val doc = Document()
            if (random().nextBoolean()) {
                doc.add(TextField("a", "baz", Store.NO))
                doc.add(TextField("b", "baz", Store.NO))
                for (k in 0..<2) {
                    doc.add(TextField("ab", "baz", Store.NO))
                }
                w.addDocument(doc)
                doc.clear()
            }
            val freqA = random().nextInt(5) + 1
            for (j in 0..<freqA) {
                doc.add(TextField("a", "foo", Store.NO))
            }
            val freqB = random().nextInt(5) + 1
            for (j in 0..<freqB) {
                doc.add(TextField("b", "foo", Store.NO))
            }
            val freqAB = freqA + freqB
            for (j in 0..<freqAB) {
                doc.add(TextField("ab", "foo", Store.NO))
            }
            w.addDocument(doc)
        }

        val reader = w.reader

        val extraMaxDoc = randomIntBetween(0, 10)
        val extraDocCount = randomIntBetween(0, extraMaxDoc)
        val extraSumDocFreq = extraDocCount + randomIntBetween(0, 10)

        val extraSumTotalTermFreqA = extraSumDocFreq + randomIntBetween(0, 10)
        val extraSumTotalTermFreqB = extraSumDocFreq + randomIntBetween(0, 10)
        val extraSumTotalTermFreqAB = extraSumTotalTermFreqA + extraSumTotalTermFreqB

        val searcher =
            object : IndexSearcher(reader) {
                @Throws(IOException::class)
                override fun collectionStatistics(field: String): CollectionStatistics? {
                    val shardStatistics = super.collectionStatistics(field)!!
                    val extraSumTotalTermFreq = when (field) {
                        "a" -> extraSumTotalTermFreqA
                        "b" -> extraSumTotalTermFreqB
                        "ab" -> extraSumTotalTermFreqAB
                        else -> throw AssertionError("should never be called")
                    }
                    return CollectionStatistics(
                        field,
                        shardStatistics.maxDoc + extraMaxDoc,
                        shardStatistics.docCount + extraDocCount,
                        shardStatistics.sumTotalTermFreq + extraSumTotalTermFreq,
                        shardStatistics.sumDocFreq + extraSumDocFreq,
                    )
                }
            }
        searcher.similarity = similarity
        val query = CombinedFieldQuery.Builder("foo").addField("a").addField("b").build()

        checkExpectedHits(searcher, numMatch, query, TermQuery(Term("ab", "foo")))

        reader.close()
        w.close()
        dir.close()
    }

    private class NegativeNormSimilarity : Similarity() {
        override fun computeNorm(state: FieldInvertState): Long {
            return -128
        }

        override fun scorer(
            boost: Float,
            collectionStats: CollectionStatistics,
            vararg termStats: TermStatistics,
        ): SimScorer {
            return BM25Similarity().scorer(boost, collectionStats, *termStats)
        }
    }
}
