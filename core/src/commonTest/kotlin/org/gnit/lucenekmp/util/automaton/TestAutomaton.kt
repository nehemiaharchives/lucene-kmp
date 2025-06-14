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
import org.gnit.lucenekmp.util.automaton.UTF32ToUTF8
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.Util
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.automaton.TooComplexToDeterminizeException
import org.gnit.lucenekmp.util.automaton.RegExp
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
    class TestAutomaton {
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
    @Test
    fun testOptional() {
        val a = Automata.makeString("foobar")
        val a2 = Operations.optional(a)
        AutomatonTestUtil.assertMinimalDFA(a2)
    assertTrue(Operations.run(a, "foobar"))
        assertFalse(Operations.run(a, ""))
        assertTrue(Operations.run(a2, "foobar"))
        assertTrue(Operations.run(a2, ""))
    }
    @Test
    fun testRepeatAny() {
        val a = Automata.makeString("zee")
        val a2 = Operations.repeat(a)
        AutomatonTestUtil.assertMinimalDFA(a2)
    assertTrue(Operations.run(a2, ""))
        assertTrue(Operations.run(a2, "zee"))
        assertTrue(Operations.run(a2, "zeezee"))
        assertTrue(Operations.run(a2, "zeezeezee"))
    }
    @Test
    fun testRepeatMin() {
        val a = Automata.makeString("zee")
        val a2 = Operations.repeat(a, 2)
        AutomatonTestUtil.assertCleanDFA(a2)
    assertFalse(Operations.run(a2, ""))
        assertFalse(Operations.run(a2, "zee"))
        assertTrue(Operations.run(a2, "zeezee"))
        assertTrue(Operations.run(a2, "zeezeezee"))
    }
    @Ignore
    @Test
    fun testRepeatMinMax1() {
        val a = Automata.makeString("zee")
        val a2 = Operations.repeat(a, 0, 2)
        AutomatonTestUtil.assertMinimalDFA(a2)
    assertTrue(Operations.run(a2, ""))
        assertTrue(Operations.run(a2, "zee"))
        assertTrue(Operations.run(a2, "zeezee"))
        assertFalse(Operations.run(a2, "zeezeezee"))
    }
    @Ignore
    @Test
    fun testRepeatMinMax2() {
        val a = Automata.makeString("zee")
        val a2 = Operations.repeat(a, 2, 4)
        AutomatonTestUtil.assertMinimalDFA(a2)
    assertFalse(Operations.run(a2, ""))
        assertFalse(Operations.run(a2, "zee"))
        assertTrue(Operations.run(a2, "zeezee"))
        assertTrue(Operations.run(a2, "zeezeezee"))
        assertTrue(Operations.run(a2, "zeezeezeezee"))
        assertFalse(Operations.run(a2, "zeezeezeezeezee"))
    }
    @Test
    fun testComplement() {
        val a = Automata.makeString("zee")
        val a2 = Operations.complement(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        AutomatonTestUtil.assertMinimalDFA(a2)
    assertTrue(Operations.run(a2, ""))
        assertFalse(Operations.run(a2, "zee"))
        assertTrue(Operations.run(a2, "zeezee"))
        assertTrue(Operations.run(a2, "zeezeezee"))
    }
    @Test
    fun testInterval() {
        val a = Automata.makeDecimalInterval(17, 100, 3)
        AutomatonTestUtil.assertCleanDFA(a)
    assertFalse(Operations.run(a, ""))
        assertTrue(Operations.run(a, "017"))
        assertTrue(Operations.run(a, "100"))
        assertTrue(Operations.run(a, "073"))
    }
    @Test
    fun testCommonSuffix() {
        val a = Automaton()
        val init = a.createState()
        val fini = a.createState()
        a.setAccept(init, true)
        a.setAccept(fini, true)
        a.addTransition(init, fini, 'm'.code)
        a.addTransition(fini, fini, 'm'.code)
        a.finishState()
        assertEquals(0, Operations.getCommonSuffixBytesRef(a).length)
    }
    @Test
    fun testCommonSuffixEmpty() {
        assertEquals(LuceneTestCase.newBytesRef(), Operations.getCommonSuffixBytesRef(Automata.makeEmpty()))
    }
    @Test
    fun testCommonSuffixEmptyString() {
        assertEquals(LuceneTestCase.newBytesRef(), Operations.getCommonSuffixBytesRef(Automata.makeEmptyString()))
    }
    @Test
    fun testCommonSuffixTrailingWildcard() {
        val a = Operations.concatenate(mutableListOf(Automata.makeString("boo"), Automata.makeAnyChar()))
        AutomatonTestUtil.assertMinimalDFA(a)
        assertEquals(LuceneTestCase.newBytesRef(), Operations.getCommonSuffixBytesRef(a))
    }
    @Test
    fun testCommonSuffixLeadingKleenStar() {
        val a = Operations.concatenate(mutableListOf(Automata.makeAnyString(), Automata.makeString("boo")))
        AutomatonTestUtil.assertCleanNFA(a)
        assertEquals(LuceneTestCase.newBytesRef("boo"), Operations.getCommonSuffixBytesRef(a))
    }
    @Test
    fun testCommonSuffixTrailingKleenStar() {
        val a = Operations.concatenate(mutableListOf(Automata.makeString("boo"), Automata.makeAnyString()))
        AutomatonTestUtil.assertCleanDFA(a)
        assertEquals(LuceneTestCase.newBytesRef(), Operations.getCommonSuffixBytesRef(a))
    }
    @Test
    fun testCommonSuffixUnicode() {
        val a = Operations.concatenate(mutableListOf(Automata.makeAnyString(), Automata.makeString("boo😂😂😂")))
        AutomatonTestUtil.assertCleanNFA(a)
        val binary = UTF32ToUTF8().convert(a)
        assertEquals(LuceneTestCase.newBytesRef("boo😂😂😂"), Operations.getCommonSuffixBytesRef(binary))
    }
    @Ignore
    @Test
    fun testReverseRandom1() {
    }
    @Ignore
    @Test
    fun testReverseRandom2() {
    }
    @Test
    fun testAnyStringEmptyString() {
        val a = Automata.makeAnyString()
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, ""))
    }
    @Test
    fun testBasicIsEmpty() {
        val a = Automaton()
        a.createState()
        assertTrue(Operations.isEmpty(a))
    }
    @Test
    fun testRemoveDeadTransitionsEmpty() {
        val a = Automata.makeEmpty()
        val a2 = Operations.removeDeadStates(a)
        assertTrue(Operations.isEmpty(a2))
    }
    @Test
    fun testInvalidAddTransition() {
        val a = Automaton()
        val s1 = a.createState()
        val s2 = a.createState()
        a.addTransition(s1, s2, 'a'.code)
        a.addTransition(s2, s2, 'a'.code)
        try {
            a.addTransition(s1, s2, 'b'.code)
            kotlin.test.fail("expected IllegalStateException")
        } catch (_: IllegalStateException) {
        }
    }
    @Ignore
    @Test
    fun testBuilderRandom() {
        // requires random automaton utilities
    }
    @Test
    fun testIsTotal() {
        assertFalse(Operations.isTotal(Automaton()))
        val a = Automaton()
        val init = a.createState()
        val fini = a.createState()
        a.setAccept(fini, true)
        a.addTransition(init, fini, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT)
        a.finishState()
        assertFalse(Operations.isTotal(a))
        a.addTransition(fini, fini, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT)
        a.finishState()
        assertFalse(Operations.isTotal(a))
        a.setAccept(init, true)
        assertTrue(Operations.isTotal(MinimizationOperations.minimize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)))
    }
    @Test
    fun testMinimizeEmpty() {
        var a = Automaton()
        val init = a.createState()
        val fini = a.createState()
        a.addTransition(init, fini, 'a'.code)
        a.finishState()
        a = MinimizationOperations.minimize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        assertEquals(0, a.numStates)
    }
    @Ignore
    @Test
    fun testMinus() {
        val a1 = Automata.makeString("foobar")
        val a2 = Automata.makeString("boobar")
        val a3 = Automata.makeString("beebar")
        val a = Operations.union(mutableListOf(a1, a2, a3))
        AutomatonTestUtil.assertCleanNFA(a)
        AutomatonTestUtil.assertMatches(a, "foobar", "beebar", "boobar")
    var a4 = Operations.minus(a, a2, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        AutomatonTestUtil.assertCleanDFA(a4)
    assertTrue(Operations.run(a4, "foobar"))
        assertFalse(Operations.run(a4, "boobar"))
        assertTrue(Operations.run(a4, "beebar"))
        AutomatonTestUtil.assertMatches(a4, "foobar", "beebar")
    a4 = Operations.minus(a4, a1, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        AutomatonTestUtil.assertCleanDFA(a4)
    assertFalse(Operations.run(a4, "foobar"))
        assertFalse(Operations.run(a4, "boobar"))
        assertTrue(Operations.run(a4, "beebar"))
        AutomatonTestUtil.assertMatches(a4, "beebar")
    a4 = Operations.minus(a4, a3, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        AutomatonTestUtil.assertCleanDFA(a4)
    assertFalse(Operations.run(a4, "foobar"))
        assertFalse(Operations.run(a4, "boobar"))
        assertFalse(Operations.run(a4, "beebar"))
        AutomatonTestUtil.assertMatches(a4)
    }
    @Ignore
    @Test
    fun testOneInterval() {
        var a = Automata.makeDecimalInterval(999, 1032, 0)
        AutomatonTestUtil.assertCleanNFA(a)
        a = Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        assertTrue(Operations.run(a, "0999"))
        assertTrue(Operations.run(a, "00999"))
        assertTrue(Operations.run(a, "000999"))
    }
    @Test
    fun testAnotherInterval() {
        val a = Automata.makeDecimalInterval(1, 2, 0)
        AutomatonTestUtil.assertCleanDFA(a)
        assertTrue(Operations.run(a, "01"))
    }
    @Ignore
    @Test
    fun testIntervalRandom() {
        // relies on random generation utilities
    }
    @Test
    fun testConcatenatePreservesDet() {
        val a1 = Automata.makeString("foobar")
        AutomatonTestUtil.assertMinimalDFA(a1)
        val a2 = Automata.makeString("baz")
        AutomatonTestUtil.assertMinimalDFA(a2)
        val a3 = Operations.concatenate(mutableListOf(a1, a2))
        AutomatonTestUtil.assertMinimalDFA(a3)
    }
    @Test
    fun testRemoveDeadStates() {
        var a = Automaton()
        val s1 = a.createState()
        a.createState()
        a.setAccept(s1, true)
        a.finishState()
        assertEquals(2, a.numStates)
        a = Operations.removeDeadStates(a)
        assertEquals(1, a.numStates)
    }
    @Test
    fun testRemoveDeadStatesEmpty1() {
        val a = Automaton()
        a.finishState()
        assertTrue(Operations.isEmpty(a))
        assertTrue(Operations.isEmpty(Operations.removeDeadStates(a)))
    }
    @Test
    fun testRemoveDeadStatesEmpty2() {
        val a = Automaton()
        a.finishState()
        assertTrue(Operations.isEmpty(a))
        assertTrue(Operations.isEmpty(Operations.removeDeadStates(a)))
    }
    @Test
    fun testRemoveDeadStatesEmpty3() {
        val a = Automaton()
        val init = a.createState()
        val fini = a.createState()
        a.addTransition(init, fini, 'a'.code)
        val a2 = Operations.removeDeadStates(a)
        assertEquals(0, a2.numStates)
    }
    @Test
    fun testConcatEmpty() {
        var a = Operations.concatenate(mutableListOf(Automata.makeEmpty(), Automata.makeString("foo")))
        AutomatonTestUtil.assertMinimalDFA(a)
        assertEquals(emptySet<IntsRef>(), AutomatonTestUtil.getFiniteStrings(a))
    a = Operations.concatenate(mutableListOf(Automata.makeString("foo"), Automata.makeEmpty()))
        AutomatonTestUtil.assertMinimalDFA(a)
        assertEquals(emptySet<IntsRef>(), AutomatonTestUtil.getFiniteStrings(a))
    }
    @Test
    fun testSeemsNonEmptyButIsNot1() {
        val a = Automaton()
        val init = a.createState()
        val s = a.createState()
        a.addTransition(init, s, 'a'.code)
        a.finishState()
        assertTrue(Operations.isEmpty(a))
    }
    @Test
    fun testSeemsNonEmptyButIsNot2() {
        val a = Automaton()
        val init = a.createState()
        var s = a.createState()
        a.addTransition(init, s, 'a'.code)
        s = a.createState()
        a.setAccept(s, true)
        a.finishState()
        assertTrue(Operations.isEmpty(a))
    }
    @Test
    fun testSameLanguage1() {
        val a = Automata.makeEmptyString()
        val a2 = Automata.makeEmptyString()
        val state = a2.createState()
        a2.addTransition(0, state, 'a'.code)
        a2.finishState()
        assertTrue(AutomatonTestUtil.sameLanguage(a, Operations.removeDeadStates(a2)))
    }
    @Ignore
    @Test
    fun testRandomFinite() {
        // depends on complex random operations
    }
    @Test
    fun testMakeBinaryIntervalBasic() {
        val a = Automata.makeBinaryInterval(LuceneTestCase.newBytesRef("bar"), true, LuceneTestCase.newBytesRef("foo"), true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, intsRef("bar")))
        assertTrue(Operations.run(a, intsRef("foo")))
        assertTrue(Operations.run(a, intsRef("beep")))
        assertFalse(Operations.run(a, intsRef("baq")))
        assertTrue(Operations.run(a, intsRef("bara")))
    }
    @Test
    fun testMakeBinaryIntervalLowerBoundEmptyString() {
        var a = Automata.makeBinaryInterval(LuceneTestCase.newBytesRef(""), true, LuceneTestCase.newBytesRef("bar"), true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, intsRef("")))
        assertTrue(Operations.run(a, intsRef("a")))
        assertTrue(Operations.run(a, intsRef("bar")))
        assertFalse(Operations.run(a, intsRef("bara")))
        assertFalse(Operations.run(a, intsRef("baz")))
    a = Automata.makeBinaryInterval(LuceneTestCase.newBytesRef(""), false, LuceneTestCase.newBytesRef("bar"), true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertFalse(Operations.run(a, intsRef("")))
        assertTrue(Operations.run(a, intsRef("a")))
        assertTrue(Operations.run(a, intsRef("bar")))
        assertFalse(Operations.run(a, intsRef("bara")))
        assertFalse(Operations.run(a, intsRef("baz")))
    }
    @Test
    fun testMakeBinaryIntervalEqual() {
        val a = Automata.makeBinaryInterval(LuceneTestCase.newBytesRef("bar"), true, LuceneTestCase.newBytesRef("bar"), true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, intsRef("bar")))
        assertTrue(AutomatonTestUtil.isFinite(a))
        assertEquals(1, AutomatonTestUtil.getFiniteStrings(a).size)
    }
    @Test
    fun testMakeBinaryIntervalCommonPrefix() {
        val a = Automata.makeBinaryInterval(
            LuceneTestCase.newBytesRef("bar"), true,
            LuceneTestCase.newBytesRef("barfoo"), true
        )
        AutomatonTestUtil.assertMinimalDFA(a)
        assertFalse(Operations.run(a, intsRef("bam")))
        assertTrue(Operations.run(a, intsRef("bar")))
        assertTrue(Operations.run(a, intsRef("bara")))
        assertTrue(Operations.run(a, intsRef("barf")))
        assertTrue(Operations.run(a, intsRef("barfo")))
        assertTrue(Operations.run(a, intsRef("barfoo")))
        assertTrue(Operations.run(a, intsRef("barfonz")))
        assertFalse(Operations.run(a, intsRef("barfop")))
        assertFalse(Operations.run(a, intsRef("barfoop")))
    }
    @Ignore
    @Test
    fun testMakeBinaryExceptEmpty() {
        val a = Automata.makeNonEmptyBinary()
        AutomatonTestUtil.assertMinimalDFA(a)
        assertFalse(Operations.run(a, intsRef("")))
        assertTrue(Operations.run(a, intsRef(LuceneTestCase.randomUnicodeString(LuceneTestCase.random(), 10))))
    }
    @Test
    fun testMakeBinaryIntervalOpenMax() {
        val a = Automata.makeBinaryInterval(LuceneTestCase.newBytesRef("bar"), true, null, true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertFalse(Operations.run(a, intsRef("bam")))
        assertTrue(Operations.run(a, intsRef("bar")))
        assertTrue(Operations.run(a, intsRef("bara")))
        assertTrue(Operations.run(a, intsRef("barf")))
        assertTrue(Operations.run(a, intsRef("barfo")))
        assertTrue(Operations.run(a, intsRef("barfoo")))
        assertTrue(Operations.run(a, intsRef("barfonz")))
        assertTrue(Operations.run(a, intsRef("barfop")))
        assertTrue(Operations.run(a, intsRef("barfoop")))
        assertTrue(Operations.run(a, intsRef("zzz")))
    }
    @Test
    fun testMakeBinaryIntervalOpenMaxZeroLengthMin() {
        var a = Automata.makeBinaryInterval(LuceneTestCase.newBytesRef(""), true, null, true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, intsRef("")))
        assertTrue(Operations.run(a, intsRef("a")))
        assertTrue(Operations.run(a, intsRef("aaaaaa")))
        a = Automata.makeBinaryInterval(LuceneTestCase.newBytesRef(""), false, null, true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertFalse(Operations.run(a, intsRef("")))
        assertTrue(Operations.run(a, intsRef("a")))
        assertTrue(Operations.run(a, intsRef("aaaaaa")))
    }
    @Test
    fun testMakeBinaryIntervalOpenMin() {
        val a = Automata.makeBinaryInterval(null, true, LuceneTestCase.newBytesRef("foo"), true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertFalse(Operations.run(a, intsRef("foz")))
        assertFalse(Operations.run(a, intsRef("zzz")))
        assertTrue(Operations.run(a, intsRef("foo")))
        assertTrue(Operations.run(a, intsRef("")))
        assertTrue(Operations.run(a, intsRef("a")))
        assertTrue(Operations.run(a, intsRef("aaa")))
        assertTrue(Operations.run(a, intsRef("bz")))
    }
    @Test
    fun testMakeBinaryIntervalOpenBoth() {
        val a = Automata.makeBinaryInterval(null, true, null, true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, intsRef("foz")))
        assertTrue(Operations.run(a, intsRef("zzz")))
        assertTrue(Operations.run(a, intsRef("foo")))
        assertTrue(Operations.run(a, intsRef("")))
        assertTrue(Operations.run(a, intsRef("a")))
        assertTrue(Operations.run(a, intsRef("aaa")))
        assertTrue(Operations.run(a, intsRef("bz")))
    }
    @Test
    fun testAcceptAllEmptyStringMin() {
        val a = Automata.makeBinaryInterval(LuceneTestCase.newBytesRef(), true, null, true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(AutomatonTestUtil.sameLanguage(Automata.makeAnyBinary(), a))
    }
    private fun toIntsRef(s: String): IntsRef {
        val b = IntsRefBuilder()
        var i = 0
        var cp: Int
        while (i < s.length) {
            cp = Character.codePointAt(s, i)
            b.append(cp)
            i += Character.charCount(cp)
        }
        return b.toIntsRef()
    }
    @Ignore
    @Test
    fun testGetSingleton() {
        val iters = LuceneTestCase.atLeast(10000)
        for (iter in 0 until iters) {
            val s = LuceneTestCase.randomUnicodeString(LuceneTestCase.random())
            val a = Automata.makeString(s)
            assertEquals(toIntsRef(s), Operations.getSingleton(a))
        }
    }
    @Ignore
    @Test
    fun testGetSingletonEmptyString() {
        val a = Automaton()
        val s = a.createState()
        a.setAccept(s, true)
        a.finishState()
        assertEquals(IntsRef(), Operations.getSingleton(a))
    }
    @Ignore
    @Test
    fun testGetSingletonNothing() {
        val a = Automaton()
        a.createState()
        a.finishState()
        assertNull(Operations.getSingleton(a))
    }
    @Ignore
    @Test
    fun testGetSingletonTwo() {
        val a = Automaton()
        val s = a.createState()
        val x = a.createState()
        a.setAccept(x, true)
        a.addTransition(s, x, 55)
        val y = a.createState()
        a.setAccept(y, true)
        a.addTransition(s, y, 58)
        a.finishState()
        assertNull(Operations.getSingleton(a))
    }
    @Ignore
    @Test
    fun testDeterminizeTooMuchEffort() {
        assertFailsWith<TooComplexToDeterminizeException> {
            val a = RegExp("(.*a){2000}").toAutomaton()!!
            Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        }
        assertFailsWith<TooComplexToDeterminizeException> {
            var a = RegExp("(.*a){2000}").toAutomaton()!!
            a = Operations.reverse(a)
            Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        }
    }
    @Test
    fun testMakeCharSetEmpty() {
        val expected = Automata.makeEmpty()
        val actual = Automata.makeCharSet(intArrayOf())
        AutomatonTestUtil.assertMinimalDFA(actual)
        assertTrue(AutomatonTestUtil.sameLanguage(expected, actual))
    }
    @Test
    fun testMakeCharSetOne() {
        val expected = Automata.makeChar('a'.code)
        val actual = Automata.makeCharSet(intArrayOf('a'.code))
        AutomatonTestUtil.assertMinimalDFA(actual)
        assertTrue(AutomatonTestUtil.sameLanguage(expected, actual))
    }
    @Test
    fun testMakeCharSetTwo() {
        val expected = Operations.union(mutableListOf(Automata.makeChar('a'.code), Automata.makeChar('A'.code)))
        val actual = Automata.makeCharSet(intArrayOf('a'.code, 'A'.code))
        AutomatonTestUtil.assertMinimalDFA(actual)
        assertTrue(AutomatonTestUtil.sameLanguage(expected, actual))
    }
    @Test
    fun testMakeCharSetDups() {
        val expected = Automata.makeChar('a'.code)
        val actual = Automata.makeCharSet(intArrayOf('a'.code, 'a'.code, 'a'.code))
        AutomatonTestUtil.assertMinimalDFA(actual)
        assertTrue(AutomatonTestUtil.sameLanguage(expected, actual))
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
    private fun intsRef(s: String): IntsRef {
    val builder = IntsRefBuilder()
    Util.toIntsRef(LuceneTestCase.newBytesRef(s), builder)
    return builder.toIntsRef()
}
    }

}
