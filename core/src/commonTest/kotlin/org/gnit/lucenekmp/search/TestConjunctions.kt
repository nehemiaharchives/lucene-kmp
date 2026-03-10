package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.similarities.RawTFSimilarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestConjunctions : LuceneTestCase() {
    private lateinit var analyzer: MockAnalyzer
    private lateinit var dir: Directory
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher

    companion object {
        const val F1: String = "title"
        const val F2: String = "body"

        fun doc(v1: String, v2: String): Document {
            val doc = Document()
            doc.add(StringField(F1, v1, Field.Store.YES))
            doc.add(TextField(F2, v2, Field.Store.YES))
            return doc
        }
    }

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
        reader = writer.getReader(applyDeletions = true, writeAllDeletes = false)
        writer.close()
        searcher = newSearcher(reader)
        searcher.similarity = RawTFSimilarity()
    }

    @Test
    @Throws(Exception::class)
    fun testTermConjunctionsWithOmitTF() {
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term(F1, "nutch")), BooleanClause.Occur.MUST)
        bq.add(TermQuery(Term(F2, "is")), BooleanClause.Occur.MUST)
        val td = searcher.search(bq.build(), 3)
        assertEquals(1, td.totalHits.value)
        assertEquals(3f, td.scoreDocs[0].score, 0.001f) // f1:nutch + f2:is + f2:is
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testScorerGetChildren() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        doc.add(newTextField("field", "a b", Field.Store.NO))
        w.addDocument(doc)
        val r = DirectoryReader.open(w)
        val b = BooleanQuery.Builder()
        b.add(TermQuery(Term("field", "a")), BooleanClause.Occur.MUST)
        b.add(TermQuery(Term("field", "b")), BooleanClause.Occur.FILTER)
        val q: Query = b.build()
        val s = IndexSearcher(r)
        s.search(
            q,
            object : CollectorManager<TestCollector, Unit> {
                override fun newCollector(): TestCollector {
                    return TestCollector()
                }

                override fun reduce(collectors: MutableCollection<TestCollector>) {
                    for (collector in collectors) {
                        assertTrue(collector.setScorerCalled.load())
                    }
                }
            }
        )
        IOUtils.close(r, w, dir)
    }

    @OptIn(ExperimentalAtomicApi::class)
    private class TestCollector : SimpleCollector() {
        val setScorerCalled = AtomicBoolean(false)

        override var weight: Weight? = null
            set(value) {
                field = value
                val query = value!!.query as BooleanQuery
                val clauseList = query.clauses()
                assertEquals(2, clauseList.size)
                val terms = HashSet<String>()
                for (clause in clauseList) {
                    assert(clause.query is TermQuery)
                    val term = (clause.query as TermQuery).getTerm()
                    assertEquals("field", term.field())
                    terms.add(term.text())
                }
                assertEquals(2, terms.size)
                assertTrue(terms.contains("a"))
                assertTrue(terms.contains("b"))
            }

        override var scorer: Scorable?
            get() = super.scorer
            set(s) {
                val childScorers = s!!.children
                setScorerCalled.store(true)
                assertEquals(2, childScorers.size)
            }

        @Throws(IOException::class)
        override fun collect(doc: Int) {}

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE
        }
    }
}
