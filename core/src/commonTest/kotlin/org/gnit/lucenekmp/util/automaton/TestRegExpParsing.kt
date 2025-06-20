package org.gnit.lucenekmp.util.automaton

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import okio.IOException
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil

class TestRegExpParsing {
    @Test
    fun testAnyChar() {
        val re = RegExp(".")
        assertEquals(".", re.toString())
        assertEquals("REGEXP_ANYCHAR\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeAnyChar()
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testAnyString() {
        val re = RegExp("@", RegExp.ANYSTRING)
        assertEquals("@", re.toString())
        assertEquals("REGEXP_ANYSTRING\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeAnyString()
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testChar() {
        val re = RegExp("c")
        assertEquals("\\c", re.toString())
        assertEquals("REGEXP_CHAR char=c\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeChar('c'.code)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCaseInsensitiveChar() {
        val re = RegExp("c", RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE)
        assertEquals("\\c", re.toString())
        assertEquals("REGEXP_CHAR char=c\n", re.toStringTree())

        val actual = re.toAutomaton()
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
        AutomatonTestUtil.assertMinimalDFA(re.toAutomaton())
    }

    @Test
    fun testCaseInsensitiveClassRange() {
        val re = RegExp("[c-d]", RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE)
        assertEquals("REGEXP_CHAR_RANGE from=c to=d\n", re.toStringTree())
        AutomatonTestUtil.assertMinimalDFA(re.toAutomaton())
    }

    @Test
    fun testCaseInsensitiveCharUpper() {
        val re = RegExp("C", RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE)
        assertEquals("\\C", re.toString())
        assertEquals("REGEXP_CHAR char=C\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeCharSet(intArrayOf('c'.code, 'C'.code))
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCaseInsensitiveCharNotSensitive() {
        val re = RegExp("4", RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE)
        assertEquals("\\4", re.toString())
        assertEquals("REGEXP_CHAR char=4\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeChar('4'.code)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCaseInsensitiveCharNonAscii() {
        val re = RegExp("Ж", RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE)
        assertEquals("\\Ж", re.toString())
        assertEquals("REGEXP_CHAR char=Ж\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeCharSet(intArrayOf('Ж'.code, 'ж'.code))
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCaseInsensitiveCharUnicode() {
        val re = RegExp("Ж", RegExp.NONE, RegExp.CASE_INSENSITIVE)
        assertEquals("\\Ж", re.toString())
        assertEquals("REGEXP_CHAR char=Ж\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeCharSet(intArrayOf('Ж'.code, 'ж'.code))
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCaseInsensitiveCharUnicodeSigma() {
        val re = RegExp("σ", RegExp.NONE, RegExp.CASE_INSENSITIVE)
        assertEquals("\\σ", re.toString())
        assertEquals("REGEXP_CHAR char=σ\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeCharSet(intArrayOf('Σ'.code, 'σ'.code, 'ς'.code))
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testNegatedChar() {
        val re = RegExp("[^c]")
        assertEquals("(.&~(\\c))", re.toString())
        assertEquals(
            """REGEXP_INTERSECTION
  REGEXP_ANYCHAR
  REGEXP_COMPLEMENT
    REGEXP_CHAR char=c
""",
            re.toStringTree()
        )

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Operations.union(
            mutableListOf(
                Automata.makeCharRange(0, 'b'.code),
                Automata.makeCharRange('d'.code, 0x10FFFF)
            )
        )
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testNegatedClass() {
        val re = RegExp("[^c-da]")
        assertEquals(
            """REGEXP_INTERSECTION
  REGEXP_ANYCHAR
  REGEXP_COMPLEMENT
    REGEXP_CHAR_CLASS starts=[U+0063 U+0061] ends=[U+0064 U+0061]
""",
            re.toStringTree()
        )

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)
    }

    @Test
    fun testCharRange() {
        val re = RegExp("[b-d]")
        assertEquals("[\\b-\\d]", re.toString())
        assertEquals("REGEXP_CHAR_RANGE from=b to=d\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeCharRange('b'.code, 'd'.code)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testNegatedCharRange() {
        val re = RegExp("[^b-d]")
        assertEquals("(.&~([\\b-\\d]))", re.toString())
        assertEquals(
            """REGEXP_INTERSECTION
  REGEXP_ANYCHAR
  REGEXP_COMPLEMENT
    REGEXP_CHAR_RANGE from=b to=d
""",
            re.toStringTree()
        )

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Operations.union(
            mutableListOf(
                Automata.makeCharRange(0, 'a'.code),
                Automata.makeCharRange('e'.code, 0x10FFFF)
            )
        )
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testIllegalCharRange() {
        assertFailsWith<IllegalArgumentException> { RegExp("[z-a]") }
    }

    @Test
    fun testCharClassDigit() {
        val re = RegExp("[\\d]")
        assertEquals("[\\0-\\9]", re.toString())
        assertEquals("REGEXP_CHAR_RANGE from=0 to=9\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeCharRange('0'.code, '9'.code)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCharClassNonDigit() {
        val re = RegExp("[\\D]")
        assertEquals(
            "REGEXP_CHAR_CLASS starts=[U+0000 U+003A] ends=[U+002F U+10FFFF]\n",
            re.toStringTree()
        )

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Operations.minus(
            Automata.makeAnyChar(),
            Automata.makeCharRange('0'.code, '9'.code),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCharClassWhitespace() {
        val re = RegExp("[\\s]")
        assertEquals(
            "REGEXP_CHAR_CLASS starts=[U+0009 U+000D U+0020] ends=[U+000A U+000D U+0020]\n",
            re.toStringTree()
        )

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Operations.union(
            mutableListOf(
                Automata.makeChar(' '.code),
                Automata.makeChar('\n'.code),
                Automata.makeChar('\r'.code),
                Automata.makeChar('\t'.code)
            )
        )
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCharClassNonWhitespace() {
        val re = RegExp("[\\S]")
        assertEquals(
            "REGEXP_CHAR_CLASS starts=[U+0000 U+000B U+000E U+0021] ends=[U+0008 U+000C U+001F U+10FFFF]\n",
            re.toStringTree()
        )

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        var expected = Automata.makeAnyChar()
        expected = Operations.minus(
            expected,
            Automata.makeChar(' '.code),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
        expected = Operations.minus(
            expected,
            Automata.makeChar('\n'.code),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
        expected = Operations.minus(
            expected,
            Automata.makeChar('\r'.code),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
        expected = Operations.minus(
            expected,
            Automata.makeChar('\t'.code),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCharClassWord() {
        val re = RegExp("[\\w]")
        assertEquals("[\\0-\\9\\A-\\Z\\_\\a-\\z]", re.toString())
        assertEquals(
            "REGEXP_CHAR_CLASS starts=[U+0030 U+0041 U+005F U+0061] ends=[U+0039 U+005A U+005F U+007A]\n",
            re.toStringTree()
        )

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Operations.union(
            mutableListOf(
                Automata.makeCharRange('a'.code, 'z'.code),
                Automata.makeCharRange('A'.code, 'Z'.code),
                Automata.makeCharRange('0'.code, '9'.code),
                Automata.makeChar('_'.code)
            )
        )
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCharClassNonWord() {
        val re = RegExp("[\\W]")
        assertEquals(
            "REGEXP_CHAR_CLASS starts=[U+0000 U+003A U+005B U+0060 U+007B] ends=[U+002F U+0040 U+005E U+0060 U+10FFFF]\n",
            re.toStringTree()
        )

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        var expected = Automata.makeAnyChar()
        expected = Operations.minus(
            expected,
            Automata.makeCharRange('a'.code, 'z'.code),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
        expected = Operations.minus(
            expected,
            Automata.makeCharRange('A'.code, 'Z'.code),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
        expected = Operations.minus(
            expected,
            Automata.makeCharRange('0'.code, '9'.code),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
        expected = Operations.minus(
            expected,
            Automata.makeChar('_'.code),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testJumboCharClass() {
        val re = RegExp("[0-5a\\sbc-d]")
        assertEquals(
            "REGEXP_CHAR_CLASS starts=[U+0030 U+0061 U+0009 U+000D U+0020 U+0062 U+0063] ends=[U+0035 U+0061 U+000A U+000D U+0020 U+0062 U+0064]\n",
            re.toStringTree()
        )
        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)
    }

    @Test
    fun testTruncatedCharClass() {
        assertFailsWith<IllegalArgumentException> { RegExp("[b-d") }
    }

    @Test
    fun testBogusCharClass() {
        assertFailsWith<IllegalArgumentException> { RegExp("[\\q]") }
    }

    @Test
    fun testExcapedNotCharClass() {
        val re = RegExp("[\\?]")
        assertEquals("\\?", re.toString())
        assertEquals("REGEXP_CHAR char=?\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeChar('?'.code)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testExcapedSlashNotCharClass() {
        val re = RegExp("[\\\\]")
        assertEquals("\\\\", re.toString())
        assertEquals("REGEXP_CHAR char=\\\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeChar('\\'.code)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testEscapedDashCharClass() {
        val re = RegExp("[\\-]")
        assertEquals("REGEXP_CHAR char=-\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeChar('-'.code)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testEmpty() {
        val re = RegExp("#", RegExp.EMPTY)
        assertEquals("#", re.toString())
        assertEquals("REGEXP_EMPTY\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeEmpty()
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testEmptyClass() {
        val ex = assertFailsWith<IllegalArgumentException> { RegExp("[]") }
        assertEquals("expected ']' at position 2", ex.message)
    }

    @Test
    fun testEscapedInvalidClass() {
        val ex = assertFailsWith<IllegalArgumentException> { RegExp("[\\]") }
        assertEquals("expected ']' at position 3", ex.message)
    }

    @Test
    fun testInterval() {
        val re = RegExp("<5-40>")
        assertEquals("<5-40>", re.toString())
        assertEquals("REGEXP_INTERVAL<5-40>\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertCleanNFA(actual)

        val expected = Automata.makeDecimalInterval(5, 40, 0)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testBackwardsInterval() {
        val re = RegExp("<40-5>")
        assertEquals("<5-40>", re.toString())
        assertEquals("REGEXP_INTERVAL<5-40>\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertCleanNFA(actual)

        val expected = Automata.makeDecimalInterval(5, 40, 0)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testTruncatedInterval() {
        assertFailsWith<IllegalArgumentException> { RegExp("<1-") }
    }

    @Test
    fun testTruncatedInterval2() {
        assertFailsWith<IllegalArgumentException> { RegExp("<1") }
    }

    @Test
    fun testEmptyInterval() {
        assertFailsWith<IllegalArgumentException> { RegExp("<->") }
    }

    @Test
    fun testOptional() {
        val re = RegExp("a?")
        assertEquals("(\\a)?", re.toString())
        assertEquals("REGEXP_OPTIONAL\n  REGEXP_CHAR char=a\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Operations.optional(Automata.makeChar('a'.code))
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testRepeat0() {
        val re = RegExp("a*")
        assertEquals("(\\a)*", re.toString())
        assertEquals("REGEXP_REPEAT\n  REGEXP_CHAR char=a\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Operations.repeat(Automata.makeChar('a'.code))
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testRepeat1() {
        val re = RegExp("a+")
        assertEquals("(\\a){1,}", re.toString())
        assertEquals("REGEXP_REPEAT_MIN min=1\n  REGEXP_CHAR char=a\n", re.toStringTree())

        val actual = re.toAutomaton()
        assertEquals(3, actual.numStates)
        AutomatonTestUtil.assertCleanDFA(actual)

        val expected = Operations.repeat(Automata.makeChar('a'.code), 1)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testRepeatN() {
        val re = RegExp("a{5}")
        assertEquals("(\\a){5,5}", re.toString())
        assertEquals("REGEXP_REPEAT_MINMAX min=5 max=5\n  REGEXP_CHAR char=a\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Operations.repeat(Automata.makeChar('a'.code), 5, 5)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testRepeatNPlus() {
        val re = RegExp("a{5,}")
        assertEquals("(\\a){5,}", re.toString())
        assertEquals("REGEXP_REPEAT_MIN min=5\n  REGEXP_CHAR char=a\n", re.toStringTree())

        val actual = re.toAutomaton()
        assertEquals(7, actual.numStates)
        AutomatonTestUtil.assertCleanDFA(actual)

        val expected = Operations.repeat(Automata.makeChar('a'.code), 5)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testRepeatMN() {
        val re = RegExp("a{5,8}")
        assertEquals("(\\a){5,8}", re.toString())
        assertEquals("REGEXP_REPEAT_MINMAX min=5 max=8\n  REGEXP_CHAR char=a\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Operations.repeat(Automata.makeChar('a'.code), 5, 8)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testTruncatedRepeat() {
        assertFailsWith<IllegalArgumentException> { RegExp("a{5,8") }
    }

    @Test
    fun testBogusRepeat() {
        assertFailsWith<IllegalArgumentException> { RegExp("a{Z}") }
    }

    @Test
    fun testString() {
        val re = RegExp("boo")
        assertEquals("\"boo\"", re.toString())
        assertEquals("REGEXP_STRING string=boo\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeString("boo")
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testCaseInsensitiveString() {
        val re = RegExp("boo", RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE)
        assertEquals("\"boo\"", re.toString())
        assertEquals("REGEXP_STRING string=boo\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val c1 = Operations.union(mutableListOf(Automata.makeChar('b'.code), Automata.makeChar('B'.code)))
        val c2 = Operations.union(mutableListOf(Automata.makeChar('o'.code), Automata.makeChar('O'.code)))

        val expected = Operations.concatenate(mutableListOf(c1, c2, c2))
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testExplicitString() {
        val re = RegExp("\"boo\"")
        assertEquals("\"boo\"", re.toString())
        assertEquals("REGEXP_STRING string=boo\n", re.toStringTree())

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeString("boo")
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testNotTerminatedString() {
        assertFailsWith<IllegalArgumentException> { RegExp("\"boo") }
    }

    @Test
    fun testConcatenation() {
        val re = RegExp("[b-c][e-f]")
        assertEquals("[\\b-\\c][\\e-\\f]", re.toString())
        assertEquals(
            """REGEXP_CONCATENATION
  REGEXP_CHAR_RANGE from=b to=c
  REGEXP_CHAR_RANGE from=e to=f""".trimIndent(),
            re.toStringTree().trimEnd()
        )

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Operations.concatenate(
            mutableListOf(Automata.makeCharRange('b'.code, 'c'.code), Automata.makeCharRange('e'.code, 'f'.code))
        )
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testIntersection() {
        val re = RegExp("[b-f]&[e-f]")
        assertEquals("([\\b-\\f]&[\\e-\\f])", re.toString())
        assertEquals(
            """REGEXP_INTERSECTION
  REGEXP_CHAR_RANGE from=b to=f
  REGEXP_CHAR_RANGE from=e to=f""".trimIndent(),
            re.toStringTree().trimEnd()
        )

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Operations.intersection(
            Automata.makeCharRange('b'.code, 'f'.code),
            Automata.makeCharRange('e'.code, 'f'.code)
        )
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testTruncatedIntersection() {
        assertFailsWith<IllegalArgumentException> { RegExp("a&") }
    }

    @Test
    fun testTruncatedIntersectionParens() {
        assertFailsWith<IllegalArgumentException> { RegExp("(a)&(") }
    }

    @Test
    fun testUnion() {
        val re = RegExp("[b-c]|[e-f]")
        assertEquals("([\\b-\\c]|[\\e-\\f])", re.toString())
        assertEquals(
            """REGEXP_UNION
  REGEXP_CHAR_RANGE from=b to=c
  REGEXP_CHAR_RANGE from=e to=f""".trimIndent(),
            re.toStringTree().trimEnd()
        )

        val actual = re.toAutomaton()
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Operations.union(
            mutableListOf(
                Automata.makeCharRange('b'.code, 'c'.code),
                Automata.makeCharRange('e'.code, 'f'.code)
            )
        )
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testTruncatedUnion() {
        assertFailsWith<IllegalArgumentException> { RegExp("a|") }
    }

    @Test
    fun testTruncatedUnionParens() {
        assertFailsWith<IllegalArgumentException> { RegExp("(a)|(") }
    }

    @Test
    fun testAutomaton() {
        val myProvider = object : AutomatonProvider {
            override fun getAutomaton(name: String): Automaton {
                return Automata.makeChar('z'.code)
            }
        }
        val re = RegExp("<myletter>", RegExp.ALL)
        assertEquals("<myletter>", re.toString())
        assertEquals("REGEXP_AUTOMATON\n", re.toStringTree())
        assertEquals(setOf<String?>("myletter"), re.identifiers)

        val actual = re.toAutomaton(myProvider)!!
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeChar('z'.code)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testAutomatonMap() {
        val re = RegExp("<myletter>", RegExp.ALL)
        assertEquals("<myletter>", re.toString())
        assertEquals("REGEXP_AUTOMATON\n", re.toStringTree())
        assertEquals(setOf<String?>("myletter"), re.identifiers)

        val actual = re.toAutomaton(
            mutableMapOf("myletter" to Automata.makeChar('z'.code))
        )!!
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automata.makeChar('z'.code)
        assertSameLanguage(expected, actual)
    }

    @Test
    fun testAutomatonIOException() {
        val myProvider = object : AutomatonProvider {
            override fun getAutomaton(name: String): Automaton {
                throw IOException("fake ioexception")
            }
        }
        val re = RegExp("<myletter>", RegExp.ALL)
        assertEquals("<myletter>", re.toString())
        assertEquals("REGEXP_AUTOMATON\n", re.toStringTree())
        assertEquals(setOf<String?>("myletter"), re.identifiers)

        assertFailsWith<IllegalArgumentException> { re.toAutomaton(myProvider) }
    }

    @Test
    fun testAutomatonNotFound() {
        val re = RegExp("<bogus>", RegExp.ALL)
        assertEquals("<bogus>", re.toString())
        assertEquals("REGEXP_AUTOMATON\n", re.toStringTree())

        assertFailsWith<IllegalArgumentException> {
            re.toAutomaton(mutableMapOf("myletter" to Automata.makeChar('z'.code)))
        }
    }

    @Test
    fun testIllegalSyntaxFlags() {
        assertFailsWith<IllegalArgumentException> { RegExp("bogus", Int.MAX_VALUE) }
    }

    @Test
    fun testIllegalMatchFlags() {
        assertFailsWith<IllegalArgumentException> { RegExp("bogus", RegExp.ALL, 1) }
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
