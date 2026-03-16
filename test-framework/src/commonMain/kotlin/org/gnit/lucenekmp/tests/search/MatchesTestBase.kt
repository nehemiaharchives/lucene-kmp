package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.ReaderUtil
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchesIterator
import org.gnit.lucenekmp.search.NamedMatches
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Base class for tests checking the [Weight.matches] implementations
 */
abstract class MatchesTestBase : LuceneTestCase() {
    /**
     * @return an array of documents to be indexed
     */
    protected abstract fun getDocuments(): Array<String>

    protected var searcher: IndexSearcher? = null
    protected var directory: Directory? = null
    protected var reader: IndexReader? = null

    companion object {
        const val FIELD_WITH_OFFSETS = "field_offsets"
        const val FIELD_NO_OFFSETS = "field_no_offsets"
        const val FIELD_DOCS_ONLY = "field_docs_only"
        const val FIELD_FREQS = "field_freqs"
        const val FIELD_POINT = "field_point"

        private val OFFSETS = FieldType(TextField.TYPE_STORED)
        private val DOCS = FieldType(TextField.TYPE_STORED)
        private val DOCS_AND_FREQS = FieldType(TextField.TYPE_STORED)

        init {
            OFFSETS.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
            DOCS.setIndexOptions(IndexOptions.DOCS)
            DOCS_AND_FREQS.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        }
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDownMatchesTestBase() {
        reader!!.close()
        directory!!.close()
    }

    @BeforeTest
    @Throws(Exception::class)
    fun setUpMatchesTestBase() {
        directory = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                directory!!,
                newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()),
            )
        val docFields = getDocuments()
        for (i in docFields.indices) {
            val doc = Document()
            doc.add(newField(FIELD_WITH_OFFSETS, docFields[i], OFFSETS))
            doc.add(newField(FIELD_NO_OFFSETS, docFields[i], TextField.TYPE_STORED))
            doc.add(newField(FIELD_DOCS_ONLY, docFields[i], DOCS))
            doc.add(newField(FIELD_FREQS, docFields[i], DOCS_AND_FREQS))
            doc.add(IntPoint(FIELD_POINT, 10))
            doc.add(NumericDocValuesField(FIELD_POINT, 10L))
            doc.add(NumericDocValuesField("id", i.toLong()))
            doc.add(newField("id", i.toString(), TextField.TYPE_STORED))
            writer.addDocument(doc)
        }
        writer.forceMerge(1)
        reader = writer.reader
        writer.close()
        searcher = newSearcher(getOnlyLeafReader(reader!!))
    }

    /**
     * For a given query and field, check that expected matches are retrieved
     *
     * @param q the query
     * @param field the field to pull matches from
     * @param expected an array of arrays of ints; for each entry, the first int is the expected
     *     docid, followed by pairs of start and end positions
     */
    @Throws(IOException::class)
    protected fun checkMatches(q: Query, field: String, expected: Array<IntArray>) {
        val w = searcher!!.createWeight(searcher!!.rewrite(q), ScoreMode.COMPLETE, 1f)
        for (i in expected.indices) {
            val leafContexts = searcher!!.indexReader.leaves()
            val ctx = leafContexts[ReaderUtil.subIndex(expected[i][0], leafContexts)]
            val doc = expected[i][0] - ctx.docBase
            val matches = w.matches(ctx, doc)
            if (matches == null) {
                assertEquals(1, expected[i].size)
                continue
            }
            val it = matches.getMatches(field)
            if (expected[i].size == 1) {
                assertNull(it)
                continue
            }
            checkFieldMatches(it!!, expected[i])
            checkFieldMatches(matches.getMatches(field)!!, expected[i])
        }
    }

    /**
     * For a given query and field, check that the expected numbers of query labels were found
     *
     * @param q the query
     * @param field the field to pull matches from
     * @param expected an array of expected label counts, one entry per document
     */
    @Throws(IOException::class)
    protected fun checkLabelCount(q: Query, field: String, expected: IntArray) {
        val w = searcher!!.createWeight(searcher!!.rewrite(q), ScoreMode.COMPLETE, 1f)
        for (i in expected.indices) {
            val leafContexts = searcher!!.indexReader.leaves()
            val ctx = leafContexts[ReaderUtil.subIndex(i, leafContexts)]
            val doc = i - ctx.docBase
            val matches = w.matches(ctx, doc)
            if (matches == null) {
                assertEquals(0, expected[i], "Expected to get matches on document $i")
                continue
            }
            val it = matches.getMatches(field)
            if (expected[i] == 0) {
                assertNull(it)
                continue
            } else {
                assertNotNull(it)
            }
            val labels = mutableSetOf<Query>()
            while (it!!.next()) {
                labels.add(it.query)
            }
            assertEquals(expected[i], labels.size)
        }
    }

    /**
     * Given a MatchesIterator, check that it has the expected set of start and end positions
     *
     * @param it an iterator
     * @param expected an array of expected start and end pairs and start and end offsets; the entry
     *     at position 0 is ignored
     */
    @Throws(IOException::class)
    protected fun checkFieldMatches(it: MatchesIterator, expected: IntArray) {
        var pos = 1
        while (it.next()) {
            assertEquals(expected[pos], it.startPosition(), "Wrong start position")
            assertEquals(expected[pos + 1], it.endPosition(), "Wrong end position")
            assertEquals(expected[pos + 2], it.startOffset(), "Wrong start offset")
            assertEquals(expected[pos + 3], it.endOffset(), "Wrong end offset")
            pos += 4
        }
        assertEquals(expected.size, pos)
    }

    /**
     * Given a query, check that matches contain the expected NamedQuery wrapper names
     *
     * @param q the query
     * @param expectedNames an array of arrays of Strings; for each document, an array of expected
     *     query names that match
     */
    @Throws(IOException::class)
    protected fun checkSubMatches(q: Query, expectedNames: Array<Array<String>>) {
        val w = searcher!!.createWeight(searcher!!.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f)
        for (i in expectedNames.indices) {
            val leafContexts = searcher!!.indexReader.leaves()
            val ctx = leafContexts[ReaderUtil.subIndex(i, leafContexts)]
            val doc = i - ctx.docBase
            val matches = w.matches(ctx, doc)
            if (matches == null) {
                assertEquals(0, expectedNames[i].size, "Expected to get no matches on document $i")
                continue
            }
            val expectedQueries = expectedNames[i].toMutableSet()
            val actualQueries = NamedMatches.findNamedMatches(matches).map { it.name }.toSet()

            val unexpected = actualQueries - expectedQueries
            assertEquals(0, unexpected.size, "Unexpected matching leaf queries: $unexpected")
            val missing = expectedQueries - actualQueries
            assertEquals(0, missing.size, "Missing matching leaf queries: $missing")
        }
    }

    /**
     * Assert that query matches from a field are all leaf matches and do not contain sub matches
     *
     * @param q the query
     * @param field the field
     */
    @Throws(IOException::class)
    protected fun assertIsLeafMatch(q: Query, field: String) {
        val w = searcher!!.createWeight(searcher!!.rewrite(q), ScoreMode.COMPLETE, 1f)
        for (i in 0..<searcher!!.indexReader.maxDoc()) {
            val leafContexts = searcher!!.indexReader.leaves()
            val ctx = leafContexts[ReaderUtil.subIndex(i, leafContexts)]
            val doc = i - ctx.docBase
            val matches = w.matches(ctx, doc) ?: return
            val mi = matches.getMatches(field) ?: return
            while (mi.next()) {
                assertNull(mi.subMatches)
            }
        }
    }

    /**
     * For a query and field, check that each document's submatches conform to an expected TermMatch
     *
     * @param q the query
     * @param field the field to pull matches for
     * @param expected an array per doc of arrays per match of an array of expected submatches
     */
    @Throws(IOException::class)
    protected fun checkTermMatches(q: Query, field: String, expected: Array<Array<Array<TermMatch>>>) {
        val w = searcher!!.createWeight(searcher!!.rewrite(q), ScoreMode.COMPLETE, 1f)
        for (i in expected.indices) {
            val leafContexts = searcher!!.indexReader.leaves()
            val ctx = leafContexts[ReaderUtil.subIndex(i, leafContexts)]
            val doc = i - ctx.docBase
            val matches = w.matches(ctx, doc)
            if (matches == null) {
                assertEquals(0, expected[i].size)
                continue
            }
            val it = matches.getMatches(field)
            if (expected[i].isEmpty()) {
                assertNull(it)
                continue
            }
            checkTerms(expected[i], it!!)
        }
    }

    @Throws(IOException::class)
    private fun checkTerms(expected: Array<Array<TermMatch>>, it: MatchesIterator) {
        var upTo = 0
        while (it.next()) {
            val expectedMatches = expected[upTo].toMutableSet()
            val submatches = it.subMatches!!
            while (submatches.next()) {
                val tm = TermMatch(submatches.startPosition(), submatches.startOffset(), submatches.endOffset())
                if (!expectedMatches.remove(tm)) {
                    fail("Unexpected term match: $tm")
                }
            }
            if (expectedMatches.isNotEmpty()) {
                fail("Missing term matches: ${expectedMatches.joinToString(", ")}")
            }
            upTo++
        }
        if (upTo < expected.size - 1) {
            fail("Missing expected match")
        }
    }

    /** Encapsulates a term position, start and end offset */
    class TermMatch(
        /** The position */
        val position: Int,
        /** The start offset */
        val startOffset: Int,
        /** The end offset */
        val endOffset: Int,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as TermMatch
            return position == other.position && startOffset == other.startOffset && endOffset == other.endOffset
        }

        override fun hashCode(): Int {
            var result = position
            result = 31 * result + startOffset
            result = 31 * result + endOffset
            return result
        }

        override fun toString(): String {
            return "$position[$startOffset->$endOffset]"
        }
    }
}
