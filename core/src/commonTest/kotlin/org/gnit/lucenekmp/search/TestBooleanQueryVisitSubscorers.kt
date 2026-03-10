package org.gnit.lucenekmp.search

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.search.similarities.RawTFSimilarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.ScorerIndexSearcher
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import okio.IOException

// TODO: refactor to a base class, that collects freqs from the scorer tree
// and test all queries with it
class TestBooleanQueryVisitSubscorers : LuceneTestCase() {

    private lateinit var analyzer: MockAnalyzer
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher
    private lateinit var scorerSearcher: IndexSearcher
    private lateinit var dir: Directory

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        analyzer = MockAnalyzer(random())
        dir = newDirectory()
        val config: IndexWriterConfig = newIndexWriterConfig(analyzer)
        config.mergePolicy = newLogMergePolicy() // we will use docids to validate
        val writer = RandomIndexWriter(random(), dir, config)
        writer.addDocument(doc("lucene", "lucene is a very popular search engine library"))
        writer.addDocument(doc("solr", "solr is a very popular search server and is using lucene"))
        writer.addDocument(
            doc(
                "nutch",
                "nutch is an internet search engine with web crawler and is using lucene and hadoop"
            )
        )
        reader = writer.getReader(true, false)
        writer.close()
        searcher = newSearcher(reader, true, false)
        searcher.similarity = ClassicSimilarity()
        scorerSearcher = ScorerIndexSearcher(reader)
        scorerSearcher.similarity = RawTFSimilarity()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDisjunctions() {
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term(F1, "lucene")), BooleanClause.Occur.SHOULD)
        bq.add(TermQuery(Term(F2, "lucene")), BooleanClause.Occur.SHOULD)
        bq.add(TermQuery(Term(F2, "search")), BooleanClause.Occur.SHOULD)
        val tfs = getDocCounts(scorerSearcher, bq.build())
        assertEquals(3, tfs.size)
        assertEquals(3, tfs[0])
        assertEquals(2, tfs[1])
        assertEquals(2, tfs[2])
    }

    @Test
    @Throws(Exception::class)
    fun testNestedDisjunctions() {
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term(F1, "lucene")), BooleanClause.Occur.SHOULD)
        val bq2 = BooleanQuery.Builder()
        bq2.add(TermQuery(Term(F2, "lucene")), BooleanClause.Occur.SHOULD)
        bq2.add(TermQuery(Term(F2, "search")), BooleanClause.Occur.SHOULD)
        bq.add(bq2.build(), BooleanClause.Occur.SHOULD)
        val tfs = getDocCounts(scorerSearcher, bq.build())
        assertEquals(3, tfs.size)
        assertEquals(3, tfs[0])
        assertEquals(2, tfs[1])
        assertEquals(2, tfs[2])
    }

    @Test
    @Throws(Exception::class)
    fun testConjunctions() {
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term(F2, "lucene")), BooleanClause.Occur.MUST)
        bq.add(TermQuery(Term(F2, "is")), BooleanClause.Occur.MUST)
        val tfs = getDocCounts(scorerSearcher, bq.build())
        assertEquals(3, tfs.size)
        assertEquals(2, tfs[0])
        assertEquals(3, tfs[1])
        assertEquals(3, tfs[2])
    }

    @Test
    @Throws(Exception::class)
    fun testDisjunctionMatches() {
        val bq1 = BooleanQuery.Builder()
        bq1.add(TermQuery(Term(F1, "lucene")), Occur.SHOULD)
        bq1.add(PhraseQuery(F2, "search", "engine"), Occur.SHOULD)

        val w1 = scorerSearcher.createWeight(scorerSearcher.rewrite(bq1.build()), ScoreMode.COMPLETE, 1f)
        val s1 = w1.scorer(reader.leaves()[0])!!
        assertEquals(0, s1.iterator().nextDoc())
        assertEquals(2, s1.children.size)

        val bq2 = BooleanQuery.Builder()
        bq2.add(TermQuery(Term(F1, "lucene")), Occur.SHOULD)
        bq2.add(PhraseQuery(F2, "search", "library"), Occur.SHOULD)

        val w2 = scorerSearcher.createWeight(scorerSearcher.rewrite(bq2.build()), ScoreMode.COMPLETE, 1f)
        val s2 = w2.scorer(reader.leaves()[0])!!
        assertEquals(0, s2.iterator().nextDoc())
        assertEquals(1, s2.children.size)
    }

    @Test
    @Throws(Exception::class)
    fun testMinShouldMatchMatches() {
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term(F1, "lucene")), Occur.SHOULD)
        bq.add(TermQuery(Term(F2, "lucene")), Occur.SHOULD)
        bq.add(PhraseQuery(F2, "search", "library"), Occur.SHOULD)
        bq.setMinimumNumberShouldMatch(2)

        val w = scorerSearcher.createWeight(scorerSearcher.rewrite(bq.build()), ScoreMode.COMPLETE, 1f)
        val s = w.scorer(reader.leaves()[0])!!
        assertEquals(0, s.iterator().nextDoc())
        assertEquals(2, s.children.size)
    }

    @Test
    @Throws(Exception::class)
    fun testGetChildrenMinShouldMatchSumScorer() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(F2, "nutch")), Occur.SHOULD)
        query.add(TermQuery(Term(F2, "web")), Occur.SHOULD)
        query.add(TermQuery(Term(F2, "crawler")), Occur.SHOULD)
        query.setMinimumNumberShouldMatch(2)
        query.add(MatchAllDocsQuery(), Occur.MUST)
        val scoreSummary = searcher.search(query.build(), ScorerSummarizingCollectorManager())!!
        assertEquals(1, scoreSummary.numHits)
        assertFalse(scoreSummary.summaries.isEmpty())
        for (summary in scoreSummary.summaries) {
            assertEquals(
                "ConjunctionScorer\n" +
                    "    MUST ConstantScoreScorer\n" +
                    "    MUST WANDScorer\n" +
                    "            SHOULD TermScorer\n" +
                    "            SHOULD TermScorer\n" +
                    "            SHOULD TermScorer",
                summary
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testGetChildrenBoosterScorer() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(F2, "nutch")), Occur.SHOULD)
        query.add(TermQuery(Term(F2, "miss")), Occur.SHOULD)
        val scoreSummary = scorerSearcher.search(query.build(), ScorerSummarizingCollectorManager())!!
        assertEquals(1, scoreSummary.numHits)
        assertFalse(scoreSummary.summaries.isEmpty())
        for (summary in scoreSummary.summaries) {
            assertEquals("TermScorer", summary)
        }
    }

    companion object {
        private const val F1 = "title"
        private const val F2 = "body"

        fun doc(v1: String, v2: String): Document {
            val doc = Document()
            doc.add(TextField(F1, v1, Field.Store.YES))
            doc.add(TextField(F2, v2, Field.Store.YES))
            return doc
        }

        @Throws(IOException::class)
        fun getDocCounts(searcher: IndexSearcher, query: Query): MutableMap<Int, Int> {
            return searcher.search(
                query,
                object : CollectorManager<MyCollector, MutableMap<Int, Int>> {
                    override fun newCollector(): MyCollector {
                        return MyCollector()
                    }

                    override fun reduce(collectors: MutableCollection<MyCollector>): MutableMap<Int, Int> {
                        val docCounts = hashMapOf<Int, Int>()
                        for (myCollector in collectors) {
                            docCounts.putAll(myCollector.docCounts)
                        }
                        return docCounts
                    }
                }
            )!!
        }
    }

    class MyCollector : FilterCollector(TopScoreDocCollectorManager(10, null, Int.MAX_VALUE).newCollector()) {

        val docCounts = hashMapOf<Int, Int>()
        private val tqsSet = hashSetOf<Scorer>()

        @Throws(IOException::class)
        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            val docBase = context.docBase
            return object : FilterLeafCollector(super.getLeafCollector(context)) {
                override var scorer: Scorable?
                    get() = super.scorer
                    set(scorer) {
                        super.scorer = scorer
                        tqsSet.clear()
                        fillLeaves(scorer!!, tqsSet)
                    }

                @Throws(IOException::class)
                override fun collect(doc: Int) {
                    var freq = 0
                    for (scorer in tqsSet) {
                        if (doc == scorer.docID()) {
                            freq += scorer.score().toInt()
                        }
                    }
                    docCounts[doc + docBase] = freq
                    super.collect(doc)
                }
            }
        }

        @Throws(IOException::class)
        private fun fillLeaves(scorer: Scorable, set: MutableSet<Scorer>) {
            if (scorer is TermScorer) {
                set.add(scorer)
            } else {
                for (child in scorer.children) {
                    fillLeaves(child.child, set)
                }
            }
        }

        fun topDocs(): TopDocs {
            return (`in` as TopDocsCollector<*>).topDocs()
        }

        fun freq(doc: Int): Int {
            return docCounts[doc]!!
        }
    }

    class ScoreSummary {
        val summaries = mutableListOf<String>()
        var numHits = 0
    }

    class ScorerSummarizingCollectorManager : CollectorManager<ScorerSummarizingCollector, ScoreSummary> {
        override fun newCollector(): ScorerSummarizingCollector {
            return ScorerSummarizingCollector()
        }

        override fun reduce(collectors: MutableCollection<ScorerSummarizingCollector>): ScoreSummary {
            val scoreSummary = ScoreSummary()
            for (collector in collectors) {
                scoreSummary.summaries.addAll(collector.scoreSummary.summaries)
                scoreSummary.numHits += collector.scoreSummary.numHits
            }
            return scoreSummary
        }
    }

    class ScorerSummarizingCollector : Collector {
        val scoreSummary = ScoreSummary()
        override var weight: Weight? = null

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE
        }

        @Throws(IOException::class)
        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            return object : LeafCollector {
                override var scorer: Scorable? = null
                    set(scorer) {
                        field = scorer
                        val builder = StringBuilder()
                        summarizeScorer(builder, scorer!!, 0)
                        scoreSummary.summaries.add(builder.toString())
                    }

                @Throws(IOException::class)
                override fun collect(doc: Int) {
                    scoreSummary.numHits++
                }
            }
        }

        companion object {
            @Throws(IOException::class)
            private fun summarizeScorer(builder: StringBuilder, scorer: Scorable, indent: Int) {
                builder.append(scorer::class.simpleName)
                for (childScorer in scorer.children) {
                    indent(builder, indent + 1).append(childScorer.relationship).append(" ")
                    summarizeScorer(builder, childScorer.child, indent + 2)
                }
            }

            private fun indent(builder: StringBuilder, indent: Int): StringBuilder {
                if (builder.isNotEmpty()) {
                    builder.append("\n")
                }
                for (i in 0 until indent) {
                    builder.append("    ")
                }
                return builder
            }
        }
    }
}
