package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FilteredTermsEnum
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestMultiTermQueryRewrites : LuceneTestCase() {
    private var dir: Directory? = null
    private var sdir1: Directory? = null
    private var sdir2: Directory? = null
    private var reader: IndexReader? = null
    private var multiReader: IndexReader? = null
    private var multiReaderDupls: IndexReader? = null
    private var searcher: IndexSearcher? = null
    private var multiSearcher: IndexSearcher? = null
    private var multiSearcherDupls: IndexSearcher? = null

    @BeforeTest
    @Throws(Exception::class)
    fun beforeClass() {
        dir = newDirectory()
        sdir1 = newDirectory()
        sdir2 = newDirectory()
        val writer = RandomIndexWriter(random(), dir!!, MockAnalyzer(random()))
        val swriter1 = RandomIndexWriter(random(), sdir1!!, MockAnalyzer(random()))
        val swriter2 = RandomIndexWriter(random(), sdir2!!, MockAnalyzer(random()))

        for (i in 0..<10) {
            val doc = Document()
            doc.add(newStringField("data", i.toString(), Field.Store.NO))
            writer.addDocument(doc)
            (if (i % 2 == 0) swriter1 else swriter2).addDocument(doc)
        }
        writer.forceMerge(1)
        swriter1.forceMerge(1)
        swriter2.forceMerge(1)
        writer.close()
        swriter1.close()
        swriter2.close()

        reader = DirectoryReader.open(dir!!)
        searcher = newSearcher(reader!!)

        multiReader = MultiReader(arrayOf(DirectoryReader.open(sdir1!!), DirectoryReader.open(sdir2!!)), true)
        multiSearcher = newSearcher(multiReader!!)

        multiReaderDupls = MultiReader(arrayOf(DirectoryReader.open(sdir1!!), DirectoryReader.open(dir!!)), true)
        multiSearcherDupls = newSearcher(multiReaderDupls!!)
    }

    @AfterTest
    @Throws(Exception::class)
    fun afterClass() {
        reader!!.close()
        multiReader!!.close()
        multiReaderDupls!!.close()
        dir!!.close()
        sdir1!!.close()
        sdir2!!.close()
        reader = null
        multiReader = null
        multiReaderDupls = null
        searcher = null
        multiSearcher = null
        multiSearcherDupls = null
        dir = null
        sdir1 = null
        sdir2 = null
    }

    private fun extractInnerQuery(q: Query): Query {
        var q = q
        if (q is ConstantScoreQuery) {
            // wrapped as ConstantScoreQuery
            q = q.query
        }
        return q
    }

    private fun extractTerm(q: Query): Term {
        val query = extractInnerQuery(q)
        return (query as TermQuery).getTerm()
    }

    private fun checkBooleanQueryOrder(q: Query) {
        val query = extractInnerQuery(q)
        val bq = query as BooleanQuery
        var last: Term? = null
        var act: Term
        for (clause in bq.clauses()) {
            act = extractTerm(clause.query)
            if (last != null) {
                assertTrue(last.compareTo(act) < 0, "sort order of terms in BQ violated")
            }
            last = act
        }
    }

    @Throws(Exception::class)
    private fun checkDuplicateTerms(method: MultiTermQuery.RewriteMethod) {
        val mtq = TermRangeQuery.newStringRange("data", "2", "7", true, true, method)
        val q1 = searcher!!.rewrite(mtq)
        val q2 = multiSearcher!!.rewrite(mtq)
        val q3 = multiSearcherDupls!!.rewrite(mtq)
        if (VERBOSE) {
            println()
            println("single segment: $q1")
            println("multi segment: $q2")
            println("multi segment with duplicates: $q3")
        }
        assertEquals(q1, q2, "The multi-segment case must produce same rewritten query")
        assertEquals(q1, q3, "The multi-segment case with duplicates must produce same rewritten query")
        checkBooleanQueryOrder(q1)
        checkBooleanQueryOrder(q2)
        checkBooleanQueryOrder(q3)
    }

    @Test
    @Throws(Exception::class)
    fun testRewritesWithDuplicateTerms() {
        checkDuplicateTerms(MultiTermQuery.SCORING_BOOLEAN_REWRITE)

        checkDuplicateTerms(MultiTermQuery.CONSTANT_SCORE_BOOLEAN_REWRITE)

        // use a large PQ here to only test duplicate terms and dont mix up when all scores are equal
        checkDuplicateTerms(MultiTermQuery.TopTermsScoringBooleanQueryRewrite(1024))
        checkDuplicateTerms(MultiTermQuery.TopTermsBoostOnlyBooleanQueryRewrite(1024))
    }

    private fun checkBooleanQueryBoosts(bq: BooleanQuery) {
        for (clause in bq.clauses()) {
            val boostQ = clause.query as BoostQuery
            val mtq = boostQ.query as TermQuery
            assertEquals(
                mtq.getTerm().text().toFloat(),
                boostQ.boost,
                0f,
                "Parallel sorting of boosts in rewrite mode broken",
            )
        }
    }

    @Throws(Exception::class)
    private fun checkBoosts(method: MultiTermQuery.RewriteMethod) {
        val mtq =
            object : MultiTermQuery("data", method) {
                @Throws(IOException::class)
                override fun getTermsEnum(terms: Terms, atts: AttributeSource): TermsEnum {
                    return object : FilteredTermsEnum(terms.iterator()) {
                        val boostAtt = attributes().addAttribute(BoostAttribute::class)

                        override fun accept(term: BytesRef): AcceptStatus {
                            boostAtt.boost = term.utf8ToString().toFloat()
                            if (term.length == 0) {
                                return AcceptStatus.NO
                            }
                            val c = (term.bytes[term.offset].toInt() and 0xff).toChar()
                            return if (c >= '2') {
                                if (c <= '7') {
                                    AcceptStatus.YES
                                } else {
                                    AcceptStatus.END
                                }
                            } else {
                                AcceptStatus.NO
                            }
                        }
                    }
                }

                override fun toString(field: String?): String {
                    return "dummy"
                }

                override fun visit(visitor: QueryVisitor) {}
            }
        val q1 = searcher!!.rewrite(mtq)
        val q2 = multiSearcher!!.rewrite(mtq)
        val q3 = multiSearcherDupls!!.rewrite(mtq)
        if (VERBOSE) {
            println()
            println("single segment: $q1")
            println("multi segment: $q2")
            println("multi segment with duplicates: $q3")
        }
        assertEquals(q1, q2, "The multi-segment case must produce same rewritten query")
        assertEquals(q1, q3, "The multi-segment case with duplicates must produce same rewritten query")
        if (q1 is MatchNoDocsQuery) {
            assertTrue(q1 is MatchNoDocsQuery)
            assertTrue(q3 is MatchNoDocsQuery)
        } else {
            checkBooleanQueryBoosts(q1 as BooleanQuery)
            checkBooleanQueryBoosts(q2 as BooleanQuery)
            checkBooleanQueryBoosts(q3 as BooleanQuery)
            assert(false)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBoosts() {
        checkBoosts(MultiTermQuery.SCORING_BOOLEAN_REWRITE)

        // use a large PQ here to only test boosts and dont mix up when all scores are equal
        checkBoosts(MultiTermQuery.TopTermsScoringBooleanQueryRewrite(1024))
    }

    @Throws(Exception::class)
    private fun checkMaxClauseLimitation(method: MultiTermQuery.RewriteMethod) {
        val savedMaxClauseCount = IndexSearcher.maxClauseCount
        IndexSearcher.maxClauseCount = 3

        val mtq = TermRangeQuery.newStringRange("data", "2", "7", true, true, method)
        try {
            val expected =
                expectThrows(IndexSearcher.TooManyClauses::class) {
                    multiSearcherDupls!!.rewrite(mtq)
                }
            //  Maybe remove this assert in later versions, when internal API changes:
            assertTrue(
                expected.stackTraceToString().contains("checkMaxClauseCount"),
                "Should throw BooleanQuery.TooManyClauses with a stacktrace containing checkMaxClauseCount()",
            )
        } finally {
            IndexSearcher.maxClauseCount = savedMaxClauseCount
        }
    }

    @Throws(Exception::class)
    private fun checkNoMaxClauseLimitation(method: MultiTermQuery.RewriteMethod) {
        val savedMaxClauseCount = IndexSearcher.maxClauseCount
        IndexSearcher.maxClauseCount = 3

        val mtq = TermRangeQuery.newStringRange("data", "2", "7", true, true, method)
        try {
            multiSearcherDupls!!.rewrite(mtq)
        } finally {
            IndexSearcher.maxClauseCount = savedMaxClauseCount
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMaxClauseLimitations() {
        checkMaxClauseLimitation(MultiTermQuery.SCORING_BOOLEAN_REWRITE)
        checkMaxClauseLimitation(MultiTermQuery.CONSTANT_SCORE_BOOLEAN_REWRITE)

        checkNoMaxClauseLimitation(MultiTermQuery.CONSTANT_SCORE_REWRITE)
        checkNoMaxClauseLimitation(MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE)
        checkNoMaxClauseLimitation(MultiTermQuery.TopTermsScoringBooleanQueryRewrite(1024))
        checkNoMaxClauseLimitation(MultiTermQuery.TopTermsBoostOnlyBooleanQueryRewrite(1024))
    }
}
