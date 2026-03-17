package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.DateTools
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Unit test for sorting code. */
class TestCustomSearcherSort : LuceneTestCase() {
    private var index: Directory? = null
    private var reader: IndexReader? = null
    private var query: Query? = null

    // reduced from 20000 to 2000 to speed up test...
    private var INDEX_SIZE = 0

    /** Create index and query for test cases. */
    @BeforeTest
    @Throws(Exception::class)
    fun setUpTestCustomSearcherSort() {
        INDEX_SIZE = atLeast(2000)
        index = newDirectory()
        val writer = RandomIndexWriter(random(), index!!)
        val random = RandomGen(random())
        for (i in 0..<INDEX_SIZE) {
            // don't decrease; if too low the problem doesn't show up
            val doc = Document()
            if ((i % 5) != 0) {
                // some documents must not have an entry in the first sort field
                doc.add(SortedDocValuesField("publicationDate_", BytesRef(random.getLuceneDate())))
            }
            if ((i % 7) == 0) {
                // some documents to match the query (see below)
                doc.add(newTextField("content", "test", Field.Store.YES))
            }
            // every document has a defined 'mandant' field
            doc.add(newStringField("mandant", (i % 3).toString(), Field.Store.YES))
            writer.addDocument(doc)
        }
        reader = writer.reader
        writer.close()
        query = TermQuery(Term("content", "test"))
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDownTestCustomSearcherSort() {
        reader!!.close()
        index!!.close()
    }

    /** Run the test using two CustomSearcher instances. */
    @Test
    @Throws(Exception::class)
    fun testFieldSortCustomSearcher() {
        val custSort =
            Sort(
                SortField("publicationDate_", SortField.Type.STRING),
                SortField.FIELD_SCORE,
            )
        val searcher: IndexSearcher = CustomSearcher(reader!!, 2)
        matchHits(searcher, custSort)
    }

    /** Run the test using one CustomSearcher wrapped by a MultiSearcher. */
    @Test
    @Throws(Exception::class)
    fun testFieldSortSingleSearcher() {
        val custSort =
            Sort(
                SortField("publicationDate_", SortField.Type.STRING),
                SortField.FIELD_SCORE,
            )
        val searcher: IndexSearcher = CustomSearcher(reader!!, 2)
        matchHits(searcher, custSort)
    }

    // make sure the documents returned by the search match the expected list
    @Throws(Exception::class)
    private fun matchHits(searcher: IndexSearcher, sort: Sort) {
        val hitsByRank = searcher.search(query!!, Int.MAX_VALUE).scoreDocs
        checkHits(hitsByRank, "Sort by rank: ")
        val resultMap = mutableMapOf<Int, Int>()
        for (hitid in hitsByRank.indices) {
            resultMap[hitsByRank[hitid].doc] = hitid
        }

        val resultSort = searcher.search(query!!, Int.MAX_VALUE, sort).scoreDocs
        checkHits(resultSort, "Sort by custom criteria: ")

        for (hitid in resultSort.indices) {
            val idHitDate = resultSort[hitid].doc
            if (!resultMap.containsKey(idHitDate)) {
                log("ID $idHitDate not found. Possibliy a duplicate.")
            }
            assertTrue(resultMap.containsKey(idHitDate))
            resultMap.remove(idHitDate)
        }
        if (resultMap.size == 0) {
            // log("All hits matched");
        } else {
            log("Couldn't match ${resultMap.size} hits.")
        }
        assertEquals(0, resultMap.size)
    }

    /** Check the hits for duplicates. */
    private fun checkHits(hits: Array<ScoreDoc>, prefix: String) {
        val idMap = mutableMapOf<Int, Int>()
        for (docnum in hits.indices) {
            val luceneId = hits[docnum].doc
            if (idMap.containsKey(luceneId)) {
                val message = StringBuilder(prefix)
                message.append("Duplicate key for hit index = ")
                message.append(docnum)
                message.append(", previous index = ")
                message.append(idMap[luceneId].toString())
                message.append(", Lucene ID = ")
                message.append(luceneId)
                log(message.toString())
            } else {
                idMap[luceneId] = docnum
            }
        }
    }

    // Simply write to console - choosen to be independant of log4j etc
    private fun log(message: String) {
        if (VERBOSE) println(message)
    }

    class CustomSearcher(
        r: IndexReader,
        private val switcher: Int,
    ) : IndexSearcher(r) {
        override fun search(query: Query, n: Int, sort: Sort): TopFieldDocs {
            val bq = BooleanQuery.Builder()
            bq.add(query, BooleanClause.Occur.MUST)
            bq.add(TermQuery(Term("mandant", switcher.toString())), BooleanClause.Occur.MUST)
            return super.search(bq.build(), n, sort)
        }

        override fun search(query: Query, n: Int): TopDocs {
            val bq = BooleanQuery.Builder()
            bq.add(query, BooleanClause.Occur.MUST)
            bq.add(TermQuery(Term("mandant", switcher.toString())), BooleanClause.Occur.MUST)
            return super.search(bq.build(), n)
        }
    }

    private class RandomGen(
        private val random: Random,
    ) {
        private val baseTimeMillis = 318_729_600_000L

        // Just to generate some different Lucene Date strings
        fun getLuceneDate(): String {
            return DateTools.timeToString(
                baseTimeMillis + random.nextInt().toLong() - Int.MIN_VALUE.toLong(),
                DateTools.Resolution.DAY,
            )
        }
    }
}
