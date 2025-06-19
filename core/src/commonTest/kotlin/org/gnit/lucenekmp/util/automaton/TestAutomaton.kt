package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.fst.Util
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TestAutomaton : LuceneTestCase() {

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

    @Test
    fun testConcatenate2() {
        var a: Automaton =
            Operations.concatenate(
                mutableListOf(
                    Automata.makeString("m"),
                    Automata.makeAnyString(),
                    Automata.makeString("n"),
                    Automata.makeAnyString()
                )
            )
        AutomatonTestUtil.assertCleanNFA(a)
        a = Operations.determinize(
            a,
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
        )
        assertTrue(Operations.run(a, "mn"))
        assertTrue(Operations.run(a, "mone"))
        assertFalse(Operations.run(a, "m"))
        assertFalse(AutomatonTestUtil.isFinite(a))
    }

    @Test
    fun testUnion1() {
        val a = Operations.union(mutableListOf(Automata.makeString("foobar"), Automata.makeString("barbaz")))
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, "foobar"))
        assertTrue(Operations.run(a, "barbaz"))
        AutomatonTestUtil.assertMatches(a, "foobar", "barbaz")
    }

    @Test
    fun testUnion2() {
        val a = Operations.union(
            mutableListOf(
                Automata.makeString("foobar"),
                Automata.makeString(""),
                Automata.makeString("barbaz")
            )
        )
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, "foobar"))
        assertTrue(Operations.run(a, "barbaz"))
        assertTrue(Operations.run(a, ""))
        AutomatonTestUtil.assertMatches(a, "", "foobar", "barbaz")
    }

    @Test
    fun testMinimizeSimple() {
        val a = Automata.makeString("foobar")
        val aMin = MinimizationOperations.minimize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        assertTrue(AutomatonTestUtil.sameLanguage(a, aMin))
    }

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
        assertEquals(newBytesRef(), Operations.getCommonSuffixBytesRef(Automata.makeEmpty()))
    }

    @Test
    fun testCommonSuffixEmptyString() {
        assertEquals(newBytesRef(), Operations.getCommonSuffixBytesRef(Automata.makeEmptyString()))
    }

    @Test
    fun testCommonSuffixTrailingWildcard() {
        val a = Operations.concatenate(mutableListOf(Automata.makeString("boo"), Automata.makeAnyChar()))
        AutomatonTestUtil.assertMinimalDFA(a)
        assertEquals(newBytesRef(), Operations.getCommonSuffixBytesRef(a))
    }

    @Test
    fun testCommonSuffixLeadingKleenStar() {
        val a = Operations.concatenate(mutableListOf(Automata.makeAnyString(), Automata.makeString("boo")))
        AutomatonTestUtil.assertCleanNFA(a)
        assertEquals(newBytesRef("boo"), Operations.getCommonSuffixBytesRef(a))
    }

    @Test
    fun testCommonSuffixTrailingKleenStar() {
        val a = Operations.concatenate(mutableListOf(Automata.makeString("boo"), Automata.makeAnyString()))
        AutomatonTestUtil.assertCleanDFA(a)
        assertEquals(newBytesRef(), Operations.getCommonSuffixBytesRef(a))
    }

    @Test
    fun testCommonSuffixUnicode() {
        val a = Operations.concatenate(mutableListOf(Automata.makeAnyString(), Automata.makeString("boo😂😂😂")))
        AutomatonTestUtil.assertCleanNFA(a)
        val binary = UTF32ToUTF8().convert(a)
        assertEquals(newBytesRef("boo😂😂😂"), Operations.getCommonSuffixBytesRef(binary))
    }

    @Test
    fun testReverseRandom1() {
        val ITERS: Int = atLeast(1) // TODO originally 100, but reduced to 1 for dev speed
        for (i in 0..<ITERS) {
            // NOTE: original was AutomatonTestUtil.randomAutomaton(random()). This is slow, so for
            // local dev, we use a simple random string instead.
            val a = Automata.makeString(TestUtil.randomUnicodeString(random()))
            val ra = Operations.reverse(a)
            val rra = Operations.reverse(ra)
            assertTrue(
                AutomatonTestUtil.sameLanguage(
                    Operations.determinize(Operations.removeDeadStates(a), Int.MAX_VALUE),
                    Operations.determinize(Operations.removeDeadStates(rra), Int.MAX_VALUE)
                )
            )
        }
    }

    @Test
    fun testReverseRandom2() {
        val ITERS = atLeast(1) // TODO originally 100, but reduced to 1 for dev speed
        for (iter in 0 until ITERS) {
            // NOTE: original was AutomatonTestUtil.randomAutomaton(random()). This is slow, so for
            // local dev, we use a simple random string instead.
            var a = Automata.makeString(TestUtil.randomUnicodeString(random()))
            if (random().nextBoolean()) {
                a = Operations.removeDeadStates(a)
            }
            val ra = Operations.reverse(a)
            val rda = Operations.determinize(ra, Int.MAX_VALUE)

            if (Operations.isEmpty(a)) {
                assertTrue(Operations.isEmpty(rda))
                continue
            }

            val ras = AutomatonTestUtil.RandomAcceptedStrings(a)

            for (iter2 in 0 until 20) {
                // Find string accepted by original automaton
                val s = ras.getRandomAcceptedString(random())

                // Reverse it
                s.reverse()

                // Make sure reversed automaton accepts it
                assertTrue(Operations.run(rda, IntsRef(s, 0, s.size)))
            }
        }
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

    @Test
    fun testBuilderRandom() {
        val ITERS = atLeast(1) // TODO originally 100, but reduced to 1 for dev speed
        for (iter in 0 until ITERS) {
            // NOTE: original was AutomatonTestUtil.randomAutomaton(random()). This is slow, so for
            // local dev, we use a simple random string instead.
            val a = Automata.makeString(TestUtil.randomUnicodeString(random()))

            // Just get all transitions, shuffle, and build a new automaton with the same transitions:
            val allTrans = mutableListOf<Transition>()
            val numStates = a.numStates
            for (s in 0 until numStates) {
                val count = a.getNumTransitions(s)
                for (i in 0 until count) {
                    val t = Transition()
                    a.getTransition(s, i, t)
                    allTrans.add(t)
                }
            }

            val builder = Automaton.Builder()
            for (i in 0 until numStates) {
                val s = builder.createState()
                builder.setAccept(s, a.isAccept(s))
            }

            allTrans.shuffle(random())
            for (t in allTrans) {
                builder.addTransition(t.source, t.dest, t.min, t.max)
            }

            assertTrue(
                AutomatonTestUtil.sameLanguage(
                    Operations.determinize(Operations.removeDeadStates(a), Int.MAX_VALUE),
                    Operations.determinize(
                        Operations.removeDeadStates(builder.finish()),
                        Int.MAX_VALUE
                    )
                )
            )
        }
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

    private fun assertMatches(a: Automaton, vararg strings: String) {
        val expected = mutableSetOf<IntsRef>()
        for (s in strings) {
            val ints = IntsRefBuilder()
            Util.toUTF32(s, ints)
            expected.add(ints.toIntsRef())
        }
        assertEquals(expected, TestOperations.getFiniteStrings(a))
    }

    @Test
    fun testMinus() {
        val a1 = Automata.makeString("foobar")
        val a2 = Automata.makeString("boobar")
        val a3 = Automata.makeString("beebar")
        val terms = listOf(BytesRef("foobar"), BytesRef("boobar"), BytesRef("beebar")).sorted()
        val a = Automata.makeStringUnion(terms)
        AutomatonTestUtil.assertCleanNFA(a)
        assertMatches(a, "foobar", "beebar", "boobar")
        var a4 = Operations.minus(a, a2, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        AutomatonTestUtil.assertCleanDFA(a4)
        assertTrue(Operations.run(a4, "foobar"))
        assertFalse(Operations.run(a4, "boobar"))
        assertTrue(Operations.run(a4, "beebar"))
        assertMatches(a4, "foobar", "beebar")
        a4 = Operations.minus(a4, a1, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        AutomatonTestUtil.assertCleanDFA(a4)
        assertFalse(Operations.run(a4, "foobar"))
        assertFalse(Operations.run(a4, "boobar"))
        assertTrue(Operations.run(a4, "beebar"))
        assertMatches(a4, "beebar")
        a4 = Operations.minus(a4, a3, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        AutomatonTestUtil.assertCleanDFA(a4)
        assertFalse(Operations.run(a4, "foobar"))
        assertFalse(Operations.run(a4, "boobar"))
        assertFalse(Operations.run(a4, "beebar"))
        assertMatches(a4)
    }

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

    @Test
    fun testIntervalRandom() {
        val iters = atLeast(100)
        for (iter in 0 until iters) {
            val min = TestUtil.nextInt(random(), 0, 100000)
            val max = TestUtil.nextInt(random(), min, min + 100000)
            val digits = if (random().nextBoolean()) {
                0
            } else {
                val s = max.toString()
                TestUtil.nextInt(random(), s.length, 2 * s.length)
            }
            val b = StringBuilder()
            for (i in 0 until digits) {
                b.append('0')
            }
            val prefix = b.toString()

            var a = Operations.determinize(
                Automata.makeDecimalInterval(min, max, digits),
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
            )
            if (random().nextBoolean()) {
                a = MinimizationOperations.minimize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
            }
            var mins = min.toString()
            var maxs = max.toString()
            if (digits > 0) {
                mins = prefix.substring(mins.length) + mins
                maxs = prefix.substring(maxs.length) + maxs
            }
            assertTrue(Operations.run(a, mins))
            assertTrue(Operations.run(a, maxs))

            for (iter2 in 0 until 100) {
                val x = random().nextInt(2 * max)
                val expected = x >= min && x <= max
                var sx = x.toString()
                if (sx.length < digits) {
                    // Left-fill with 0s
                    sx = b.substring(sx.length) + sx
                } else if (digits == 0) {
                    // Left-fill with random number of 0s:
                    val numZeros = random().nextInt(10)
                    val sb = StringBuilder()
                    for (i in 0 until numZeros) {
                        sb.append('0')
                    }
                    sb.append(sx)
                    sx = sb.toString()
                }
                assertEquals(expected, Operations.run(a, sx))
            }
        }
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
        assertEquals(emptySet(), TestOperations.getFiniteStrings(a))
        a = Operations.concatenate(mutableListOf(Automata.makeString("foo"), Automata.makeEmpty()))
        AutomatonTestUtil.assertMinimalDFA(a)
        assertEquals(emptySet(), TestOperations.getFiniteStrings(a))
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

    private fun randomNoOp(a: Automaton): Automaton? {
        var a: Automaton = a
        when (random().nextInt(7)) {
            0 -> {
                if (VERBOSE) {
                    println("  randomNoOp: determinize")
                }
                return Operations.determinize(a, Int.Companion.MAX_VALUE)
            }

            1 -> if (a.numStates < 100) {
                if (VERBOSE) {
                    println("  randomNoOp: minimize")
                }
                return MinimizationOperations.minimize(
                    a,
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                )
            } else {
                if (VERBOSE) {
                    println(
                        "  randomNoOp: skip op=minimize: too many states (" + a.numStates + ")"
                    )
                }
                return a
            }

            2 -> {
                if (VERBOSE) {
                    println("  randomNoOp: removeDeadStates")
                }
                return Operations.removeDeadStates(a)
            }

            3 -> {
                if (VERBOSE) {
                    println("  randomNoOp: reverse reverse")
                }
                a = Operations.reverse(a)
                a = randomNoOp(a)!!
                return Operations.reverse(a)
            }

            4 -> {
                if (VERBOSE) {
                    println("  randomNoOp: concat empty string")
                }
                return Operations.concatenate(
                    mutableListOf(
                        a,
                        Automata.makeEmptyString()
                    )
                )
            }

            5 -> {
                if (VERBOSE) {
                    println("  randomNoOp: union empty automaton")
                }
                return Operations.union(
                    mutableListOf(
                        a,
                        Automata.makeEmpty()
                    )
                )
            }

            6 -> {
                if (VERBOSE) {
                    println("  randomNoOp: do nothing!")
                }
                return a
            }
        }
        assert(false)
        return null
    }

    private fun hasMassiveTerm(terms: MutableCollection<BytesRef>): Boolean {
        for (term in terms) {
            if (term.length > Automata.MAX_STRING_UNION_TERM_LENGTH) {
                return true
            }
        }
        return false
    }

    private fun unionTerms(terms: MutableCollection<BytesRef>): Automaton? {
        val a: Automaton
        if (random()
                .nextBoolean() || hasMassiveTerm(terms)
        ) {
            if (VERBOSE) {
                println("TEST: unionTerms: use union")
            }
            val `as`: MutableList<Automaton> =
                mutableListOf()
            for (term in terms) {
                `as`.add(Automata.makeString(term.utf8ToString()))
            }
            a = Operations.union(`as`)
        } else {
            if (VERBOSE) {
                println("TEST: unionTerms: use makeStringUnion")
            }
            val termsList: MutableList<BytesRef> = ArrayList(terms)
            termsList.sort()
            a = Automata.makeStringUnion(termsList)
        }

        return randomNoOp(a)
    }

    private fun getRandomString(): String {
        // return TestUtil.randomSimpleString(random());
        return TestUtil.randomRealisticUnicodeString(random())
    }

    @Test
    fun testRandomFinite() {

        val numTerms: Int = atLeast(10)
        val iters: Int = atLeast(10) // TODO originally 100, but reduced to 10 for dev speed

        if (VERBOSE) {
            println("TEST: numTerms=$numTerms iters=$iters")
        }

        var terms: MutableSet<BytesRef> = HashSet()
        while (terms.size < numTerms) {
            terms.add(newBytesRef(getRandomString()))
        }

        var a: Automaton = unionTerms(terms)!!
        assertSame(terms, a)

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println(
                    ("TEST: iter="
                            + iter
                            + " numTerms="
                            + terms.size
                            + " a.numStates="
                            + a.numStates)
                )
                /*
        System.out.println("  terms:");
        for(BytesRef term : terms) {
          System.out.println("    " + term);
        }
        */
            }
            when (random().nextInt(15)) {
                0 ->           // concatenate prefix
                {
                    if (VERBOSE) {
                        println("  op=concat prefix")
                    }
                    val newTerms: MutableSet<BytesRef> =
                        HashSet()
                    val prefix: BytesRef =
                        newBytesRef(getRandomString())
                    val newTerm = BytesRefBuilder()
                    for (term in terms) {
                        newTerm.copyBytes(prefix)
                        newTerm.append(term)
                        newTerms.add(newTerm.toBytesRef())
                    }
                    terms = newTerms
                    val wasDeterministic: Boolean = a.isDeterministic
                    a = Operations.concatenate(
                        mutableListOf(
                            Automata.makeString(
                                prefix.utf8ToString()
                            ),
                            a
                        )
                    )
                    if (wasDeterministic) {
                        assertEquals(wasDeterministic, a.isDeterministic)
                    }
                }

                1 ->           // concatenate suffix
                {
                    val suffix: BytesRef =
                        newBytesRef(getRandomString())
                    if (VERBOSE) {
                        println("  op=concat suffix $suffix")
                    }
                    val newTerms: MutableSet<BytesRef> =
                        HashSet()
                    val newTerm = BytesRefBuilder()
                    for (term in terms) {
                        newTerm.copyBytes(term)
                        newTerm.append(suffix)
                        newTerms.add(newTerm.toBytesRef())
                    }
                    terms = newTerms
                    a = Operations.concatenate(
                        mutableListOf(
                            a,
                            Automata.makeString(suffix.utf8ToString())
                        )
                    )
                }

                2 -> {
                    // determinize
                    if (VERBOSE) {
                        println("  op=determinize")
                    }
                    a = Operations.determinize(a, Int.Companion.MAX_VALUE)
                    assertTrue(a.isDeterministic)
                }

                3 -> if (a.numStates < 100) {
                    if (VERBOSE) {
                        println("  op=minimize")
                    }
                    // minimize
                    a = MinimizationOperations.minimize(
                        a,
                        Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                    )
                } else if (VERBOSE) {
                    println("  skip op=minimize: too many states (" + a.numStates + ")")
                }

                4 ->           // union
                {
                    if (VERBOSE) {
                        println("  op=union")
                    }
                    val newTerms: MutableSet<BytesRef> =
                        HashSet()
                    val numNewTerms: Int = random().nextInt(5)
                    while (newTerms.size < numNewTerms) {
                        newTerms.add(newBytesRef(getRandomString()))
                    }
                    terms.addAll(newTerms)
                    val newA: Automaton = unionTerms(newTerms)!!
                    a = Operations.union(
                        mutableListOf(
                            a,
                            newA
                        )
                    )
                }

                5 ->           // optional
                {
                    if (VERBOSE) {
                        println("  op=optional")
                    }
                    // NOTE: This can add a dead state:
                    a = Operations.optional(a)
                    terms.add(newBytesRef())
                }

                6 ->           // minus finite
                {
                    if (VERBOSE) {
                        println("  op=minus finite")
                    }
                    if (terms.isNotEmpty()) {
                        val rasl: AutomatonTestUtil.RandomAcceptedStrings =
                            AutomatonTestUtil.RandomAcceptedStrings(
                                Operations.removeDeadStates(
                                    a
                                )
                            )
                        val toRemove: MutableSet<BytesRef> =
                            HashSet()
                        val numToRemove: Int = TestUtil.nextInt(
                            random(),
                            1,
                            (terms.size + 1) / 2
                        )
                        while (toRemove.size < numToRemove) {
                            val ints: IntArray =
                                rasl.getRandomAcceptedString(random())
                            val term: BytesRef =
                                newBytesRef(
                                    UnicodeUtil.newString(
                                        ints,
                                        0,
                                        ints.size
                                    )
                                )
                            if (toRemove.contains(term) == false) {
                                toRemove.add(term)
                            }
                        }
                        for (term in toRemove) {
                            val removed = terms.remove(term)
                            assertTrue(removed)
                        }
                        val a2: Automaton = unionTerms(toRemove)!!
                        a = Operations.minus(a, a2, Int.Companion.MAX_VALUE)
                    }
                }

                7 -> {
                    // minus infinite
                    val `as`: MutableList<Automaton> = mutableListOf()
                    val count: Int = TestUtil.nextInt(random(), 1, 5)
                    val prefixes: MutableSet<Int> = HashSet()
                    while (prefixes.size < count) {
                        // prefix is a leading ascii byte; we remove <prefix>* from a
                        val prefix: Int = random().nextInt(128)
                        prefixes.add(prefix)
                    }

                    if (VERBOSE) {
                        println("  op=minus infinite prefixes=$prefixes")
                    }

                    for (prefix in prefixes) {
                        // prefix is a leading ascii byte; we remove <prefix>* from a
                        val a2 = Automaton()
                        val init: Int = a2.createState()
                        val state: Int = a2.createState()
                        a2.addTransition(init, state, prefix)
                        a2.setAccept(state, true)
                        a2.addTransition(
                            state,
                            state,
                            Character.MIN_CODE_POINT,
                            Character.MAX_CODE_POINT
                        )
                        a2.finishState()
                        `as`.add(a2)
                        val it: MutableIterator<BytesRef> = terms.iterator()
                        while (it.hasNext()) {
                            val term: BytesRef = it.next()
                            if (term.length > 0 && (term.bytes[term.offset].toInt() and 0xFF) == prefix) {
                                it.remove()
                            }
                        }
                    }
                    val a2: Automaton =
                        randomNoOp(Operations.union(`as`))!!
                    a = Operations.minus(
                        a,
                        a2,
                        Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                    )
                }

                8 -> {
                    val count: Int = TestUtil.nextInt(
                        random(),
                        10,
                        20
                    )
                    if (VERBOSE) {
                        println("  op=intersect infinite count=$count")
                    }
                    // intersect infinite
                    val `as`: MutableList<Automaton> =
                        mutableListOf()

                    val prefixes: MutableSet<Int> = HashSet()
                    while (prefixes.size < count) {
                        val prefix: Int = random().nextInt(128)
                        prefixes.add(prefix)
                    }
                    if (VERBOSE) {
                        println("  prefixes=$prefixes")
                    }

                    for (prefix in prefixes) {
                        // prefix is a leading ascii byte; we retain <prefix>* in a
                        val a2 = Automaton()
                        val init: Int = a2.createState()
                        val state: Int = a2.createState()
                        a2.addTransition(init, state, prefix)
                        a2.setAccept(state, true)
                        a2.addTransition(
                            state,
                            state,
                            Character.MIN_CODE_POINT,
                            Character.MAX_CODE_POINT
                        )
                        a2.finishState()
                        `as`.add(a2)
                        prefixes.add(prefix)
                    }

                    var a2: Automaton =
                        Operations.union(`as`)
                    if (random().nextBoolean()) {
                        a2 = Operations.determinize(
                            a2,
                            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                        )
                    } else if (random().nextBoolean()) {
                        a2 = MinimizationOperations.minimize(
                            a2,
                            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                        )
                    }
                    a = Operations.intersection(a, a2)

                    val it: MutableIterator<BytesRef> = terms.iterator()
                    while (it.hasNext()) {
                        val term: BytesRef = it.next()
                        if (term.length == 0 || prefixes.contains(term.bytes[term.offset].toInt() and 0xff) == false) {
                            if (VERBOSE) {
                                println("  drop term=$term")
                            }
                            it.remove()
                        } else {
                            if (VERBOSE) {
                                println("  keep term=$term")
                            }
                        }
                    }
                }

                9 ->           // reverse
                {
                    if (VERBOSE) {
                        println("  op=reverse")
                    }
                    a = Operations.reverse(a)
                    val newTerms: MutableSet<BytesRef> =
                        HashSet()
                    for (term in terms) {
                        newTerms.add(
                            newBytesRef(
                                StringBuilder(term.utf8ToString()).reverse().toString()
                            )
                        )
                    }
                    terms = newTerms
                }

                10 -> {
                    if (VERBOSE) {
                        println("  op=randomNoOp")
                    }
                    a = randomNoOp(a)!!
                }

                11 ->           // interval
                {
                    val min: Int = random().nextInt(1000)
                    val max: Int = min + random().nextInt(50)
                    // digits must be non-zero else we make cycle
                    val digits = max.toString().length
                    if (VERBOSE) {
                        println(
                            "  op=union interval min=$min max=$max digits=$digits"
                        )
                    }
                    a = Operations.union(
                        mutableListOf(a, Automata.makeDecimalInterval(min, max, digits))
                    )
                    val b = StringBuilder()
                    run {
                        var i = 0
                        while (i < digits) {
                            b.append('0')
                            i++
                        }
                    }
                    val prefix = b.toString()
                    var i = min
                    while (i <= max) {
                        var s = i.toString()
                        if (s.length < digits) {
                            // Left-fill with 0s
                            s = prefix.substring(s.length) + s
                        }
                        terms.add(newBytesRef(s))
                        i++
                    }
                }

                12 -> {
                    if (VERBOSE) {
                        println("  op=remove the empty string")
                    }
                    a = Operations.minus(
                        a,
                        Automata.makeEmptyString(),
                        Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                    )
                    terms.remove(newBytesRef())
                }

                13 -> {
                    if (VERBOSE) {
                        println("  op=add the empty string")
                    }
                    a = Operations.union(
                        mutableListOf(
                            a,
                            Automata.makeEmptyString()
                        )
                    )
                    terms.add(newBytesRef())
                }

                14 ->           // Safety in case we are really unlucky w/ the dice:
                    if (terms.size <= numTerms * 3) {
                        if (VERBOSE) {
                            println("  op=concat finite automaton")
                        }
                        val count = if (random().nextBoolean()) 2 else 3
                        val addTerms: MutableSet<BytesRef> =
                            HashSet()
                        while (addTerms.size < count) {
                            addTerms.add(newBytesRef(getRandomString()))
                        }
                        if (VERBOSE) {
                            for (term in addTerms) {
                                println("    term=$term")
                            }
                        }
                        val a2: Automaton = unionTerms(addTerms)!!
                        val newTerms: MutableSet<BytesRef> =
                            HashSet()
                        if (random().nextBoolean()) {
                            // suffix
                            if (VERBOSE) {
                                println("  do suffix")
                            }
                            a = Operations.concatenate(
                                mutableListOf(a, randomNoOp(a2)!!)
                            )
                            val newTerm = BytesRefBuilder()
                            for (term in terms) {
                                for (suffix in addTerms) {
                                    newTerm.copyBytes(term)
                                    newTerm.append(suffix)
                                    newTerms.add(newTerm.toBytesRef())
                                }
                            }
                        } else {
                            // prefix
                            if (VERBOSE) {
                                println("  do prefix")
                            }
                            a = Operations.concatenate(
                                mutableListOf(
                                    randomNoOp(a2)!!,
                                    a
                                )
                            )
                            val newTerm =
                                BytesRefBuilder()
                            for (term in terms) {
                                for (prefix in addTerms) {
                                    newTerm.copyBytes(prefix)
                                    newTerm.append(term)
                                    newTerms.add(newTerm.toBytesRef())
                                }
                            }
                        }

                        terms = newTerms
                    }

                else -> throw AssertionError()
            }

            assertSame(terms, a)
            assertEquals(
                AutomatonTestUtil.isDeterministicSlow(a),
                a.isDeterministic
            )

            if (random().nextInt(10) == 7) {
                a = verifyTopoSort(a)
            }
        }

        assertSame(terms, a)
    }

    /**
     * Runs topo sort, verifies transitions then only "go forwards", and builds and returns new
     * automaton with those remapped toposorted states.
     */
    private fun verifyTopoSort(a: Automaton): Automaton {
        val sorted: IntArray = Operations.topoSortStates(a)
        // This can be < if we removed dead states:
        assertTrue(sorted.size <= a.numStates)
        val a2 = Automaton()
        val stateMap = IntArray(a.numStates)
        Arrays.fill(stateMap, -1)
        val transition = Transition()
        for (state in sorted) {
            val newState: Int = a2.createState()
            a2.setAccept(newState, a.isAccept(state))

            // Each state should only appear once in the sort:
            assertEquals(-1, stateMap[state].toLong())
            stateMap[state] = newState
        }

        // 2nd pass: add new transitions
        for (state in sorted) {
            val count: Int = a.initTransition(state, transition)
            for (i in 0..<count) {
                a.getNextTransition(transition)
                assert(stateMap[transition.dest] > stateMap[state])
                a2.addTransition(
                    stateMap[state], stateMap[transition.dest], transition.min, transition.max
                )
            }
        }

        a2.finishState()
        return a2
    }

    private fun assertSame(
        terms: MutableCollection<BytesRef>,
        a: Automaton
    ) {
        try {
            assertTrue(AutomatonTestUtil.isFinite(a))
            assertFalse(Operations.isTotal(a))

            val detA: Automaton =
                Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)

            // Make sure all terms are accepted:
            val scratch = IntsRefBuilder()
            for (term in terms) {
                Util.toIntsRef(term, scratch)
                assertTrue(
                    Operations.run(detA, term.utf8ToString()), "failed to accept term=" + term.utf8ToString()
                )
            }

            // Use getFiniteStrings:
            val expected: MutableSet<IntsRef?> = HashSet()
            for (term in terms) {
                val intsRef = IntsRefBuilder()
                Util.toUTF32(term.utf8ToString(), intsRef)
                expected.add(intsRef.toIntsRef())
            }
            val actual: MutableSet<IntsRef> =
                TestOperations.getFiniteStrings(a)

            if (expected == actual == false) {
                println("FAILED:")
                for (term in expected) {
                    if (actual.contains(term) == false) {
                        println("  term=$term should be accepted but isn't")
                    }
                }
                for (term in actual) {
                    if (expected.contains(term) == false) {
                        println("  term=$term is accepted but should not be")
                    }
                }
                throw AssertionError("mismatch")
            }

            // Use sameLanguage:
            val a2: Automaton =
                Operations.removeDeadStates(
                    Operations.determinize(
                        unionTerms(terms)!!,
                        Int.Companion.MAX_VALUE
                    )
                )
            assertTrue(
                AutomatonTestUtil.sameLanguage(
                    a2,
                    Operations.removeDeadStates(
                        Operations.determinize(
                            a,
                            Int.Companion.MAX_VALUE
                        )
                    )
                )
            )

            // Do same check, in UTF8 space
            val utf8: Automaton =
                randomNoOp(UTF32ToUTF8().convert(a))!!

            val expected2: MutableSet<IntsRef> = HashSet()
            for (term in terms) {
                val intsRef = IntsRefBuilder()
                Util.toIntsRef(term, intsRef)
                expected2.add(intsRef.toIntsRef())
            }
            assertEquals(
                expected2,
                TestOperations.getFiniteStrings(utf8)
            )
        } catch (ae: AssertionError) {
            println("TEST: FAILED: not same")
            println("  terms (count=" + terms.size + "):")
            for (term in terms) {
                println("    $term")
            }
            println("  automaton:")
            println(a.toDot())
            // a.writeDot("fail");
            throw ae
        }
    }

    private fun accepts(a: Automaton, b: BytesRef): Boolean {
        val intsBuilder = IntsRefBuilder()
        Util.toIntsRef(b, intsBuilder)
        return Operations.run(a, intsBuilder.toIntsRef())
    }

    private fun makeBinaryInterval(
        minTerm: BytesRef?,
        minInclusive: Boolean,
        maxTerm: BytesRef?,
        maxInclusive: Boolean
    ): Automaton {
        if (VERBOSE) {
            println(
                ("TEST: minTerm="
                        + minTerm
                        + " minInclusive="
                        + minInclusive
                        + " maxTerm="
                        + maxTerm
                        + " maxInclusive="
                        + maxInclusive)
            )
        }

        val a: Automaton =
            Automata.makeBinaryInterval(
                minTerm, minInclusive,
                maxTerm, maxInclusive
            )

        val minA: Automaton =
            MinimizationOperations.minimize(a, Int.Companion.MAX_VALUE)
        if (minA.numStates != a.numStates) {
            assertTrue(minA.numStates < a.numStates)
            println("Original was not minimal:")
            println("Original:\n" + a.toDot())
            println("Minimized:\n" + minA.toDot())
            println("minTerm=$minTerm minInclusive=$minInclusive")
            println("maxTerm=$maxTerm maxInclusive=$maxInclusive")
            fail("automaton was not minimal")
        }

        if (VERBOSE) {
            println(a.toDot())
        }

        return a
    }

    @Test
    fun testMakeBinaryIntervalFiniteCasesBasic() {
        val zeros = ByteArray(3)
        var a = makeBinaryInterval(
            newBytesRef(zeros, 0, 1), true,
            newBytesRef(zeros, 0, 2), true
        )
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(AutomatonTestUtil.isFinite(a))
        assertFalse(accepts(a, newBytesRef()))
        assertTrue(accepts(a, newBytesRef(zeros, 0, 1)))
        assertTrue(accepts(a, newBytesRef(zeros, 0, 2)))
        assertFalse(accepts(a, newBytesRef(zeros, 0, 3)))

        a = makeBinaryInterval(
            newBytesRef(), true,
            newBytesRef(zeros, 0, 2), true
        )
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(AutomatonTestUtil.isFinite(a))
        assertTrue(accepts(a, newBytesRef()))
        assertTrue(accepts(a, newBytesRef(zeros, 0, 1)))
        assertTrue(accepts(a, newBytesRef(zeros, 0, 2)))
        assertFalse(accepts(a, newBytesRef(zeros, 0, 3)))

        a = makeBinaryInterval(
            newBytesRef(), false,
            newBytesRef(zeros, 0, 2), true
        )
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(AutomatonTestUtil.isFinite(a))
        assertFalse(accepts(a, newBytesRef()))
        assertTrue(accepts(a, newBytesRef(zeros, 0, 1)))
        assertTrue(accepts(a, newBytesRef(zeros, 0, 2)))
        assertFalse(accepts(a, newBytesRef(zeros, 0, 3)))

        a = makeBinaryInterval(
            newBytesRef(zeros, 0, 1), false,
            newBytesRef(zeros, 0, 2), true
        )
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(AutomatonTestUtil.isFinite(a))
        assertFalse(accepts(a, newBytesRef()))
        assertFalse(accepts(a, newBytesRef(zeros, 0, 1)))
        assertTrue(accepts(a, newBytesRef(zeros, 0, 2)))
        assertFalse(accepts(a, newBytesRef(zeros, 0, 3)))

        a = makeBinaryInterval(
            newBytesRef(zeros, 0, 1), false,
            newBytesRef(zeros, 0, 2), false
        )
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(AutomatonTestUtil.isFinite(a))
        assertFalse(accepts(a, newBytesRef()))
        assertFalse(accepts(a, newBytesRef(zeros, 0, 1)))
        assertFalse(accepts(a, newBytesRef(zeros, 0, 2)))
        assertFalse(accepts(a, newBytesRef(zeros, 0, 3)))
    }

    @Test
    fun testMakeBinaryIntervalFiniteCasesRandom() {
        val random = random()
        val iters = atLeast(100)
        for (iter in 0 until iters) {
            val prefix = newBytesRef(TestUtil.randomUnicodeString(random))
            var b = BytesRefBuilder()
            b.append(prefix)
            var numZeros = random.nextInt(10)
            repeat(numZeros) { b.append(0) }
            val minTerm = b.toBytesRef()

            b = BytesRefBuilder()
            b.append(minTerm)
            numZeros = random.nextInt(10)
            repeat(numZeros) { b.append(0) }
            val maxTerm = b.toBytesRef()

            val minInclusive = random.nextBoolean()
            val maxInclusive = random.nextBoolean()
            val a = makeBinaryInterval(minTerm, minInclusive, maxTerm, maxInclusive)
            assertTrue(AutomatonTestUtil.isFinite(a))
            var expectedCount = maxTerm.length - minTerm.length + 1
            if (!minInclusive) expectedCount--
            if (!maxInclusive) expectedCount--
            if (expectedCount <= 0) {
                assertTrue(Operations.isEmpty(a))
                continue
            } else {
                assertEquals(expectedCount, TestOperations.getFiniteStrings(a).size)
            }

            b = BytesRefBuilder()
            b.append(minTerm)
            if (!minInclusive) {
                assertFalse(accepts(a, b.toBytesRef()))
                b.append(0)
            }
            while (b.length() < maxTerm.length) {
                b.append(0)
                val expected = if (b.length() == maxTerm.length) maxInclusive else true
                assertEquals(expected, accepts(a, b.toBytesRef()))
            }
        }
    }

    @Test
    fun testMakeBinaryIntervalRandom() {
        val random = random()
        val iters = atLeast(100)
        for (iter in 0 until iters) {
            val minTerm = randomBinaryTerm(random)
            val minInclusive = random.nextBoolean()
            val maxTerm = randomBinaryTerm(random)
            val maxInclusive = random.nextBoolean()

            val a = makeBinaryInterval(minTerm, minInclusive, maxTerm, maxInclusive)

            for (iter2 in 0 until 500) {
                val term = randomBinaryTerm(random)
                val minCmp = minTerm.compareTo(term)
                val maxCmp = maxTerm.compareTo(term)

                val expected = when {
                    minCmp > 0 || maxCmp < 0 -> false
                    minCmp == 0 && maxCmp == 0 -> minInclusive && maxInclusive
                    minCmp == 0 -> minInclusive
                    maxCmp == 0 -> maxInclusive
                    else -> true
                }

                val intsBuilder = IntsRefBuilder()
                Util.toIntsRef(term, intsBuilder)
                assertEquals(expected, Operations.run(a, intsBuilder.toIntsRef()))
            }
        }
    }

    private fun intsRef(s: String): IntsRef {
        val intsBuilder = IntsRefBuilder()
        Util.toIntsRef(
            newBytesRef(s),
            intsBuilder
        )
        return intsBuilder.toIntsRef()
    }

    @Test
    fun testMakeBinaryIntervalBasic() {
        val a = Automata.makeBinaryInterval(
            newBytesRef("bar"),
            true,
            newBytesRef("foo"),
            true
        )
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, intsRef("bar")))
        assertTrue(Operations.run(a, intsRef("foo")))
        assertTrue(Operations.run(a, intsRef("beep")))
        assertFalse(Operations.run(a, intsRef("baq")))
        assertTrue(Operations.run(a, intsRef("bara")))
    }

    @Test
    fun testMakeBinaryIntervalLowerBoundEmptyString() {
        var a =
            Automata.makeBinaryInterval(newBytesRef(""), true, newBytesRef("bar"), true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, intsRef("")))
        assertTrue(Operations.run(a, intsRef("a")))
        assertTrue(Operations.run(a, intsRef("bar")))
        assertFalse(Operations.run(a, intsRef("bara")))
        assertFalse(Operations.run(a, intsRef("baz")))
        a = Automata.makeBinaryInterval(newBytesRef(""), false, newBytesRef("bar"), true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertFalse(Operations.run(a, intsRef("")))
        assertTrue(Operations.run(a, intsRef("a")))
        assertTrue(Operations.run(a, intsRef("bar")))
        assertFalse(Operations.run(a, intsRef("bara")))
        assertFalse(Operations.run(a, intsRef("baz")))
    }

    @Test
    fun testMakeBinaryIntervalEqual() {
        val a = Automata.makeBinaryInterval(
            newBytesRef("bar"), true,
            newBytesRef("bar"), true
        )
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, intsRef("bar")))
        assertTrue(AutomatonTestUtil.isFinite(a))
        assertEquals(1, TestOperations.getFiniteStrings(a).size)
    }

    @Test
    fun testMakeBinaryIntervalCommonPrefix() {
        val a = Automata.makeBinaryInterval(
            newBytesRef("bar"), true,
            newBytesRef("barfoo"), true
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

    @Test
    fun testMakeBinaryExceptEmpty() {
        val a = Automata.makeNonEmptyBinary()
        AutomatonTestUtil.assertMinimalDFA(a)
        assertFalse(Operations.run(a, intsRef("")))
        assertTrue(Operations.run(a, intsRef(randomUnicodeString(random(), 10))))
    }

    @Test
    fun testMakeBinaryIntervalOpenMax() {
        val a = Automata.makeBinaryInterval(newBytesRef("bar"), true, null, true)
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
        var a = Automata.makeBinaryInterval(newBytesRef(""), true, null, true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertTrue(Operations.run(a, intsRef("")))
        assertTrue(Operations.run(a, intsRef("a")))
        assertTrue(Operations.run(a, intsRef("aaaaaa")))
        a = Automata.makeBinaryInterval(newBytesRef(""), false, null, true)
        AutomatonTestUtil.assertMinimalDFA(a)
        assertFalse(Operations.run(a, intsRef("")))
        assertTrue(Operations.run(a, intsRef("a")))
        assertTrue(Operations.run(a, intsRef("aaaaaa")))
    }

    @Test
    fun testMakeBinaryIntervalOpenMin() {
        val a = Automata.makeBinaryInterval(null, true, newBytesRef("foo"), true)
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
        val a = Automata.makeBinaryInterval(newBytesRef(), true, null, true)
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

    @Test
    fun testGetSingleton() {
        val iters = atLeast(10000)
        for (iter in 0 until iters) {
            val s = randomUnicodeString(random())
            val a = Automata.makeString(s)
            assertEquals(toIntsRef(s), Operations.getSingleton(a))
        }
    }

    @Test
    fun testGetSingletonEmptyString() {
        val a = Automaton()
        val s = a.createState()
        a.setAccept(s, true)
        a.finishState()
        assertEquals(IntsRef(), Operations.getSingleton(a))
    }

    @Test
    fun testGetSingletonNothing() {
        val a = Automaton()
        a.createState()
        a.finishState()
        assertNull(Operations.getSingleton(a))
    }

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

    @Test
    fun testDeterminizeTooMuchEffort() {
        assertFailsWith<TooComplexToDeterminizeException> {
            val a = RegExp("(.*a){2000}").toAutomaton()
            Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        }
        assertFailsWith<TooComplexToDeterminizeException> {
            var a = RegExp("(.*a){2000}").toAutomaton()
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

    private fun randomBinaryTerm(random: Random, length: Int = random.nextInt(15)): BytesRef {
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return BytesRef(bytes)
    }

}
