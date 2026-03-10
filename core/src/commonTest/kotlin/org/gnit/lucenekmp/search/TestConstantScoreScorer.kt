package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestConstantScoreScorer : LuceneTestCase() {
    companion object {
        private const val FIELD = "f"
        private val VALUES =
            arrayOf("foo", "bar", "foo bar", "bar foo", "foo not bar", "bar foo bar", "azerty")

        private val TERM_QUERY: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term(FIELD, "foo")), Occur.MUST)
                .add(TermQuery(Term(FIELD, "bar")), Occur.MUST)
                .build()
        private val PHRASE_QUERY: Query = PhraseQuery(FIELD, "foo", "bar")
    }

    @Test
    @Throws(Exception::class)
    fun testMatching_ScoreMode_COMPLETE() {
        testMatching(ScoreMode.COMPLETE)
    }

    @Test
    @Throws(Exception::class)
    fun testMatching_ScoreMode_COMPLETE_NO_SCORES() {
        testMatching(ScoreMode.COMPLETE_NO_SCORES)
    }

    @Throws(Exception::class)
    private fun testMatching(scoreMode: ScoreMode) {
        TestConstantScoreScorerIndex().use { index ->
            var doc: Int
            val scorer = index.constantScoreScorer(TERM_QUERY, 1f, scoreMode)

            // "foo bar" match
            doc = scorer.iterator().nextDoc()
            assertEquals(2, doc)
            assertEquals(1f, scorer.score(), 0f)

            // should not reset iterator
            scorer.minCompetitiveScore = 2f
            assertEquals(doc, scorer.docID())
            assertEquals(doc, scorer.iterator().docID())
            assertEquals(1f, scorer.score(), 0f)

            // "bar foo" match
            doc = scorer.iterator().nextDoc()
            assertEquals(3, doc)
            assertEquals(1f, scorer.score(), 0f)

            // "foo not bar" match
            doc = scorer.iterator().nextDoc()
            assertEquals(4, doc)
            assertEquals(1f, scorer.score(), 0f)

            // "foo bar foo" match
            doc = scorer.iterator().nextDoc()
            assertEquals(5, doc)
            assertEquals(1f, scorer.score(), 0f)

            doc = scorer.iterator().nextDoc()
            assertEquals(NO_MORE_DOCS, doc)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMatching_ScoreMode_TOP_SCORES() {
        TestConstantScoreScorerIndex().use { index ->
            var doc: Int
            val scorer = index.constantScoreScorer(TERM_QUERY, 1f, ScoreMode.TOP_SCORES)

            // "foo bar" match
            doc = scorer.iterator().nextDoc()
            assertEquals(2, doc)
            assertEquals(1f, scorer.score(), 0f)

            scorer.minCompetitiveScore = 2f
            assertEquals(doc, scorer.docID())
            assertEquals(doc, scorer.iterator().docID())
            assertEquals(1f, scorer.score(), 0f)

            doc = scorer.iterator().nextDoc()
            assertEquals(NO_MORE_DOCS, doc)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testTwoPhaseMatching_ScoreMode_COMPLETE() {
        testTwoPhaseMatching(ScoreMode.COMPLETE)
    }

    @Test
    @Throws(Exception::class)
    fun testTwoPhaseMatching_ScoreMode_COMPLETE_NO_SCORES() {
        testTwoPhaseMatching(ScoreMode.COMPLETE_NO_SCORES)
    }

    @Throws(Exception::class)
    private fun testTwoPhaseMatching(scoreMode: ScoreMode) {
        TestConstantScoreScorerIndex().use { index ->
            var doc: Int
            val scorer = index.constantScoreScorer(PHRASE_QUERY, 1f, scoreMode)

            // "foo bar" match
            doc = scorer.iterator().nextDoc()
            assertEquals(2, doc)
            assertEquals(1f, scorer.score(), 0f)

            // should not reset iterator
            scorer.minCompetitiveScore = 2f
            assertEquals(doc, scorer.docID())
            assertEquals(doc, scorer.iterator().docID())
            assertEquals(1f, scorer.score(), 0f)

            // "foo not bar" will match the approximation but not the two phase iterator

            // "foo bar foo" match
            doc = scorer.iterator().nextDoc()
            assertEquals(5, doc)
            assertEquals(1f, scorer.score(), 0f)

            doc = scorer.iterator().nextDoc()
            assertEquals(NO_MORE_DOCS, doc)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testTwoPhaseMatching_ScoreMode_TOP_SCORES() {
        TestConstantScoreScorerIndex().use { index ->
            var doc: Int
            val scorer = index.constantScoreScorer(PHRASE_QUERY, 1f, ScoreMode.TOP_SCORES)

            // "foo bar" match
            doc = scorer.iterator().nextDoc()
            assertEquals(2, doc)
            assertEquals(1f, scorer.score(), 0f)

            scorer.minCompetitiveScore = 2f
            assertEquals(doc, scorer.docID())
            assertEquals(doc, scorer.iterator().docID())
            assertEquals(1f, scorer.score(), 0f)

            doc = scorer.iterator().nextDoc()
            assertEquals(NO_MORE_DOCS, doc)
        }
    }

    internal class TestConstantScoreScorerIndex : AutoCloseable {
        private val directory: Directory
        private val writer: RandomIndexWriter
        private val reader: IndexReader

        @Throws(IOException::class)
        constructor() {
            directory = newDirectory()

            writer =
                RandomIndexWriter(
                    random(),
                    directory,
                    newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean())),
                )

            for (VALUE in VALUES) {
                val doc = Document()
                doc.add(newTextField(FIELD, VALUE, Field.Store.YES))
                writer.addDocument(doc)
            }
            writer.forceMerge(1)

            reader = writer.getReader(applyDeletions = true, writeAllDeletes = false)
            writer.close()
        }

        @Throws(IOException::class)
        fun constantScoreScorer(query: Query, score: Float, scoreMode: ScoreMode): ConstantScoreScorer {
            val searcher = newSearcher(reader)
            val weight = searcher.createWeight(ConstantScoreQuery(query), scoreMode, 1f)
            val leaves: List<LeafReaderContext> = searcher.indexReader.leaves()

            assertEquals(1, leaves.size)

            val context = leaves[0]
            val scorer = weight.scorer(context)!!

            return if (scorer.twoPhaseIterator() == null) {
                ConstantScoreScorer(score, scoreMode, scorer.iterator())
            } else {
                ConstantScoreScorer(score, scoreMode, scorer.twoPhaseIterator()!!)
            }
        }

        override fun close() {
            reader.close()
            directory.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testEarlyTermination() {
        val analyzer = MockAnalyzer(random())
        val dir = newDirectory()
        val iw =
            IndexWriter(
                dir,
                newIndexWriterConfig(analyzer)
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(newLogMergePolicy()),
            )
        val numDocs = 50
        for (i in 0..<numDocs) {
            val doc = Document()
            val f = newTextField("key", if (i % 2 == 0) "foo bar" else "baz", Field.Store.YES)
            doc.add(f)
            iw.addDocument(doc)
        }
        val ir = DirectoryReader.open(iw)

        // Don't use threads so that we can assert on the number of visited hits
        val `is` = newSearcher(ir, true, true, false)

        var c = TopScoreDocCollectorManager(10, 10)
        var topDocs = `is`.search(ConstantScoreQuery(TermQuery(Term("key", "foo"))), c)
        assertEquals(11L, topDocs.totalHits.value)
        assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)

        c = TopScoreDocCollectorManager(10, 10)
        val query =
            BooleanQuery.Builder()
                .add(ConstantScoreQuery(TermQuery(Term("key", "foo"))), Occur.SHOULD)
                .add(ConstantScoreQuery(TermQuery(Term("key", "bar"))), Occur.FILTER)
                .build()
        topDocs = `is`.search(query, c)
        assertEquals(11L, topDocs.totalHits.value)
        assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)

        iw.close()
        ir.close()
        dir.close()
    }
}
