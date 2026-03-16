package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.tests.search.CheckHits
import kotlin.test.Test

/** subclass of TestSimpleExplanations that verifies non matches. */
class TestSpanExplanationsOfNonMatches : TestSpanExplanations() {
    /**
     * Overrides superclass to ignore matches and focus on non-matches
     *
     * @see CheckHits.checkNoMatchExplanations
     */
    @Throws(Exception::class)
    override fun qtest(q: Query, expDocNrs: IntArray) {
        CheckHits.checkNoMatchExplanations(q, FIELD, searcher!!, expDocNrs)
    }

    // tests inherited from TestSpanExplanations
    @Test
    @Throws(Exception::class)
    override fun testST1() = super.testST1()

    @Test
    @Throws(Exception::class)
    override fun testST2() = super.testST2()

    @Test
    @Throws(Exception::class)
    override fun testST4() = super.testST4()

    @Test
    @Throws(Exception::class)
    override fun testST5() = super.testST5()

    @Test
    @Throws(Exception::class)
    override fun testSF1() = super.testSF1()

    @Test
    @Throws(Exception::class)
    override fun testSF2() = super.testSF2()

    @Test
    @Throws(Exception::class)
    override fun testSF4() = super.testSF4()

    @Test
    @Throws(Exception::class)
    override fun testSF5() = super.testSF5()

    @Test
    @Throws(Exception::class)
    override fun testSF6() = super.testSF6()

    @Test
    @Throws(Exception::class)
    override fun testSO1() = super.testSO1()

    @Test
    @Throws(Exception::class)
    override fun testSO2() = super.testSO2()

    @Test
    @Throws(Exception::class)
    override fun testSO3() = super.testSO3()

    @Test
    @Throws(Exception::class)
    override fun testSO4() = super.testSO4()

    @Test
    @Throws(Exception::class)
    override fun testSNear1() = super.testSNear1()

    @Test
    @Throws(Exception::class)
    override fun testSNear2() = super.testSNear2()

    @Test
    @Throws(Exception::class)
    override fun testSNear3() = super.testSNear3()

    @Test
    @Throws(Exception::class)
    override fun testSNear4() = super.testSNear4()

    @Test
    @Throws(Exception::class)
    override fun testSNear5() = super.testSNear5()

    @Test
    @Throws(Exception::class)
    override fun testSNear6() = super.testSNear6()

    @Test
    @Throws(Exception::class)
    override fun testSNear7() = super.testSNear7()

    @Test
    @Throws(Exception::class)
    override fun testSNear8() = super.testSNear8()

    @Test
    @Throws(Exception::class)
    override fun testSNear9() = super.testSNear9()

    @Test
    @Throws(Exception::class)
    override fun testSNear10() = super.testSNear10()

    @Test
    @Throws(Exception::class)
    override fun testSNear11() = super.testSNear11()

    @Test
    @Throws(Exception::class)
    override fun testSNot1() = super.testSNot1()

    @Test
    @Throws(Exception::class)
    override fun testSNot2() = super.testSNot2()

    @Test
    @Throws(Exception::class)
    override fun testSNot4() = super.testSNot4()

    @Test
    @Throws(Exception::class)
    override fun testSNot5() = super.testSNot5()

    @Test
    @Throws(Exception::class)
    override fun testSNot7() = super.testSNot7()

    @Test
    @Throws(Exception::class)
    override fun testSNot10() = super.testSNot10()

    @Test
    @Throws(Exception::class)
    override fun testExplainWithoutScoring() = super.testExplainWithoutScoring()

    @Test
    @Throws(Exception::class)
    override fun test1() = super.test1()

    @Test
    @Throws(Exception::class)
    override fun test2() = super.test2()
}
