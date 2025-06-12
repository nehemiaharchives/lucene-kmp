package org.gnit.lucenekmp.util.automaton

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.Ignore
import org.gnit.lucenekmp.util.automaton.NFARunAutomaton
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.isLowSurrogate
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.MinimizationOperations
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil


class TestAutomaton {
    @Test
    fun testBasic() {
        val a = Automaton()
        val start = a.createState()
        val x = a.createState()
        val y = a.createState()
        val end = a.createState()
        a.setAccept(end, true)

        a.addTransition(start, x, 'a'.code, 'a'.code)
        a.addTransition(start, end, 'd'.code, 'd'.code)
        a.addTransition(x, y, 'b'.code, 'b'.code)
        a.addTransition(y, end, 'c'.code, 'c'.code)
        a.finishState()
    }

    @Test
    fun testReduceBasic() {
        val a = Automaton()
        val start = a.createState()
        val end = a.createState()
        a.setAccept(end, true)
        a.addTransition(start, end, 'a'.code, 'a'.code)
        a.addTransition(start, end, 'b'.code, 'b'.code)
        a.addTransition(start, end, 'm'.code, 'm'.code)
        a.addTransition(start, end, 'x'.code, 'x'.code)
        a.addTransition(start, end, 'y'.code, 'y'.code)
        a.finishState()
        assertEquals(3, a.getNumTransitions(start))
        val scratch = Transition()
        a.initTransition(start, scratch)
        a.getNextTransition(scratch)
        assertEquals('a'.code, scratch.min)
        assertEquals('b'.code, scratch.max)
        a.getNextTransition(scratch)
        assertEquals('m'.code, scratch.min)
        assertEquals('m'.code, scratch.max)
        a.getNextTransition(scratch)
        assertEquals('x'.code, scratch.min)
        assertEquals('y'.code, scratch.max)
    }

    @Test
    fun testSameLanguage() {
        val a1 = Automata.makeString("foobar")
        val a2 = Operations.concatenate(mutableListOf(Automata.makeString("foo"), Automata.makeString("bar")))
        assertTrue(AutomatonTestUtil.sameLanguage(a1, a2))
    }

    @Test
    fun testCommonPrefixString() {
        val a = Operations.concatenate(mutableListOf(Automata.makeString("foobar"), Automata.makeAnyString()))
        AutomatonTestUtil.assertCleanDFA(a)
        assertEquals("foobar", Operations.getCommonPrefix(a))
    }


    @Test
    fun testCommonPrefixEmpty() {
        assertEquals("", Operations.getCommonPrefix(Automata.makeEmpty()))
    }

    @Test
    fun testCommonPrefixEmptyString() {
        assertEquals("", Operations.getCommonPrefix(Automata.makeEmptyString()))
    }

    @Test
    fun testCommonPrefixAny() {
        assertEquals("", Operations.getCommonPrefix(Automata.makeAnyString()))
    }

    @Test
    fun testCommonPrefixRange() {
        assertEquals("", Operations.getCommonPrefix(Automata.makeCharRange('a'.code, 'b'.code)))
    }

    @Test
    fun testAlternatives() {
        val a = Automata.makeChar('a'.code)
        val c = Automata.makeChar('c'.code)
        assertEquals("", Operations.getCommonPrefix(Operations.union(mutableListOf(a, c))))
    }

    @Test
    fun testCommonPrefixLeadingWildcard() {
        val a = Operations.concatenate(mutableListOf(Automata.makeAnyChar(), Automata.makeString("boo")))
        AutomatonTestUtil.assertMinimalDFA(a)
        assertEquals("", Operations.getCommonPrefix(a))
    }

    @Test
    fun testCommonPrefixTrailingWildcard() {
        val a = Operations.concatenate(mutableListOf(Automata.makeString("boo"), Automata.makeAnyChar()))
        AutomatonTestUtil.assertMinimalDFA(a)
        assertEquals("boo", Operations.getCommonPrefix(a))
    }

    @Test
    fun testCommonPrefixLeadingKleenStar() {
        val a = Operations.concatenate(mutableListOf(Automata.makeAnyString(), Automata.makeString("boo")))
        AutomatonTestUtil.assertCleanNFA(a)
        assertEquals("", Operations.getCommonPrefix(a))
    }

    @Test
    fun testCommonPrefixTrailingKleenStar() {
        val a = Operations.concatenate(mutableListOf(Automata.makeString("boo"), Automata.makeAnyString()))
        AutomatonTestUtil.assertCleanDFA(a)
        assertEquals("boo", Operations.getCommonPrefix(a))
    }

    @Test
    fun testCommonPrefixOptional() {
        val a = Automaton()
        val init = a.createState()
        val fini = a.createState()
        a.setAccept(init, true)
        a.setAccept(fini, true)
        a.addTransition(init, fini, 'm'.code, 'm'.code)
        a.addTransition(fini, fini, 'm'.code, 'm'.code)
        a.finishState()
        assertEquals("", Operations.getCommonPrefix(a))
    }

