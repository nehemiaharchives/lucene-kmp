package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TopScoreDocCollectorManager
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestLongDistanceFeatureQuery : LuceneTestCase() {

    @Test
    fun testEqualsAndHashcode() {
        val q1: Query = LongField.newDistanceFeatureQuery("foo", 3f, 10L, 5L)
        val q2: Query = LongField.newDistanceFeatureQuery("foo", 3f, 10L, 5L)
        QueryUtils.checkEqual(q1, q2)

        val q3: Query = LongField.newDistanceFeatureQuery("bar", 3f, 10L, 5L)
        QueryUtils.checkUnequal(q1, q3)

        val q4: Query = LongField.newDistanceFeatureQuery("foo", 4f, 10L, 5L)
        QueryUtils.checkUnequal(q1, q4)

        val q5: Query = LongField.newDistanceFeatureQuery("foo", 3f, 9L, 5L)
        QueryUtils.checkUnequal(q1, q5)

        val q6: Query = LongField.newDistanceFeatureQuery("foo", 3f, 10L, 6L)
        QueryUtils.checkUnequal(q1, q6)
    }

    @Test
    fun testBasics() {
        val dir: Directory = newDirectory()
        val w =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
            )
        val doc = Document()
        val field = LongField("foo", 0L, Store.NO)
        doc.add(field)

        field.setLongValue(3L)
        w.addDocument(doc)

        field.setLongValue(12L)
        w.addDocument(doc)

        field.setLongValue(8L)
        w.addDocument(doc)

        field.setLongValue(-1L)
        w.addDocument(doc)

        field.setLongValue(7L)
        w.addDocument(doc)

        val reader = w.reader
        val searcher = newSearcher(reader)

        var q: Query = LongField.newDistanceFeatureQuery("foo", 3f, 10L, 5L)
        var collectorManager = TopScoreDocCollectorManager(2, 1)
        var topHits = searcher.search(q, collectorManager)
        assertEquals(2, topHits.scoreDocs.size)

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(1, (3f * (5.0 / (5.0 + 2.0))).toFloat()),
                ScoreDoc(2, (3f * (5.0 / (5.0 + 2.0))).toFloat())
            ),
            topHits.scoreDocs
        )

        q = LongField.newDistanceFeatureQuery("foo", 3f, 7L, 5L)
        collectorManager = TopScoreDocCollectorManager(2, 1)
        topHits = searcher.search(q, collectorManager)
        assertEquals(2, topHits.scoreDocs.size)
        CheckHits.checkExplanations(q, "", searcher)

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(4, (3f * (5.0 / (5.0 + 0.0))).toFloat()),
                ScoreDoc(2, (3f * (5.0 / (5.0 + 1.0))).toFloat())
            ),
            topHits.scoreDocs
        )

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testOverUnderFlow() {
        val dir: Directory = newDirectory()
        val w =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
            )
        val doc = Document()
        val field = LongField("foo", 0L, Store.NO)
        doc.add(field)

        field.setLongValue(3L)
        w.addDocument(doc)

        field.setLongValue(12L)
        w.addDocument(doc)

        field.setLongValue(-10L)
        w.addDocument(doc)

        field.setLongValue(Long.MAX_VALUE)
        w.addDocument(doc)

        field.setLongValue(Long.MIN_VALUE)
        w.addDocument(doc)

        val reader = w.reader
        val searcher = newSearcher(reader)

        var q: Query = LongField.newDistanceFeatureQuery("foo", 3f, Long.MAX_VALUE - 1, 100L)
        val collectorManager = TopScoreDocCollectorManager(2, 1)
        var topHits = searcher.search(q, collectorManager)
        assertEquals(2, topHits.scoreDocs.size)

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(3, (3f * (100.0 / (100.0 + 1.0))).toFloat()),
                ScoreDoc(0, (3f * (100.0 / (100.0 + Long.MAX_VALUE.toDouble()))).toFloat())
            ),
            topHits.scoreDocs
        )

        q = LongField.newDistanceFeatureQuery("foo", 3f, Long.MIN_VALUE + 1, 100L)
        topHits = searcher.search(q, collectorManager)

        assertEquals(2, topHits.scoreDocs.size)
        CheckHits.checkExplanations(q, "", searcher)

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(4, (3f * (100.0 / (100.0 + 1.0))).toFloat()),
                ScoreDoc(0, (3f * (100.0 / (100.0 + Long.MAX_VALUE.toDouble()))).toFloat())
            ),
            topHits.scoreDocs
        )

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMissingField() {
        val reader: IndexReader = MultiReader()
        val searcher = newSearcher(reader)

        val q: Query = LongField.newDistanceFeatureQuery("foo", 3f, 10L, 5L)
        val topHits: TopDocs = searcher.search(q, 2)
        assertEquals(0L, topHits.totalHits.value)
    }

    @Test
    fun testMissingValue() {
        val dir: Directory = newDirectory()
        val w =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
            )
        val doc = Document()
        val field = LongField("foo", 0L, Store.NO)
        doc.add(field)

        field.setLongValue(3L)
        w.addDocument(doc)

        w.addDocument(Document())

        field.setLongValue(7L)
        w.addDocument(doc)

        val reader = w.reader
        val searcher = newSearcher(reader)

        val q: Query = LongField.newDistanceFeatureQuery("foo", 3f, 10L, 5L)
        val collectorManager = TopScoreDocCollectorManager(3, 1)
        val topHits = searcher.search(q, collectorManager)
        assertEquals(2, topHits.scoreDocs.size)

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(2, (3f * (5.0 / (5.0 + 3.0))).toFloat()),
                ScoreDoc(0, (3f * (5.0 / (5.0 + 7.0))).toFloat())
            ),
            topHits.scoreDocs
        )

        CheckHits.checkExplanations(q, "", searcher)

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMultiValued() {
        val dir: Directory = newDirectory()
        val w =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
            )

        var doc = Document()
        for (v in longArrayOf(3L, 1000L, Long.MAX_VALUE)) {
            doc.add(LongField("foo", v, Store.NO))
        }
        w.addDocument(doc)

        doc = Document()
        for (v in longArrayOf(-100L, 12L, 999L)) {
            doc.add(LongField("foo", v, Store.NO))
        }
        w.addDocument(doc)

        doc = Document()
        for (v in longArrayOf(Long.MIN_VALUE, -1000L, 8L)) {
            doc.add(LongField("foo", v, Store.NO))
        }
        w.addDocument(doc)

        doc = Document()
        for (v in longArrayOf(-1L)) {
            doc.add(LongField("foo", v, Store.NO))
        }
        w.addDocument(doc)

        doc = Document()
        for (v in longArrayOf(Long.MIN_VALUE, 7L)) {
            doc.add(LongField("foo", v, Store.NO))
        }
        w.addDocument(doc)

        val reader = w.reader
        val searcher = newSearcher(reader)

        var q: Query = LongField.newDistanceFeatureQuery("foo", 3f, 10L, 5L)
        var collectorManager = TopScoreDocCollectorManager(2, 1)
        var topHits = searcher.search(q, collectorManager)
        assertEquals(2, topHits.scoreDocs.size)

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(1, (3f * (5.0 / (5.0 + 2.0))).toFloat()),
                ScoreDoc(2, (3f * (5.0 / (5.0 + 2.0))).toFloat())
            ),
            topHits.scoreDocs
        )

        q = LongField.newDistanceFeatureQuery("foo", 3f, 7L, 5L)
        collectorManager = TopScoreDocCollectorManager(2, 1)
        topHits = searcher.search(q, collectorManager)
        assertEquals(2, topHits.scoreDocs.size)
        CheckHits.checkExplanations(q, "", searcher)

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(4, (3f * (5.0 / (5.0 + 0.0))).toFloat()),
                ScoreDoc(2, (3f * (5.0 / (5.0 + 1.0))).toFloat())
            ),
            topHits.scoreDocs
        )

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testRandom() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean())))
        val doc = Document()
        val field = LongField("foo", 0L, Store.NO)
        doc.add(field)

        val numDocs = atLeast(10000)
        for (i in 0..<numDocs) {
            val v = random().nextLong()
            field.setLongValue(v)
            w.addDocument(doc)
        }

        val reader: IndexReader = DirectoryReader.open(w)
        val searcher = newSearcher(reader)

        for (iter in 0..<10) {
            val origin = random().nextLong()
            var pivotDistance: Long
            do {
                pivotDistance = random().nextLong()
            } while (pivotDistance <= 0L)
            val boost = (1 + random().nextInt(10)) / 3f
            val q: Query = LongField.newDistanceFeatureQuery("foo", boost, origin, pivotDistance)

            CheckHits.checkTopScores(random(), q, searcher)
        }

        reader.close()
        w.close()
        dir.close()
    }
}
