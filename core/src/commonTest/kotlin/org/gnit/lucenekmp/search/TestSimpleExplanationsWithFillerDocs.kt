package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * subclass of TestSimpleExplanations that adds a lot of filler docs which will be ignored at query
 * time. These filler docs will either all be empty in which case the queries will be unmodified, or
 * they will all use terms from same set of source data as our regular docs (to emphasis the DocFreq
 * factor in scoring), in which case the queries will be wrapped so they can be excluded.
 */
class TestSimpleExplanationsWithFillerDocs : TestSimpleExplanations() {
    /** num of empty docs injected between every doc in the index */
    private val NUM_FILLER_DOCS = if (TEST_NIGHTLY) BooleanScorer.SIZE else 4

    /** num of empty docs injected prior to the first doc in the (main) index */
    private var PRE_FILLER_DOCS = 0

    /**
     * If non-null then the filler docs are not empty, and need to be filtered out from queries using
     * this as both field name & field value
     */
    private var EXTRA: String? = null

    private val EMPTY_DOC = Document()

    /**
     * Replaces the index created by our superclass with a new one that includes a lot of docs filler
     * docs. [qtest] will account for these extra filler docs.
     *
     * @see qtest
     */
    @BeforeTest
    @Throws(Exception::class)
    override fun beforeClassTestExplanations() {
        super.beforeClassTestExplanations()
        EXTRA = if (random().nextBoolean()) null else "extra"
        PRE_FILLER_DOCS = TestUtil.nextInt(random(), 0, (NUM_FILLER_DOCS / 2))

        // free up what our super class created that we won't be using
        reader!!.close()
        directory!!.close()

        directory = newDirectory()
        RandomIndexWriter(
            random(),
            directory!!,
            newIndexWriterConfig(analyzer!!).setMergePolicy(newLogMergePolicy()),
        ).use { writer ->
            for (filler in 0..<PRE_FILLER_DOCS) {
                writer.addDocument(makeFillerDoc())
            }
            for (i in docFields.indices) {
                writer.addDocument(createDoc(i))

                for (filler in 0..<NUM_FILLER_DOCS) {
                    writer.addDocument(makeFillerDoc())
                }
            }
            reader = writer.reader
            searcher = newSearcher(reader!!)
        }
    }

    private fun makeFillerDoc(): Document {
        if (null == EXTRA) {
            return EMPTY_DOC
        }
        val doc = createDoc(TestUtil.nextInt(random(), 0, docFields.size - 1))
        doc.add(newStringField(EXTRA!!, EXTRA!!, Field.Store.NO))
        return doc
    }

    /**
     * Adjusts `expDocNrs` based on the filler docs injected in the index, and if
     * neccessary wraps the `q` in a BooleanQuery that will filter out all filler docs
     * using the [EXTRA] field.
     *
     * @see replaceIndex
     */
    @Throws(Exception::class)
    override fun qtest(q: Query, expDocNrs: IntArray) {
        var q = q
        val adjustedExpDocNrs = ArrayUtil.copyArray(expDocNrs)
        for (i in adjustedExpDocNrs.indices) {
            adjustedExpDocNrs[i] = PRE_FILLER_DOCS + ((NUM_FILLER_DOCS + 1) * adjustedExpDocNrs[i])
        }

        if (null != EXTRA) {
            val builder = BooleanQuery.Builder()
            builder.add(BooleanClause(q, BooleanClause.Occur.MUST))
            builder.add(
                BooleanClause(
                    TermQuery(Term(EXTRA!!, EXTRA!!)),
                    BooleanClause.Occur.MUST_NOT,
                ),
            )
            q = builder.build()
        }
        super.qtest(q, adjustedExpDocNrs)
    }

    @Test
    @Throws(Exception::class)
    override fun testMA1() {
        if (EXTRA == null) {
            return
        }
        super.testMA1()
    }

    @Test
    @Throws(Exception::class)
    override fun testMA2() {
        if (EXTRA == null) {
            return
        }
        super.testMA2()
    }

    // tests inherited from TestSimpleExplanations
    @Test
    @Throws(Exception::class)
    override fun testT1() = super.testT1()

    @Test
    @Throws(Exception::class)
    override fun testT2() = super.testT2()

    @Test
    @Throws(Exception::class)
    override fun testP1() = super.testP1()

    @Test
    @Throws(Exception::class)
    override fun testP2() = super.testP2()

    @Test
    @Throws(Exception::class)
    override fun testP3() = super.testP3()

    @Test
    @Throws(Exception::class)
    override fun testP4() = super.testP4()

    @Test
    @Throws(Exception::class)
    override fun testP5() = super.testP5()

    @Test
    @Throws(Exception::class)
    override fun testP6() = super.testP6()

    @Test
    @Throws(Exception::class)
    override fun testP7() = super.testP7()