    @Test
    fun testCommonPrefixNFA() {
        val a = Automaton()
        val init = a.createState()
        val medial = a.createState()
        val fini = a.createState()
        a.setAccept(fini, true)
        a.addTransition(init, medial, 'm'.code, 'm'.code)
        a.addTransition(init, fini, 'm'.code, 'm'.code)
        a.addTransition(medial, fini, 'o'.code, 'o'.code)
        a.finishState()
        assertEquals("m", Operations.getCommonPrefix(a))
    }

    @Test
    fun testCommonPrefixNFAInfinite() {
        val a = Automaton()
        val init = a.createState()
        val medial = a.createState()
        val fini = a.createState()
        a.setAccept(fini, true)
        a.addTransition(init, medial, 'm'.code, 'm'.code)
        a.addTransition(init, fini, 'm'.code, 'm'.code)
        a.addTransition(medial, fini, 'm'.code, 'm'.code)
        a.addTransition(fini, fini, 'm'.code, 'm'.code)
        a.finishState()
        assertEquals("m", Operations.getCommonPrefix(a))
    }

    @Test
    fun testCommonPrefixUnicode() {
        val a = Operations.concatenate(mutableListOf(Automata.makeString("boo😂😂😂"), Automata.makeAnyChar()))
        assertEquals("boo😂😂😂", Operations.getCommonPrefix(a))
    }

    @Test
    fun testConcatenate1() {
        val a = Operations.concatenate(mutableListOf(Automata.makeString("m"), Automata.makeAnyString()))
        AutomatonTestUtil.assertCleanDFA(a)
        assertTrue(Operations.run(a, "m"))
        assertTrue(Operations.run(a, "me"))
        assertTrue(Operations.run(a, "me too"))
    }

    @Ignore
    @Test
    fun testConcatenate2() {
        val a = Operations.concatenate(mutableListOf(Automata.makeString("m"), Automata.makeAnyString(), Automata.makeString("n"), Automata.makeAnyString()))
        AutomatonTestUtil.assertCleanNFA(a)
        val run = NFARunAutomaton(a)
        assertTrue(run.run("mn".toCodePoints()))
        assertTrue(run.run("mone".toCodePoints()))
        assertFalse(run.run("m".toCodePoints()))
        assertFalse(AutomatonTestUtil.isFinite(a))
    }

    @Ignore
    @Test
    fun testUnion1() {
        val a = Operations.union(mutableListOf(Automata.makeString("foobar"), Automata.makeString("barbaz")))
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, "foobar"))
        assertTrue(Operations.run(a, "barbaz"))
        AutomatonTestUtil.assertMatches(a, "foobar", "barbaz")
    }

    @Ignore
    @Test
    fun testUnion2() {
        val a = Operations.union(mutableListOf(Automata.makeString("foobar"), Automata.makeString(""), Automata.makeString("barbaz")))
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, "foobar"))
        assertTrue(Operations.run(a, "barbaz"))
        assertTrue(Operations.run(a, ""))
        AutomatonTestUtil.assertMatches(a, "", "foobar", "barbaz")
    }

    @Ignore
    @Test
    fun testMinimizeSimple() {
        val a = Automata.makeString("foobar")
        val aMin = MinimizationOperations.minimize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        assertTrue(AutomatonTestUtil.sameLanguage(a, aMin))
    }

    @Ignore
    @Test
    fun testMinimize2() {
        val a = Operations.union(mutableListOf(Automata.makeString("foobar"), Automata.makeString("boobar")))
        val aMin = MinimizationOperations.minimize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                Operations.determinize(Operations.removeDeadStates(a), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT),
                aMin
            )
        )
    }

    @Test
    fun testReverse() {
        val a = Automata.makeString("foobar")
        val ra = Operations.reverse(a)
        AutomatonTestUtil.assertMinimalDFA(a)
        val a2 = Operations.reverse(ra)
        AutomatonTestUtil.assertMinimalDFA(a2)
        assertTrue(AutomatonTestUtil.sameLanguage(a, a2))
    }
}

private fun String.toCodePoints(): IntArray {
    var result = IntArray(this.length)
    var j = 0
    var i = 0
    while (i < this.length) {
        val ch = this[i]
        val cp: Int
        if (Character.isHighSurrogate(ch) && i + 1 < this.length && this[i + 1].isLowSurrogate()) {
            cp = Character.toCodePoint(ch, this[i + 1])
            i += 2
        } else {
            cp = ch.code
            i++
        }
        if (j == result.size) {
            result = result.copyOf(j + 10)
        }
        result[j++] = cp
    }
    return result.copyOf(j)
}
