package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.tests.search.CheckHits
import kotlin.test.Test


/** subclass of TestSimpleExplanations that verifies non matches.  */
class TestComplexExplanationsOfNonMatches : TestComplexExplanations() {

    /**
     * Overrides superclass to ignore matches and focus on non-matches
     *
     * @see CheckHits.checkNoMatchExplanations
     */
    @Throws(Exception::class)
    override fun qtest(q: Query, expDocNrs: IntArray) {
        CheckHits.checkNoMatchExplanations(q, FIELD, searcher!!, expDocNrs)
    }

    // Tests inherited from TestComplexExplanations
    @Test
    @Throws(Exception::class)
    override fun testT3() {
        super.testT3()
    }

    @Test
    @Throws(Exception::class)
    override fun testMA3() {
        super.testMA3()
    }

    @Test
    @Throws(Exception::class)
    override fun testFQ5() {
        super.testFQ5()
    }

    @Test
    @Throws(Exception::class)
    override fun testCSQ4() {
        super.testCSQ4()
    }

    @Test
    @Throws(Exception::class)
    override fun testDMQ10() {
        super.testDMQ10()
    }

    @Test
    @Throws(Exception::class)
    override fun testMPQ7() {
        super.testMPQ7()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ12() {
        super.testBQ12()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ13() {
        super.testBQ13()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ18() {
        super.testBQ18()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ21() {
        super.testBQ21()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ22() {
        super.testBQ22()
    }
}
