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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestUniqueTermCount : LuceneTestCase() {

    lateinit var dir: Directory
    lateinit var reader: IndexReader

    /* expected uniqueTermCount values for our documents */
    var expected: ArrayList<Int> = ArrayList()

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        dir = newDirectory()
        val analyzer = MockAnalyzer(random(), MockTokenizer.SIMPLE, true)
        val config: IndexWriterConfig = newIndexWriterConfig(analyzer)
        config.setMergePolicy(newLogMergePolicy())
        config.setSimilarity(TestSimilarity())
        val writer = RandomIndexWriter(random(), dir, config)
        val doc = Document()
        val foo = newTextField("foo", "", Field.Store.NO)
        doc.add(foo)
        for (i in 0..99) {
            foo.setStringValue(addValue())
            writer.addDocument(doc)
        }
        reader = writer.reader
        writer.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun test() {
        val fooNorms: NumericDocValues? = MultiDocValues.getNormValues(reader, "foo")
        assertNotNull(fooNorms)
        for (i in 0..<reader.maxDoc()) {
            assertEquals(i, fooNorms.nextDoc())
            assertEquals(expected[i].toLong(), fooNorms.longValue())
        }
    }

    /**
     * Makes a bunch of single-char tokens (the max # unique terms will at most be 26). puts the #
     * unique terms into expected, to be checked against the norm.
     */
    private fun addValue(): String {
        val sb = StringBuilder()
        val terms: HashSet<String> = HashSet()
        val num = TestUtil.nextInt(random(), 0, 255)
        for (i in 0..<num) {
            sb.append(' ')
            val term = TestUtil.nextInt(random(), 'a'.code, 'z'.code).toChar()
            sb.append(term)
            terms.add("" + term)
        }
        expected.add(terms.size)
        return sb.toString()
    }

    /** Simple similarity that encodes maxTermFrequency directly  */
    class TestSimilarity : Similarity() {
        override fun computeNorm(state: FieldInvertState): Long {
            return state.uniqueTermCount.toLong()
        }

        override fun scorer(
            boost: Float, collectionStats: CollectionStatistics, vararg termStats: TermStatistics
        ): SimScorer {
            throw UnsupportedOperationException()
        }
    }
}
