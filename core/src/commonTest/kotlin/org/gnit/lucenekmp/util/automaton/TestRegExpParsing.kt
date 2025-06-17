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

    @Test
    fun testChar() {
        val re = RegExp("c")
        assertEquals("\\c", re.toString())
        assertEquals("REGEXP_CHAR char=c\n", re.toStringTree())

        val actual = re.toAutomaton()!!
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeChar('c'.code)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCaseInsensitiveChar() {
        val re = RegExp("c", RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE)
        assertEquals("\\c", re.toString())
        assertEquals("REGEXP_CHAR char=c\n", re.toStringTree())

        val actual = re.toAutomaton()!!
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeCharSet(intArrayOf('c'.code, 'C'.code))
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCaseInsensitiveClassChar() {
        val re = RegExp("[c]", RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE)
        assertEquals(
            "REGEXP_CHAR_CLASS starts=[U+0043 U+0063] ends=[U+0043 U+0063]\n",
            re.toStringTree()
        )
        AutomatonTestUtil.assertMinimalDFA(re.toAutomaton()!!)
    }

    @Test
    fun testCaseInsensitiveClassRange() {
        val re = RegExp("[c-d]", RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE)
        assertEquals("REGEXP_CHAR_RANGE from=c to=d\n", re.toStringTree())
        AutomatonTestUtil.assertMinimalDFA(re.toAutomaton()!!)
    }

    @Test
    fun testCaseInsensitiveCharUpper() {
        val re = RegExp("C", RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE)
        assertEquals("\\C", re.toString())
        assertEquals("REGEXP_CHAR char=C\n", re.toStringTree())

        val actual = re.toAutomaton()!!
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeCharSet(intArrayOf('c'.code, 'C'.code))
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCaseInsensitiveCharNotSensitive() {
        val re = RegExp("4", RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE)
        assertEquals("\\4", re.toString())
        assertEquals("REGEXP_CHAR char=4\n", re.toStringTree())

        val actual = re.toAutomaton()!!
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeChar('4'.code)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCaseInsensitiveCharNonAscii() {
        val re = RegExp("Ж", RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE)
        assertEquals("\\Ж", re.toString())
        assertEquals("REGEXP_CHAR char=Ж\n", re.toStringTree())

        val actual = re.toAutomaton()!!
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeCharSet(intArrayOf('Ж'.code, 'ж'.code))
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCaseInsensitiveCharUnicode() {
        val re = RegExp("Ж", RegExp.NONE, RegExp.CASE_INSENSITIVE)
        assertEquals("\\Ж", re.toString())
        assertEquals("REGEXP_CHAR char=Ж\n", re.toStringTree())

        val actual = re.toAutomaton()!!
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeCharSet(intArrayOf('Ж'.code, 'ж'.code))
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCaseInsensitiveCharUnicodeSigma() {
        val re = RegExp("σ", RegExp.NONE, RegExp.CASE_INSENSITIVE)
        assertEquals("\\σ", re.toString())
        assertEquals("REGEXP_CHAR char=σ\n", re.toStringTree())

        val actual = re.toAutomaton()!!
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeCharSet(intArrayOf('Σ'.code, 'σ'.code, 'ς'.code))
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
