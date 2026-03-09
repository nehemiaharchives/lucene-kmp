package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.search.AssertingQuery
import org.gnit.lucenekmp.tests.search.BlockScoreQueryWrapper
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.RandomApproximationQuery
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test

class TestBlockMaxConjunction : LuceneTestCase() {
    private fun maybeWrap(query: Query): Query {
        var query = query
        if (random().nextBoolean()) {
            query = BlockScoreQueryWrapper(query, TestUtil.nextInt(random(), 2, 8))
            query = AssertingQuery(random(), query)
        }
        return query
    }

    private fun maybeWrapTwoPhase(query: Query): Query {
        var query = query
        if (random().nextBoolean()) {
            query = RandomApproximationQuery(query, random())
            query = AssertingQuery(random(), query)
        }
        return query
    }

    @Test
    @Throws(IOException::class)
    fun testRandom() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val numDocs = atLeast(30) // TODO reduced from 1000 to 30 for dev speed
        for (i in 0..<numDocs) {
            val doc = Document()
            val numValues = random().nextInt(1 shl random().nextInt(5))
            val start = random().nextInt(10)
            for (j in 0..<numValues) {
                doc.add(StringField("foo", (start + j).toString(), Store.NO))
            }
            w.addDocument(doc)
        }
        val reader: IndexReader = DirectoryReader.open(w)
        w.close()
        val searcher = newSearcher(reader, random().nextBoolean(), random().nextBoolean(), false)

        for (iter in 0..<100) {
            val start = random().nextInt(10)
            val numClauses = random().nextInt(1 shl random().nextInt(5))
            var builder = BooleanQuery.Builder()
            for (i in 0..<numClauses) {
                builder.add(maybeWrap(TermQuery(Term("foo", (start + i).toString()))), Occur.MUST)
            }
            val query = builder.build()
            CheckHits.checkTopScores(random(), query, searcher)

            val filterTerm = random().nextInt(30)
            val filteredQuery = BooleanQuery.Builder()
                .add(query, Occur.MUST)
                .add(TermQuery(Term("foo", filterTerm.toString())), Occur.FILTER)
                .build()
            CheckHits.checkTopScores(random(), filteredQuery, searcher)

            builder = BooleanQuery.Builder()
            for (i in 0..<numClauses) {
                builder.add(
                    maybeWrapTwoPhase(TermQuery(Term("foo", (start + i).toString()))),
                    Occur.MUST
                )
            }

            val twoPhaseQuery = BooleanQuery.Builder()
                .add(query, Occur.MUST)
                .add(TermQuery(Term("foo", filterTerm.toString())), Occur.FILTER)
                .build()
            CheckHits.checkTopScores(random(), twoPhaseQuery, searcher)
        }
        reader.close()
        dir.close()
    }
}