    @Test
    @Throws(Exception::class)
    override fun testCSQ1() = super.testCSQ1()

    @Test
    @Throws(Exception::class)
    override fun testCSQ2() = super.testCSQ2()

    @Test
    @Throws(Exception::class)
    override fun testCSQ3() = super.testCSQ3()

    @Test
    @Throws(Exception::class)
    override fun testDMQ1() = super.testDMQ1()

    @Test
    @Throws(Exception::class)
    override fun testDMQ2() = super.testDMQ2()

    @Test
    @Throws(Exception::class)
    override fun testDMQ3() = super.testDMQ3()

    @Test
    @Throws(Exception::class)
    override fun testDMQ4() = super.testDMQ4()

    @Test
    @Throws(Exception::class)
    override fun testDMQ5() = super.testDMQ5()

    @Test
    @Throws(Exception::class)
    override fun testDMQ6() = super.testDMQ6()

    @Test
    @Throws(Exception::class)
    override fun testDMQ7() = super.testDMQ7()

    @Test
    @Throws(Exception::class)
    override fun testDMQ8() = super.testDMQ8()

    @Test
    @Throws(Exception::class)
    override fun testDMQ9() = super.testDMQ9()

    @Test
    @Throws(Exception::class)
    override fun testMPQ1() = super.testMPQ1()

    @Test
    @Throws(Exception::class)
    override fun testMPQ2() = super.testMPQ2()

    @Test
    @Throws(Exception::class)
    override fun testMPQ3() = super.testMPQ3()

    @Test
    @Throws(Exception::class)
    override fun testMPQ4() = super.testMPQ4()

    @Test
    @Throws(Exception::class)
    override fun testMPQ5() = super.testMPQ5()

    @Test
    @Throws(Exception::class)
    override fun testMPQ6() = super.testMPQ6()

    @Test
    @Throws(Exception::class)
    override fun testBQ1() = super.testBQ1()

    @Test
    @Throws(Exception::class)
    override fun testBQ2() = super.testBQ2()

    @Test
    @Throws(Exception::class)
    override fun testBQ3() = super.testBQ3()

    @Test
    @Throws(Exception::class)
    override fun testBQ4() = super.testBQ4()

    @Test
    @Throws(Exception::class)
    override fun testBQ5() = super.testBQ5()

    @Test
    @Throws(Exception::class)
    override fun testBQ6() = super.testBQ6()

    @Test
    @Throws(Exception::class)
    override fun testBQ7() = super.testBQ7()

    @Test
    @Throws(Exception::class)
    override fun testBQ8() = super.testBQ8()

    @Test
    @Throws(Exception::class)
    override fun testBQ9() = super.testBQ9()

    @Test
    @Throws(Exception::class)
    override fun testBQ10() = super.testBQ10()

    @Test
    @Throws(Exception::class)
    override fun testBQ11() = super.testBQ11()

    @Test
    @Throws(Exception::class)
    override fun testBQ14() = super.testBQ14()

    @Test
    @Throws(Exception::class)
    override fun testBQ15() = super.testBQ15()

    @Test
    @Throws(Exception::class)
    override fun testBQ16() = super.testBQ16()

    @Test
    @Throws(Exception::class)
    override fun testBQ17() = super.testBQ17()

    @Test
    @Throws(Exception::class)
    override fun testBQ19() = super.testBQ19()

    @Test
    @Throws(Exception::class)
    override fun testBQ20() = super.testBQ20()

    @Test
    @Throws(Exception::class)
    override fun testBQ21() = super.testBQ21()

    @Test
    @Throws(Exception::class)
    override fun testBQ23() = super.testBQ23()

    @Test
    @Throws(Exception::class)
    override fun testBQ24() = super.testBQ24()

    @Test
    @Throws(Exception::class)
    override fun testBQ25() = super.testBQ25()

    @Test
    @Throws(Exception::class)
    override fun testBQ26() = super.testBQ26()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ1() = super.testMultiFieldBQ1()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ2() = super.testMultiFieldBQ2()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ3() = super.testMultiFieldBQ3()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ4() = super.testMultiFieldBQ4()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ5() = super.testMultiFieldBQ5()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ6() = super.testMultiFieldBQ6()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ7() = super.testMultiFieldBQ7()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ8() = super.testMultiFieldBQ8()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ9() = super.testMultiFieldBQ9()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ10() = super.testMultiFieldBQ10()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ1() = super.testMultiFieldBQofPQ1()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ2() = super.testMultiFieldBQofPQ2()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ3() = super.testMultiFieldBQofPQ3()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ4() = super.testMultiFieldBQofPQ4()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ5() = super.testMultiFieldBQofPQ5()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ6() = super.testMultiFieldBQofPQ6()

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ7() = super.testMultiFieldBQofPQ7()

    @Test
    @Throws(Exception::class)
    override fun testSynonymQuery() = super.testSynonymQuery()

    @Test
    override fun testEquality() = super.testEquality()
}
