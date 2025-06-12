package org.gnit.lucenekmp.util.automaton

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Operations
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
}
