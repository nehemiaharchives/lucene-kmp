package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TermRangeQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.CharacterRunAutomaton
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Simple base class for checking search equivalence. Extend it, and write tests that create [ ][.randomTerm]s (all terms are single characters a-z), and use [ ][.assertSameSet] and [assertSubsetOf].
 */
abstract class SearchEquivalenceTestBase : LuceneTestCase() {
    protected lateinit var s1: IndexSearcher
    protected lateinit var s2: IndexSearcher
    protected lateinit var directory: Directory
    protected lateinit var reader: IndexReader
    protected lateinit var analyzer: Analyzer
    protected lateinit var stopword: String

    @BeforeTest
    @Throws(Exception::class)
    fun beforeClass() {
        val random = random()
        directory = newDirectory()
        stopword = "${randomChar()}"
        val stopset = CharacterRunAutomaton(Automata.makeString(stopword))
        analyzer = MockAnalyzer(random, MockTokenizer.WHITESPACE, false, stopset)
        val iw = RandomIndexWriter(random, directory, analyzer)
        val doc = Document()
        val id: Field = StringField("id", "", Field.Store.NO)
        val field: Field = TextField("field", "", Field.Store.NO)
        doc.add(id)
        doc.add(field)

        // index some docs
        val numDocs = if (TEST_NIGHTLY) atLeast(1000) else atLeast(100)
        for (i in 0..<numDocs) {
            id.setStringValue(i.toString())
            field.setStringValue(randomFieldContents())
            iw.addDocument(doc)
        }

        // delete some docs
        val numDeletes = numDocs / 20
        repeat(numDeletes) {
            val toDelete = Term("id", random.nextInt(numDocs).toString())
            if (random.nextBoolean()) {
                iw.deleteDocuments(toDelete)
            } else {
                iw.deleteDocuments(TermQuery(toDelete))
            }
        }

        reader = iw.getReader(applyDeletions = true, writeAllDeletes = true)
        s1 = newSearcher(reader)
        // Disable the query cache, which converts two-phase iterators to normal iterators, while we
        // want to make sure two-phase iterators are exercised.
        s1.queryCache = null
        s2 = newSearcher(reader)
        s2.queryCache = null
        iw.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun afterClass() {
        reader.close()
        directory.close()
        analyzer.close()
    }

    /**
     * populate a field with random contents. terms should be single characters in lowercase (a-z)
     * tokenization can be assumed to be on whitespace.
     */
    protected fun randomFieldContents(): String {
        val sb = StringBuilder()
        val numTerms = random().nextInt(15)
        repeat(numTerms) {
            if (sb.isNotEmpty()) {
                sb.append(' ') // whitespace
            }
            sb.append(randomChar())
        }
        return sb.toString()
    }

    /** returns a term suitable for searching. terms are single characters in lowercase (a-z) */
    @Suppress("unused")
    protected fun randomTerm(): Term {
        return Term("field", "${randomChar()}")
    }

    /** Returns a random filter over the document set */
    protected fun randomFilter(): Query {
        return if (random().nextBoolean()) {
            TermRangeQuery.newStringRange("field", "a", "${randomChar()}", includeLower = true, includeUpper = true)
        } else {
            // use a query with a two-phase approximation
            PhraseQuery(100, "field", "${randomChar()}", "${randomChar()}")
        }
    }

    /**
     * Asserts that the documents returned by `q1` are the same as of those returned by
     * `q2`
     */
    @Throws(Exception::class)
    fun assertSameSet(q1: Query, q2: Query) {
        assertSubsetOf(q1, q2)
        assertSubsetOf(q2, q1)
    }

    /**
     * Asserts that the documents returned by `q1` are a subset of those returned by `q2`
     */
    @Throws(Exception::class)
    fun assertSubsetOf(q1: Query, q2: Query) {
        // test without a filter
        assertSubsetOf(q1, q2, null)

        // test with some filters (this will sometimes cause advance'ing enough to test it)
        val numFilters = if (TEST_NIGHTLY) atLeast(10) else atLeast(3)
        repeat(numFilters) {
            val filter = randomFilter()
            // incorporate the filter in different ways.
            assertSubsetOf(q1, q2, filter)
            assertSubsetOf(filteredQuery(q1, filter), filteredQuery(q2, filter), null)
        }
    }

    /**
     * Asserts that the documents returned by `q1` are a subset of those returned by `q2`.
     *
     * Both queries will be filtered by `filter`
     */
    @Throws(Exception::class)
    protected fun assertSubsetOf(q1: Query, q2: Query, filter: Query?) {
        QueryUtils.check(q1)
        QueryUtils.check(q2)

        var q1 = q1
        var q2 = q2
        if (filter != null) {
            q1 = BooleanQuery.Builder().add(q1, BooleanClause.Occur.MUST).add(filter, BooleanClause.Occur.FILTER).build()
            q2 = BooleanQuery.Builder().add(q2, BooleanClause.Occur.MUST).add(filter, BooleanClause.Occur.FILTER).build()
        }
        // we test both INDEXORDER and RELEVANCE because we want to test needsScores=true/false
        for (sort in arrayOf(Sort.INDEXORDER, Sort.RELEVANCE)) {
            // not efficient, but simple!
            val td1: TopDocs = s1.search(q1, reader.maxDoc(), sort)
            val td2: TopDocs = s2.search(q2, reader.maxDoc(), sort)
            assertTrue(td1.totalHits.value <= td2.totalHits.value, "too many hits: ${td1.totalHits.value} > ${td2.totalHits.value}")

            // fill the superset into a bitset
            val bitset = BitSet()
            for (i in td2.scoreDocs.indices) {
                bitset.set(td2.scoreDocs[i].doc)
            }

            // check in the subset, that every bit was set by the super
            for (i in td1.scoreDocs.indices) {
                assertTrue(bitset[td1.scoreDocs[i].doc])
            }
        }
    }

    /** Assert that two queries return the same documents and with the same scores. */
    @Suppress("unused")
    @Throws(Exception::class)
    protected fun assertSameScores(q1: Query, q2: Query) {
        assertSameSet(q1, q2)

        assertSameScores(q1, q2, null)
        // also test with some filters to test advancing
        val numFilters = if (TEST_NIGHTLY) atLeast(10) else atLeast(3)
        repeat(numFilters) {
            val filter = randomFilter()
            // incorporate the filter in different ways.
            assertSameScores(q1, q2, filter)
            assertSameScores(filteredQuery(q1, filter), filteredQuery(q2, filter), null)
        }
    }

    @Throws(IOException::class)
    protected fun assertSameScores(q1: Query, q2: Query, filter: Query?) {
        // not efficient, but simple!
        var q1 = q1
        var q2 = q2
        if (filter != null) {
            q1 = BooleanQuery.Builder().add(q1, BooleanClause.Occur.MUST).add(filter, BooleanClause.Occur.FILTER).build()
            q2 = BooleanQuery.Builder().add(q2, BooleanClause.Occur.MUST).add(filter, BooleanClause.Occur.FILTER).build()
        }
        val td1 = s1.search(q1, reader.maxDoc())
        val td2 = s2.search(q2, reader.maxDoc())
        assertEquals(td1.totalHits.value, td2.totalHits.value)
        for (i in td1.scoreDocs.indices) {
            assertEquals(td1.scoreDocs[i].doc, td2.scoreDocs[i].doc)
            assertEquals(td1.scoreDocs[i].score, td2.scoreDocs[i].score, 10e-5f)
        }
    }

    protected fun filteredQuery(query: Query, filter: Query): Query {
        return BooleanQuery.Builder().add(query, BooleanClause.Occur.MUST).add(filter, BooleanClause.Occur.FILTER).build()
    }

    companion object {
        /** returns random character (a-z) */
        fun randomChar(): Char {
            var c = TestUtil.nextInt(random(), 'a'.code, 'z'.code).toChar()
            if (random().nextBoolean()) {
                // bias towards earlier chars, so that chars have a ~ zipfian distribution with earlier chars
                // having a higher frequency
                c = TestUtil.nextInt(random(), 'a'.code, c.code).toChar()
            }
            return c
        }
    }
}
