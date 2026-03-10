package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.tests.search.CheckHits
import kotlin.test.Test

/** subclass of TestSimpleExplanations that verifies non matches. */
class TestSimpleExplanationsOfNonMatches : TestSimpleExplanations() {

    /**
     * Overrides superclass to ignore matches and focus on non-matches
     *
     * @see CheckHits.checkNoMatchExplanations
     */
    @Throws(Exception::class)
    override fun qtest(q: Query, expDocNrs: IntArray) {
        CheckHits.checkNoMatchExplanations(q, FIELD, searcher!!, expDocNrs)
    }

    // tests inherited from TestSimpleExplanations
    @Test
    @Throws(Exception::class)
    override fun testT1() {
        super.testT1()
    }

    @Test
    @Throws(Exception::class)
    override fun testT2() {
        super.testT2()
    }

    @Test
    @Throws(Exception::class)
    override fun testMA1() {
        super.testMA1()
    }

    @Test
    @Throws(Exception::class)
    override fun testMA2() {
        super.testMA2()
    }

    @Test
    @Throws(Exception::class)
    override fun testP1() {
        super.testP1()
    }

    @Test
    @Throws(Exception::class)
    override fun testP2() {
        super.testP2()
    }

    @Test
    @Throws(Exception::class)
    override fun testP3() {
        super.testP3()
    }

    @Test
    @Throws(Exception::class)
    override fun testP4() {
        super.testP4()
    }

    @Test
    @Throws(Exception::class)
    override fun testP5() {
        super.testP5()
    }

    @Test
    @Throws(Exception::class)
    override fun testP6() {
        super.testP6()
    }

    @Test
    @Throws(Exception::class)
    override fun testP7() {
        super.testP7()
    }

    @Test
    @Throws(Exception::class)
    override fun testCSQ1() {
        super.testCSQ1()
    }

    @Test
    @Throws(Exception::class)
    override fun testCSQ2() {
        super.testCSQ2()
    }

    @Test
    @Throws(Exception::class)
    override fun testCSQ3() {
        super.testCSQ3()
    }

    @Test
    @Throws(Exception::class)
    override fun testDMQ1() {
        super.testDMQ1()
    }

    @Test
    @Throws(Exception::class)
    override fun testDMQ2() {
        super.testDMQ2()
    }

    @Test
    @Throws(Exception::class)
    override fun testDMQ3() {
        super.testDMQ3()
    }

    @Test
    @Throws(Exception::class)
    override fun testDMQ4() {
        super.testDMQ4()
    }

    @Test
    @Throws(Exception::class)
    override fun testDMQ5() {
        super.testDMQ5()
    }

    @Test
    @Throws(Exception::class)
    override fun testDMQ6() {
        super.testDMQ6()
    }

    @Test
    @Throws(Exception::class)
    override fun testDMQ7() {
        super.testDMQ7()
    }

    @Test
    @Throws(Exception::class)
    override fun testDMQ8() {
        super.testDMQ8()
    }

    @Test
    @Throws(Exception::class)
    override fun testDMQ9() {
        super.testDMQ9()
    }

    @Test
    @Throws(Exception::class)
    override fun testMPQ1() {
        super.testMPQ1()
    }

    @Test
    @Throws(Exception::class)
    override fun testMPQ2() {
        super.testMPQ2()
    }

    @Test
    @Throws(Exception::class)
    override fun testMPQ3() {
        super.testMPQ3()
    }

    @Test
    @Throws(Exception::class)
    override fun testMPQ4() {
        super.testMPQ4()
    }

    @Test
    @Throws(Exception::class)
    override fun testMPQ5() {
        super.testMPQ5()
    }

    @Test
    @Throws(Exception::class)
    override fun testMPQ6() {
        super.testMPQ6()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ1() {
        super.testBQ1()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ2() {
        super.testBQ2()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ3() {
        super.testBQ3()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ4() {
        super.testBQ4()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ5() {
        super.testBQ5()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ6() {
        super.testBQ6()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ7() {
        super.testBQ7()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ8() {
        super.testBQ8()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ9() {
        super.testBQ9()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ10() {
        super.testBQ10()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ11() {
        super.testBQ11()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ14() {
        super.testBQ14()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ15() {
        super.testBQ15()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ16() {
        super.testBQ16()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ17() {
        super.testBQ17()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ19() {
        super.testBQ19()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ20() {
        super.testBQ20()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ21() {
        super.testBQ21()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ23() {
        super.testBQ23()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ24() {
        super.testBQ24()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ25() {
        super.testBQ25()
    }

    @Test
    @Throws(Exception::class)
    override fun testBQ26() {
        super.testBQ26()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ1() {
        super.testMultiFieldBQ1()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ2() {
        super.testMultiFieldBQ2()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ3() {
        super.testMultiFieldBQ3()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ4() {
        super.testMultiFieldBQ4()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ5() {
        super.testMultiFieldBQ5()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ6() {
        super.testMultiFieldBQ6()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ7() {
        super.testMultiFieldBQ7()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ8() {
        super.testMultiFieldBQ8()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ9() {
        super.testMultiFieldBQ9()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQ10() {
        super.testMultiFieldBQ10()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ1() {
        super.testMultiFieldBQofPQ1()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ2() {
        super.testMultiFieldBQofPQ2()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ3() {
        super.testMultiFieldBQofPQ3()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ4() {
        super.testMultiFieldBQofPQ4()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ5() {
        super.testMultiFieldBQofPQ5()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ6() {
        super.testMultiFieldBQofPQ6()
    }

    @Test
    @Throws(Exception::class)
    override fun testMultiFieldBQofPQ7() {
        super.testMultiFieldBQofPQ7()
    }

    @Test
    @Throws(Exception::class)
    override fun testSynonymQuery() {
        super.testSynonymQuery()
    }

    @Test
    override fun testEquality() {
        super.testEquality()
    }

}
