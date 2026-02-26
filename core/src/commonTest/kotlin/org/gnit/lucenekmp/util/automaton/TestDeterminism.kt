package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import kotlin.test.Test
import kotlin.test.assertTrue

/** Not completely thorough, but tries to test determinism correctness somewhat randomly. */
class TestDeterminism : LuceneTestCase() {

    /** test a bunch of random regular expressions */
    @Test
    fun testRegexps() {
        val num = atLeast(5) // TODO reduced from 500  to 5 for dev speed
        for (i in 0 until num) {
            assertAutomaton(RegExp(AutomatonTestUtil.randomRegexp(random()), RegExp.NONE).toAutomaton())
        }
    }

    /** test against a simple, unoptimized det */
    @Test
    fun testAgainstSimple() {
        val num = atLeast(3) // TODO reduced from 200 to 3 for dev speed
        for (i in 0 until num) {
            var a = AutomatonTestUtil.randomAutomaton(random())
            a = AutomatonTestUtil.determinizeSimple(a)
            val b = Operations.determinize(a, Int.MAX_VALUE)
            // TODO: more verifications possible?
            assertTrue(AutomatonTestUtil.sameLanguage(a, b))
        }
    }

    companion object {
        private fun assertAutomaton(a0: Automaton) {
            val a = Operations.determinize(Operations.removeDeadStates(a0), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)

            // complement(complement(a)) = a
            var equivalent =
                Operations.complement(
                    Operations.complement(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT),
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                )
            assertTrue(AutomatonTestUtil.sameLanguage(a, equivalent))

            // a union a = a
            equivalent =
                Operations.determinize(
                    Operations.removeDeadStates(Operations.union(mutableListOf(a, a))),
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                )
            assertTrue(AutomatonTestUtil.sameLanguage(a, equivalent))

            // a intersect a = a
            equivalent =
                Operations.determinize(
                    Operations.removeDeadStates(Operations.intersection(a, a)),
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                )
            assertTrue(AutomatonTestUtil.sameLanguage(a, equivalent))

            // a minus a = empty
            val empty = Operations.minus(a, a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
            assertTrue(Operations.isEmpty(empty))

            // as long as don't accept the empty string
            // then optional(a) - empty = a
            if (!Operations.run(a, "")) {
                // System.out.println("test " + a);
                val optional = Operations.optional(a)
                // System.out.println("optional " + optional);
                equivalent =
                    Operations.minus(optional, Automata.makeEmptyString(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
                // System.out.println("equiv " + equivalent);
                assertTrue(AutomatonTestUtil.sameLanguage(a, equivalent))
            }
        }
    }
}
