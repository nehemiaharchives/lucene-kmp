package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestWildcardRandom : LuceneTestCase() {

    private var searcher: IndexSearcher? = null
    private var reader: IndexReader? = null
    private var dir: Directory? = null

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        //super.setUp()
        dir = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                dir!!,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(TestUtil.nextInt(random(), 50, 1000))
            )

        val doc = Document()
        val field = newStringField("field", "", Field.Store.NO)
        doc.add(field)

        for (i in 0..999) {
            field.setStringValue(i.toString().padStart(3, '0'))
            writer.addDocument(doc)
        }

        reader = writer.reader
        searcher = newSearcher(reader!!)
        writer.close()
        if (VERBOSE) {
            println("TEST: setUp searcher=$searcher")
        }
    }

    private fun N(): Char {
        return (0x30 + random().nextInt(10)).toChar()
    }

    private fun fillPattern(wildcardPattern: String): String {
        val sb = StringBuilder()
        for (i in 0..<wildcardPattern.length) {
            when (wildcardPattern.get(i)) {
                'N' -> sb.append(N())
                else -> sb.append(wildcardPattern.get(i))
            }
        }
        return sb.toString()
    }

    @Throws(Exception::class)
    private fun assertPatternHits(pattern: String, numHits: Int) {
        // TODO: run with different rewrites
        val filledPattern = fillPattern(pattern)
        if (VERBOSE) {
            println("TEST: run wildcard pattern=$pattern filled=$filledPattern")
        }
        val wq: Query = WildcardQuery(Term("field", filledPattern))
        val docs: TopDocs = searcher!!.search(wq, 25)
        assertEquals(numHits, docs.totalHits.value.toInt(), "Incorrect hits for pattern: $pattern")
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader!!.close()
        dir!!.close()
    }

    @Test
    @Throws(Exception::class)
    fun testWildcards() {
        val num = atLeast(1)
        for (i in 0..<num) {
            assertPatternHits("NNN", 1)
            assertPatternHits("?NN", 10)
            assertPatternHits("N?N", 10)
            assertPatternHits("NN?", 10)
        }

        for (i in 0..<num) {
            assertPatternHits("??N", 100)
            assertPatternHits("N??", 100)
            assertPatternHits("???", 1000)

            assertPatternHits("NN*", 10)
            assertPatternHits("N*", 100)
            assertPatternHits("*", 1000)

            assertPatternHits("*NN", 10)
            assertPatternHits("*N", 100)

            assertPatternHits("N*N", 10)

            // combo of ? and * operators
            assertPatternHits("?N*", 100)
            assertPatternHits("N?*", 100)

            assertPatternHits("*N?", 100)
            assertPatternHits("*??", 1000)
            assertPatternHits("*?N", 100)
        }
    }
}
