package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests the maxTermFrequency statistic in FieldInvertState */
class TestMaxTermFrequency : LuceneTestCase() {
    lateinit var dir: Directory
    lateinit var reader: IndexReader
    /* expected maxTermFrequency values for our documents */
    val expected = ArrayList<Int>()

    @Test
    @Throws(Exception::class)
    fun test() {
        setUp()
        try {
            val fooNorms = MultiDocValues.getNormValues(reader, "foo")!!
            for (i in 0..<reader.maxDoc()) {
                assertEquals(i, fooNorms.nextDoc())
                assertEquals(expected[i].toLong(), fooNorms.longValue() and 0xffL)
            }
        } finally {
            tearDown()
        }
    }

    @Throws(Exception::class)
    fun setUp() {
        dir = newDirectory()
        val config =
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.SIMPLE, true))
                .setMergePolicy(newLogMergePolicy())
        config.setSimilarity(TestSimilarity())
        val writer = RandomIndexWriter(random(), dir, config)
        val doc = Document()
        val foo = newTextField("foo", "", Field.Store.NO)
        doc.add(foo)
        for (i in 0..<100) {
            foo.setStringValue(addValue())
            writer.addDocument(doc)
        }
        reader = writer.getReader(applyDeletions = true, writeAllDeletes = false)
        writer.close()
    }

    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        dir.close()
    }

    /**
     * Makes a bunch of single-char tokens (the max freq will at most be 255). shuffles them around,
     * and returns the whole list with Arrays.toString(). This works fine because we use
     * lettertokenizer. puts the max-frequency term into expected, to be checked against the norm.
     */
    private fun addValue(): String {
        val terms = ArrayList<String>()
        val maxCeiling = TestUtil.nextInt(random(), 0, 255)
        var max = 0
        for (ch in 'a'..'z') {
            val num = TestUtil.nextInt(random(), 0, maxCeiling)
            for (i in 0..<num) terms.add(ch.toString())
            max = kotlin.math.max(max, num)
        }
        expected.add(max)
        terms.shuffle(random())
        return terms.toString()
    }

    /** Simple similarity that encodes maxTermFrequency directly as a byte */
    class TestSimilarity : Similarity() {

        override fun computeNorm(state: FieldInvertState): Long {
            return state.maxTermFrequency.toLong()
        }

        override fun scorer(
            boost: Float,
            collectionStats: CollectionStatistics,
            vararg termStats: TermStatistics
        ): SimScorer {
            return object : SimScorer() {

                override fun score(freq: Float, norm: Long): Float {
                    return 0f
                }
            }
        }
    }
}
