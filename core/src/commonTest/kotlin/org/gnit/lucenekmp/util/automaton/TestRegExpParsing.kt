package org.gnit.lucenekmp.util.automaton

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil

class TestRegExpParsing {
    @Test
    fun testAnyChar() {
        val re = RegExp(".")
        assertEquals(".", re.toString())
        assertEquals("REGEXP_ANYCHAR\n", re.toStringTree())

        val actual = re.toAutomaton()!!
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeAnyChar()
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testAnyString() {
        val re = RegExp("@", RegExp.ANYSTRING)
        assertEquals("@", re.toString())
        assertEquals("REGEXP_ANYSTRING\n", re.toStringTree())

        val actual = re.toAutomaton()!!
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeAnyString()
        assertSameLanguage(expected, actual)
    }

    private fun assertSameLanguage(expected: Automaton, actual: Automaton) {
        val detExpected = Operations.determinize(expected, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        val detActual = Operations.determinize(actual, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        val result = AutomatonTestUtil.sameLanguage(detExpected, detActual)
        if (!result) {
            println(detExpected.toDot())
            println(detActual.toDot())
        }
        assertTrue(result)
    }
}
