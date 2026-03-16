package org.gnit.lucenekmp.queryparser.complexPhrase

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.queries.spans.SpanNearQuery
import org.gnit.lucenekmp.queries.spans.SpanQuery
import org.gnit.lucenekmp.queries.spans.SpanTermQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockSynonymAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestComplexPhraseQuery : LuceneTestCase() {
    private lateinit var rd: Directory
    private lateinit var analyzer: Analyzer
    private val docsContent = arrayOf(
        DocData("john smith", "1", "developer"),
        DocData("johathon smith", "2", "developer"),
        DocData("john percival smith", "3", "designer"),
        DocData("jackson waits tom", "4", "project manager"),
        DocData("johny perkins", "5", "orders pizza"),
        DocData("hapax neverson", "6", "never matches"),
        DocData("dog cigar", "7", "just for synonyms"),
        DocData("dogs don't smoke cigarettes", "8", "just for synonyms"),
    )
    private lateinit var searcher: IndexSearcher
    private lateinit var reader: IndexReader
    private var defaultFieldName = "name"
    private var inOrder = true

    @Test
    fun testComplexPhrases() {
        checkMatches("\"john smith\"", "1")
        checkMatches("\"j*   smyth~\"", "1,2")
        checkMatches("\"(jo* -john)  smith\"", "2")
        checkMatches("\"jo*  smith\"~2", "1,2,3")
        checkMatches("\"jo* [sma TO smZ]\" ", "1,2")
        checkMatches("\"john\"", "1,3")
        checkMatches("\"(john OR johathon)  smith\"", "1,2")
        checkMatches("\"(john OR nosuchword*)  smith\"", "1")
        checkMatches("\"(jo* -john) smyth~\"", "2")
        checkMatches("\"john  nosuchword*\"", "")
        checkBadQuery("\"jo*  id:1 smith\"")
        checkBadQuery("\"jo* \" + '\"' + \"smith\" + '\"' + \" \" + '\"'")
    }

    @Test
    fun testSingleTermPhrase() {
        checkMatches("\"joh*\"", "1,2,3,5")
        checkMatches("\"joh~\"", "1,3,5")
        checkMatches("\"joh*\" \"tom\"", "1,2,3,4,5")
        checkMatches("+\"j*\" +\"tom\"", "4")
        checkMatches("\"jo*\" \"[sma TO smZ]\" ", "1,2,3,5,8")
        checkMatches("+\"j*hn\" +\"sm*h\"", "1,3")
    }

    @Test
    fun testSynonyms() {
        checkMatches("\"dogs\"", "8")
        val synonym = MockSynonymAnalyzer()
        checkMatches("\"dogs\"", "7,8", synonym)
        checkMatches("\"dog\"", "7", synonym)
        checkMatches("\"dogs cigar*\"", "")
        checkMatches("\"dog cigar*\"", "7")
        checkMatches("\"dogs cigar*\"", "7", synonym)
        checkMatches("\"dog cigar*\"", "7", synonym)
        checkMatches("\"dogs cigar*\"~2", "7,8", synonym)
        checkMatches("\"dog cigar*\"~2", "7", synonym)
    }

    @Test
    fun testUnOrderedProximitySearches() {
        inOrder = true
        checkMatches("\"smith jo*\"~2", "")
        inOrder = false
        checkMatches("\"smith jo*\"~2", "1,2,3")
    }

    @Test
    fun testFieldedQuery() {
        checkMatches("name:\"john smith\"", "1")
        checkMatches("name:\"j*   smyth~\"", "1,2")
        checkMatches("role:\"developer\"", "1,2")
        checkMatches("role:\"p* manager\"", "4")
        checkMatches("role:de*", "1,2,3")
        checkMatches("name:\"j* smyth~\"~5", "1,2,3")
        checkMatches("role:\"p* manager\" AND name:jack*", "4")
        checkMatches("+role:developer +name:jack*", "")
        checkMatches("name:\"john smith\"~2 AND role:designer AND id:3", "3")
    }

    @Test
    fun testToStringContainsSlop() {
        val qp = ComplexPhraseQueryParser("", analyzer)
        val slop = random().nextInt(31) + 1

        val qString = "name:\"j* smyth~\"~$slop"
        val query = requireNotNull(qp.parse(qString))
        val actualQStr = query.toString()
        assertTrue(actualQStr.endsWith("~$slop"), "Slop is not shown in toString()")
        assertEquals(qString, actualQStr)

        val string = "\"j* smyth~\""
        val q = requireNotNull(qp.parse(string))
        assertEquals(string, q.toString())
    }

    @Test
    fun testHashcodeEquals() {
        val qp = ComplexPhraseQueryParser(defaultFieldName, analyzer)
        qp.setInOrder(true)
        qp.fuzzyPrefixLength = 1

        val qString = "\"aaa* bbb*\""
        val q = requireNotNull(qp.parse(qString))
        var q2 = requireNotNull(qp.parse(qString))

        assertEquals(q.hashCode(), q2.hashCode())
        assertEquals(q, q2)

        qp.setInOrder(false)
        q2 = requireNotNull(qp.parse(qString))
        assertTrue(q.hashCode() != q2.hashCode())
        assertTrue(q != q2)
        assertTrue(q2 != q)
    }

    @Test
    fun testBoosts() {
        val topLevel = "(\"john^3 smit*\"~4)^2"
        val parser = ComplexPhraseQueryParser("name", StandardAnalyzer())
        parser.setInOrder(true)
        val actual = searcher.rewrite(requireNotNull(parser.parse(topLevel)))
        val expected = BoostQuery(
            SpanNearQuery(
                arrayOf<SpanQuery>(
                    SpanTermQuery(Term("name", "john")),
                    SpanTermQuery(Term("name", "smith")),
                ),
                4,
                true,
            ),
            2f,
        )
        assertEquals(expected, actual)
    }

    private fun checkBadQuery(qString: String) {
        val qp = ComplexPhraseQueryParser(defaultFieldName, analyzer)
        qp.setInOrder(inOrder)
        expectThrows(Throwable::class) { qp.parse(qString) }
    }

    private fun checkMatches(qString: String, expectedVals: String) {
        checkMatches(qString, expectedVals, analyzer)
    }

    private fun checkMatches(qString: String, expectedVals: String, anAnalyzer: Analyzer) {
        val qp = ComplexPhraseQueryParser(defaultFieldName, anAnalyzer)
        qp.setInOrder(inOrder)
        qp.fuzzyPrefixLength = 1
        val q = requireNotNull(qp.parse(qString))

        val expecteds = HashSet<String>()
        val vals = expectedVals.split(",")
        for (value in vals) {
            if (value.isNotEmpty()) {
                expecteds.add(value)
            }
        }

        val td: TopDocs = searcher.search(q, 10)
        val sd: Array<ScoreDoc> = td.scoreDocs
        val storedFields: StoredFields = searcher.storedFields()
        for (scoreDoc in sd) {
            val doc: Document = storedFields.document(scoreDoc.doc)
            val id = doc.get("id")
            assertTrue(expecteds.contains(id), "$qString matched doc#$id not expected")
            expecteds.remove(id)
        }

        assertEquals(0, expecteds.size, "$qString missing some matches ")
    }

    @BeforeTest
    fun setUp() {
        analyzer = MockAnalyzer(random())
        rd = newDirectory()
        val w = IndexWriter(rd, newIndexWriterConfig(analyzer))
        for (docData in docsContent) {
            val doc = Document()
            doc.add(newTextField("name", docData.name, Field.Store.YES))
            doc.add(newTextField("id", docData.id, Field.Store.YES))
            doc.add(newTextField("role", docData.role, Field.Store.YES))
            w.addDocument(doc)
        }
        w.close()
        reader = DirectoryReader.open(rd)
        searcher = newSearcher(reader)
    }

    @AfterTest
    fun tearDown() {
        reader.close()
        rd.close()
    }

    class DocData(var name: String, var id: String, var role: String)
}
