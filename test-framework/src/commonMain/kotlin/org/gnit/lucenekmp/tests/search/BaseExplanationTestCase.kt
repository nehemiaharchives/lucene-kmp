package org.gnit.lucenekmp.tests.search

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Tests primitive queries (ie: that rewrite to themselves) to insure they match the expected set of
 * docs, and that the score of each match is equal to the value of the scores explanation.
 *
 * <p>The assumption is that if all of the "primitive" queries work well, then anything that
 * rewrites to a primitive will work well also.
 */
abstract class BaseExplanationTestCase : LuceneTestCase() {
    protected var searcher: IndexSearcher? = null
    protected var reader: IndexReader? = null
    protected var directory: Directory? = null
    protected var analyzer: Analyzer? = null

    companion object {
        const val KEY: String = "KEY"

        // boost on this field is the same as the iterator for the doc
        const val FIELD: String = "field"

        // same contents, but no field boost
        const val ALTFIELD: String = "alt"

        val docFields =
            arrayOf(
                "w1 w2 w3 w4 w5",
                "w1 w3 w2 w3 zz",
                "w1 xx w2 yy w3",
                "w1 w3 xx w2 yy w3 zz",
            )

        fun createDoc(index: Int): Document {
            val doc = Document()
            doc.add(newStringField(KEY, "$index", Field.Store.NO))
            doc.add(SortedDocValuesField(KEY, BytesRef("$index")))
            val f = newTextField(FIELD, docFields[index], Field.Store.NO)
            doc.add(f)
            doc.add(newTextField(ALTFIELD, docFields[index], Field.Store.NO))
            return doc
        }

        /** helper for generating MultiPhraseQueries */
        fun ta(s: Array<String>): Array<Term> {
            val t = Array(s.size) { i -> Term(FIELD, s[i]) }
            return t
        }
    }

    @BeforeTest
    @Throws(Exception::class)
    open fun beforeClassTestExplanations() {
        directory = newDirectory()
        analyzer = MockAnalyzer(random())
        RandomIndexWriter(
            random(),
            directory!!,
            newIndexWriterConfig(analyzer!!).setMergePolicy(newLogMergePolicy()),
        ).use { writer ->
            for (i in docFields.indices) {
                writer.addDocument(createDoc(i))
            }
            reader = writer.reader
            searcher = newSearcher(reader!!)
        }
    }

    @AfterTest
    @Throws(Exception::class)
    open fun afterClassTestExplanations() {
        searcher = null
        reader?.close()
        reader = null
        directory?.close()
        directory = null
        analyzer?.close()
        analyzer = null
    }

    /**
     * check the expDocNrs match and have scores that match the explanations. Query may be randomly
     * wrapped in a BooleanQuery with a term that matches no documents.
     */
    @Throws(Exception::class)
    open fun qtest(q: Query, expDocNrs: IntArray) {
        var q = q
        if (random().nextBoolean()) {
            val bq = BooleanQuery.Builder()
            bq.add(q, BooleanClause.Occur.SHOULD)
            bq.add(TermQuery(Term("NEVER", "MATCH")), BooleanClause.Occur.SHOULD)
            q = bq.build()
        }
        CheckHits.checkHitCollector(random(), q, FIELD, searcher!!, expDocNrs)
    }

    /**
     * Tests a query using qtest after wrapping it with both optB and reqB
     *
     * @see qtest
     * @see reqB
     * @see optB
     */
    @Throws(Exception::class)
    open fun bqtest(q: Query, expDocNrs: IntArray) {
        qtest(reqB(q), expDocNrs)
        qtest(optB(q), expDocNrs)
    }

    /** Convenience subclass of TermsQuery */
    protected fun matchTheseItems(terms: IntArray): Query {
        val query = BooleanQuery.Builder()
        for (term in terms) {
            query.add(BooleanClause(TermQuery(Term(KEY, "$term")), BooleanClause.Occur.SHOULD))
        }
        return query.build()
    }

    /**
     * MACRO: Wraps a Query in a BooleanQuery so that it is optional, along with a second prohibited
     * clause which will never match anything
     */
    @Throws(Exception::class)
    fun optB(q: Query): Query {
        val bq = BooleanQuery.Builder()
        bq.add(q, BooleanClause.Occur.SHOULD)
        bq.add(TermQuery(Term("NEVER", "MATCH")), BooleanClause.Occur.MUST_NOT)
        return bq.build()
    }

    /**
     * MACRO: Wraps a Query in a BooleanQuery so that it is required, along with a second optional
     * clause which will match everything
     */
    @Throws(Exception::class)
    fun reqB(q: Query): Query {
        val bq = BooleanQuery.Builder()
        bq.add(q, BooleanClause.Occur.MUST)
        bq.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.SHOULD)
        return bq.build()
    }
}
