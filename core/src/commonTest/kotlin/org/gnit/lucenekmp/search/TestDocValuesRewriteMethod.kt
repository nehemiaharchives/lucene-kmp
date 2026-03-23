package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Tests the DocValuesRewriteMethod */
class TestDocValuesRewriteMethod : LuceneTestCase() {
    protected lateinit var searcher: IndexSearcher
    private lateinit var reader: IndexReader
    private lateinit var dir: Directory
    protected lateinit var fieldName: String

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        dir = newDirectory()
        fieldName = if (random().nextBoolean()) "field" else "" // sometimes use an empty string as field name
        val writer =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.KEYWORD, false))
                    .setMaxBufferedDocs(TestUtil.nextInt(random(), 50, 1000))
            )
        val terms = ArrayList<String>()
        val num = atLeast(200)
        for (i in 0..<num) {
            val doc = Document()
            doc.add(newStringField("id", "$i", Field.Store.NO))
            val numTerms = random().nextInt(4)
            for (j in 0..<numTerms) {
                val s = TestUtil.randomUnicodeString(random())
                doc.add(newStringField(fieldName, s, Field.Store.NO))
                doc.add(SortedSetDocValuesField(fieldName, BytesRef(s)))
                doc.add(SortedSetDocValuesField.indexedField("${fieldName}_with-skip", BytesRef(s)))
                terms.add(s)
            }
            writer.addDocument(doc)
        }

        if (VERBOSE) {
            // utf16 order
            terms.sort()
            println("UTF16 order:")
            for (s in terms) {
                println("  ${UnicodeUtil.toHexString(s)} $s")
            }
        }

        val numDeletions = random().nextInt(num / 10)
        for (i in 0..<numDeletions) {
            writer.deleteDocuments(Term("id", "${random().nextInt(num)}"))
        }

        reader = writer.reader
        searcher = newSearcher(reader)
        writer.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        dir.close()
    }

    /** test a bunch of random regular expressions */
    @Test
    @Throws(Exception::class)
    fun testRegexps() {
        val num = atLeast(1000)
        for (i in 0..<num) {
            val reg = AutomatonTestUtil.randomRegexp(random())
            if (VERBOSE) {
                println("TEST: regexp=$reg")
            }
            assertSame(reg)
        }
    }

    /** check that the # of hits is the same as if the query is run against the inverted index */
    @Throws(IOException::class)
    protected fun assertSame(regexp: String) {
        val docValues =
            RegexpQuery(
                Term(fieldName, regexp),
                RegExp.NONE,
                0,
                RegexpQuery.DEFAULT_PROVIDER,
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                DocValuesRewriteMethod()
            )
        val docValuesWithSkip =
            RegexpQuery(
                Term("${fieldName}_with-skip", regexp),
                RegExp.NONE,
                0,
                RegexpQuery.DEFAULT_PROVIDER,
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                DocValuesRewriteMethod()
            )
        val inverted = RegexpQuery(Term(fieldName, regexp), RegExp.NONE)

        val invertedDocs = searcher.search(inverted, 25)
        val docValuesDocs = searcher.search(docValues, 25)
        val docValuesWithSkipDocs = searcher.search(docValuesWithSkip, 25)

        CheckHits.checkEqual(inverted, invertedDocs.scoreDocs, docValuesDocs.scoreDocs)
        CheckHits.checkEqual(inverted, invertedDocs.scoreDocs, docValuesWithSkipDocs.scoreDocs)
    }

    @Test
    @Throws(Exception::class)
    fun testEquals() {
        run {
            val a1 = RegexpQuery(Term(fieldName, "[aA]"), RegExp.NONE)
            val a2 = RegexpQuery(Term(fieldName, "[aA]"), RegExp.NONE)
            val b = RegexpQuery(Term(fieldName, "[bB]"), RegExp.NONE)
            assertEquals(a1, a2)
            assertFalse(a1 == b)
        }

        run {
            val a1 =
                RegexpQuery(
                    Term(fieldName, "[aA]"),
                    RegExp.NONE,
                    0,
                    RegexpQuery.DEFAULT_PROVIDER,
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                    DocValuesRewriteMethod()
                )
            val a2 =
                RegexpQuery(
                    Term(fieldName, "[aA]"),
                    RegExp.NONE,
                    0,
                    RegexpQuery.DEFAULT_PROVIDER,
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                    DocValuesRewriteMethod()
                )
            val b =
                RegexpQuery(
                    Term(fieldName, "[bB]"),
                    RegExp.NONE,
                    0,
                    RegexpQuery.DEFAULT_PROVIDER,
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                    DocValuesRewriteMethod()
                )
            assertEquals(a1, a2)
            assertFalse(a1 == b)
            QueryUtils.check(a1)
        }
    }
}
