package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RamUsageTester
import kotlin.test.Test
import kotlin.test.Ignore
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.jdkport.Character
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.random.Random

class TestNFARunAutomaton : LuceneTestCase() {

    @Test
    fun testRamUsageEstimation() {
        val regExp = RegExp(AutomatonTestUtil.randomRegexp(random()), RegExp.NONE)
        val nfa = regExp.toAutomaton()
        val runAutomaton = NFARunAutomaton(nfa)
        val estimation = runAutomaton.ramBytesUsed()
        val actual = RamUsageTester.ramUsed(runAutomaton)
        assertEquals(actual.toDouble(), estimation.toDouble(), actual.toDouble() * 0.3)
    }

    @Test
    fun testWithRandomRegex() {
        var count = 0
        while (count < 100) {
            val regExp = RegExp(AutomatonTestUtil.randomRegexp(random()), RegExp.NONE)
            val nfa = regExp.toAutomaton()
            if (nfa.isDeterministic) {
                continue
            }
            val dfa = Operations.determinize(nfa, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
            val candidate = NFARunAutomaton(nfa)
            val randomStringGen = try {
                AutomatonTestUtil.RandomAcceptedStrings(dfa)
            } catch (_: IllegalArgumentException) {
                continue
            }
            repeat(20) {
                if (random().nextBoolean()) {
                    testAcceptedString(regExp, randomStringGen, candidate, 10)
                    testRandomString(regExp, dfa, candidate, 10)
                } else {
                    testRandomString(regExp, dfa, candidate, 10)
                    testAcceptedString(regExp, randomStringGen, candidate, 10)
                }
            }
            count++
        }
    }

    @Test
    fun testRandomAccessTransition() {
        var nfa = RegExp(AutomatonTestUtil.randomRegexp(random()), RegExp.NONE).toAutomaton()
        while (nfa.isDeterministic) {
            nfa = RegExp(AutomatonTestUtil.randomRegexp(random()), RegExp.NONE).toAutomaton()
        }
        val runAutomaton1 = NFARunAutomaton(nfa)
        val runAutomaton2 = NFARunAutomaton(nfa)
        assertRandomAccessTransition(runAutomaton1, runAutomaton2, 0, HashSet())
    }

    @Ignore
    @Test
    fun testRandomAutomatonQuery() {
        // TODO implement after IndexWriter ported
    }

    private fun assertRandomAccessTransition(
        automaton1: NFARunAutomaton,
        automaton2: NFARunAutomaton,
        state: Int,
        visited: MutableSet<Int>
    ) {
        if (!visited.add(state)) return

        val t1 = Transition()
        val t2 = Transition()
        automaton1.initTransition(state, t1)
        if (random().nextBoolean()) {
            automaton2.initTransition(state, t2)
        }
        val numStates = automaton2.getNumTransitions(state)
        for (i in 0 until numStates) {
            automaton1.getNextTransition(t1)
            automaton2.getTransition(state, i, t2)
            assertEquals(t1.toString(), t2.toString())
            assertRandomAccessTransition(automaton1, automaton2, t1.dest, visited)
        }
    }

    private fun testAcceptedString(
        regExp: RegExp,
        randomStringGen: AutomatonTestUtil.RandomAcceptedStrings,
        candidate: NFARunAutomaton,
        repeat: Int
    ) {
        repeat(repeat) {
            val acceptedString = randomStringGen.getRandomAcceptedString(random())
            assertTrue(candidate.run(acceptedString),
                "regExp: $regExp testString: ${acceptedString.contentToString()}")
        }
    }

    private fun testRandomString(
        regExp: RegExp,
        dfa: Automaton,
        candidate: NFARunAutomaton,
        repeat: Int
    ) {
        repeat(repeat) {
            val randomString = IntArray(random().nextInt(50)) { Random.nextInt(0, Character.MAX_CODE_POINT) }
            val expected = Operations.run(dfa, IntsRef(randomString, 0, randomString.size))
            val actual = candidate.run(randomString)
            assertEquals(expected, actual,
                "regExp: $regExp testString: ${randomString.contentToString()}")
        }
    }
}
