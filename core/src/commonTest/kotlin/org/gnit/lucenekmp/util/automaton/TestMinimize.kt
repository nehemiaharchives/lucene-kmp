package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Port of Lucene's TestMinimize from commit ec75fca.
 */
class TestMinimize : LuceneTestCase() {

    /** the minimal and non-minimal are compared to ensure they are the same. */
    @Test
    fun testBasic() {
        val num = atLeast(200)
        for (i in 0 until num) {
            val a = AutomatonTestUtil.randomAutomaton(random())
            val la = Operations.determinize(Operations.removeDeadStates(a), Int.MAX_VALUE)
            val lb = MinimizationOperations.minimize(a, Int.MAX_VALUE)
            assertTrue(AutomatonTestUtil.sameLanguage(la, lb))
        }
    }

    /**
     * compare minimized against minimized with a slower, simple impl. we check not only that they are
     * the same, but that #states/#transitions are the same.
     */
    @Test
    fun testAgainstBrzozowski() {
        val num = atLeast(200)
        for (i in 0 until num) {
            var a = AutomatonTestUtil.randomAutomaton(random())
            a = AutomatonTestUtil.minimizeSimple(a)
            val b = MinimizationOperations.minimize(a, Int.MAX_VALUE)
            assertTrue(AutomatonTestUtil.sameLanguage(a, b))
            assertEquals(a.numStates, b.numStates)
            val numStates = a.numStates
            var sum1 = 0
            for (s in 0 until numStates) {
                sum1 += a.getNumTransitions(s)
            }
            var sum2 = 0
            for (s in 0 until numStates) {
                sum2 += b.getNumTransitions(s)
            }
            assertEquals(sum1, sum2)
        }
    }

    /** n^2 space usage in Hopcroft minimization? */
    @Test
    @LuceneTestCase.Companion.Nightly
    fun testMinimizeHuge() {
        val a = RegExp("+-*(A|.....|BC)*]", RegExp.NONE).toAutomaton()
        val b = MinimizationOperations.minimize(a, 1_000_000)
        assertTrue(b.isDeterministic)
    }
}

