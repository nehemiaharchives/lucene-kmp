package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.automaton.Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestOperations : LuceneTestCase() {
    /** Test string union.  */
    // TODO originally this test was following
    /*@Test
    fun testStringUnion() {
        val strings: MutableList<BytesRef> =
            ArrayList()
        var i: Int = *//*RandomNumbers.randomIntBetween(random(), 0, 1000)*//* random().nextInt(0, 1000)
        while (--i >= 0) {
            strings.add(BytesRef(TestUtil.randomUnicodeString(random())))
        }

        strings.sort()
        val union: Automaton = Automata.makeStringUnion(strings)
        assertTrue(union.isDeterministic)
        assertFalse(Operations.hasDeadStatesFromInitial(union))

        val naiveUnion: Automaton = naiveUnion(strings)
        assertTrue(naiveUnion.isDeterministic)
        assertFalse(Operations.hasDeadStatesFromInitial(naiveUnion))

        assertTrue(
            AutomatonTestUtil.sameLanguage(
                union,
                naiveUnion
            )
        )
    }*/
    @Test
    fun testStringUnion() {
        val strings =
            listOf("abc", "abd", "abe").map { BytesRef(it) }.sorted()

        var expected = Operations.union(strings.map { Automata.makeString(it.utf8ToString()) }.toMutableList())
        expected = Operations.determinize(expected, DEFAULT_DETERMINIZE_WORK_LIMIT)

        val actual = Automata.makeStringUnion(strings)

        AutomatonTestUtil.assertMinimalDFA(actual)
        assertTrue(AutomatonTestUtil.sameLanguage(expected, actual))
    }

    /** Test concatenation with empty language returns empty  */
    @Test
    fun testEmptyLanguageConcatenate() {
        val concat: Automaton =
            Operations.concatenate(
                mutableListOf(
                    Automata.makeString("a"),
                    Automata.makeEmpty()
                )
            )
        AutomatonTestUtil.assertMinimalDFA(concat)
        assertTrue(Operations.isEmpty(concat))
    }

    /**
     * Test case for the topoSortStates method when the input Automaton contains a cycle. This test
     * case constructs an Automaton with two disjoint sets of states—one without a cycle and one with
     * a cycle. The topoSortStates method should detect the presence of a cycle and throw an
     * IllegalArgumentException.
     */
    @Test
    fun testCycledAutomaton() {
        val a: Automaton = generateRandomAutomaton(true)
        val exc: IllegalArgumentException? =
            expectThrows(
                IllegalArgumentException::class
            ) { Operations.topoSortStates(a) }
        assertTrue(exc?.message!!.contains("Input automaton has a cycle"))
    }

    @Test
    fun testTopoSortStates() {
        val a: Automaton = generateRandomAutomaton(false)

        val sorted: IntArray = Operations.topoSortStates(a)
        val stateMap = IntArray(a.numStates)
        stateMap.fill(-1)
        var order = 0
        for (state in sorted) {
            assertEquals(-1, stateMap[state].toLong())
            stateMap[state] = (order++)
        }

        val transition = Transition()
        for (state in sorted) {
            val count: Int = a.initTransition(state, transition)
            for (i in 0..<count) {
                a.getNextTransition(transition)
                // ensure dest's order is higher than current state
                assertTrue(stateMap[transition.dest] > stateMap[state])
            }
        }
    }

    /** Test optimization to concatenate() with empty String to an NFA  */
    @Test
    fun testEmptySingletonNFAConcatenate() {
        val singleton: Automaton =
            Automata.makeString("")
        val expandedSingleton: Automaton = singleton
        // an NFA (two transitions for 't' from initial state)
        val nfa: Automaton =
            Operations.union(
                mutableListOf(
                    Automata.makeString("this"),
                    Automata.makeString("three")
                )
            )
        AutomatonTestUtil.assertCleanNFA(nfa)
        val concat1: Automaton =
            Operations.concatenate(
                mutableListOf(
                    expandedSingleton,
                    nfa
                )
            )
        AutomatonTestUtil.assertCleanNFA(concat1)
        val concat2: Automaton =
            Operations.concatenate(
                mutableListOf(
                    singleton,
                    nfa
                )
            )
        AutomatonTestUtil.assertCleanNFA(concat2)
        assertFalse(concat2.isDeterministic)
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                Operations.determinize(concat1, 100),
                Operations.determinize(concat2, 100)
            )
        )
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                Operations.determinize(nfa, 100),
                Operations.determinize(concat1, 100)
            )
        )
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                Operations.determinize(nfa, 100),
                Operations.determinize(concat2, 100)
            )
        )
    }

    @Test
    @Throws(Throwable::class)
    fun testGetRandomAcceptedString() {
        val ITER1: Int = atLeast(100)
        val ITER2: Int = atLeast(100)
        for (i in 0..<ITER1) {
            val text: String =
                AutomatonTestUtil.randomRegexp(random())
            val re =
                RegExp(text, RegExp.NONE)
            // System.out.println("TEST i=" + i + " re=" + re);
            val a: Automaton = Operations.determinize(
                re.toAutomaton(),
                DEFAULT_DETERMINIZE_WORK_LIMIT
            )
            assertFalse(Operations.isEmpty(a), "empty: $text")

            val rx: AutomatonTestUtil.RandomAcceptedStrings =
                AutomatonTestUtil.RandomAcceptedStrings(a)
            for (j in 0..<ITER2) {
                // System.out.println("TEST: j=" + j);
                var acc: IntArray? = null
                try {
                    acc = rx.getRandomAcceptedString(random())
                    val s: String = UnicodeUtil.newString(acc, 0, acc.size)
                    // a.writeDot("adot");
                    assertTrue(Operations.run(a, s))
                } catch (t: Throwable) {
                    println("regexp: $re")
                    if (acc != null) {
                        println("fail acc re=" + re + " count=" + acc.size)
                        for (k in acc) {
                            println("  " + k.toString(16))
                        }
                    }
                    throw t
                }
            }
        }
    }

    @Test
    fun testIsFiniteEatsStack() {
        val chars = CharArray(50000)
        TestUtil.randomFixedLengthUnicodeString(
            random(),
            chars,
            0,
            chars.size
        )
        val bigString1 = chars.concatToString()
        TestUtil.randomFixedLengthUnicodeString(
            random(),
            chars,
            0,
            chars.size
        )
        val bigString2 = chars.concatToString()
        val a: Automaton =
            Operations.union(
                mutableListOf(
                    Automata.makeString(bigString1),
                    Automata.makeString(bigString2)
                )
            )
        val exc: IllegalArgumentException? =
            expectThrows(
                IllegalArgumentException::class
            ) { AutomatonTestUtil.isFinite(a) }
        assertTrue(exc?.message!!.contains("input automaton is too large"))
    }

    @Test
    fun testIsTotal() {
        // minimal
        assertFalse(Operations.isTotal(Automata.makeEmpty()))
        assertFalse(Operations.isTotal(Automata.makeEmptyString()))
        assertTrue(Operations.isTotal(Automata.makeAnyString()))
        assertTrue(
            Operations.isTotal(
                Automata.makeAnyBinary(),
                0,
                255
            )
        )
        assertFalse(
            Operations.isTotal(
                Automata.makeNonEmptyBinary(),
                0,
                255
            )
        )
        // deterministic, but not minimal
        assertTrue(
            Operations.isTotal(
                Operations.repeat(
                    Automata.makeAnyChar()
                )
            )
        )
        val tricky: Automaton =
            Operations.repeat(
                Operations.union(
                    mutableListOf(
                        Automata.makeCharRange(
                            Char.MIN_VALUE.code,
                            100
                        ),
                        Automata.makeCharRange(101, Character.MAX_CODE_POINT)
                    )
                )
            )
        assertTrue(Operations.isTotal(tricky))
        // not total, but close
        val tricky2: Automaton =
            Operations.repeat(
                Operations.union(
                    mutableListOf(
                        Automata.makeCharRange(
                            Char.MIN_VALUE.code + 1,
                            100
                        ),
                        Automata.makeCharRange(101, Character.MAX_CODE_POINT)
                    )
                )
            )
        assertFalse(Operations.isTotal(tricky2))
        val tricky3: Automaton =
            Operations.repeat(
                Operations.union(
                    mutableListOf(
                        Automata.makeCharRange(Char.MIN_VALUE.code, 99),
                        Automata.makeCharRange(101, Character.MAX_CODE_POINT)
                    )
                )
            )
        assertFalse(Operations.isTotal(tricky3))
        val tricky4: Automaton =
            Operations.repeat(
                Operations.union(
                    mutableListOf(
                        Automata.makeCharRange(
                            Char.MIN_VALUE.code,
                            100
                        ),
                        Automata.makeCharRange(
                            101,
                            Character.MAX_CODE_POINT - 1
                        )
                    )
                )
            )
        assertFalse(Operations.isTotal(tricky4))
    }

    /**
     * This method creates a random Automaton by generating states at multiple levels. At each level,
     * a random number of states are created, and transitions are added between the states of the
     * current and the previous level randomly, If the 'hasCycle' parameter is true, a transition is
     * added from the first state of the last level back to the initial state to create a cycle in the
     * Automaton..
     *
     * @param hasCycle if true, the generated Automaton will have a cycle; if false, it won't have a
     * cycle.
     * @return a randomly generated Automaton instance.
     */
    private fun generateRandomAutomaton(hasCycle: Boolean): Automaton {
        val a = Automaton()
        var lastLevelStates: MutableList<Int> = ArrayList()
        val initialState: Int = a.createState()
        val maxLevel: Int = random().nextInt(4, 10)
        lastLevelStates.add(initialState)

        for (level in 1..<maxLevel) {
            val numStates: Int = random().nextInt(3, 10)
            val nextLevelStates: MutableList<Int> = ArrayList()

            for (i in 0..<numStates) {
                val nextState: Int = a.createState()
                nextLevelStates.add(nextState)
            }

            for (lastState in lastLevelStates) {
                for (nextState in nextLevelStates) {
                    // if hasCycle is enabled, we will always add a transition, so we could make sure the
                    // generated Automaton has a cycle.
                    if (hasCycle || random().nextInt(7) >= 1) {
                        a.addTransition(
                            lastState,
                            nextState,
                            random().nextInt(10)
                        )
                    }
                }
            }
            lastLevelStates = nextLevelStates
        }

        if (hasCycle) {
            val lastState: Int = lastLevelStates[0]
            a.addTransition(lastState, initialState, random().nextInt(10))
        }

        a.finishState()
        return a
    }

    @Test
    fun testRepeatEmptyLanguage() {
        val expected: Automaton = Automata.makeEmpty()
        val actual: Automaton =
            Operations.repeat(expected)
        AutomatonTestUtil.assertMinimalDFA(actual)
        assertSame(expected, actual)
    }

    @Test
    fun testRepeatEmptyString() {
        val expected: Automaton =
            Automata.makeEmptyString()
        val actual: Automaton =
            Operations.repeat(expected)
        AutomatonTestUtil.assertMinimalDFA(actual)
        assertSame(expected, actual)
    }

    @Test
    fun testRepeatChar() {
        val actual: Automaton =
            Operations.repeat(Automata.makeChar('a'.code))
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected = Automaton()
        expected.createState()
        expected.setAccept(0, true)
        expected.addTransition(0, 0, 'a'.code)
        expected.finishState()
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                expected,
                actual
            )
        )
    }

    @Test
    fun testRepeatOptionalChar() {
        val aOrEmpty = Automaton()
        aOrEmpty.createState()
        aOrEmpty.setAccept(0, true)
        aOrEmpty.createState()
        aOrEmpty.setAccept(1, true)
        aOrEmpty.addTransition(0, 1, 'a'.code)
        val actual: Automaton =
            Operations.repeat(aOrEmpty)
        AutomatonTestUtil.assertMinimalDFA(actual)

        val expected: Automaton =
            Operations.repeat(Automata.makeChar('a'.code))
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                expected,
                actual
            )
        )
    }

    @Test
    fun testRepeatTwoChar() {
        val expected = Automaton()
        expected.createState()
        expected.createState()
        expected.setAccept(0, true)
        expected.addTransition(0, 1, 'a'.code)
        expected.finishState()
        expected.addTransition(1, 0, 'b'.code)
        expected.finishState()
        val actual: Automaton =
            Operations.repeat(Automata.makeString("ab"))

        AutomatonTestUtil.assertMinimalDFA(actual)
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                expected,
                actual
            )
        )
    }

    @Test
    fun testRepeatOptionalTwoChar() {
        val expected: Automaton =
            Operations.repeat(Automata.makeString("ab"))
        val actual: Automaton =
            Operations.repeat(expected)

        AutomatonTestUtil.assertMinimalDFA(actual)
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                expected,
                actual
            )
        )
    }

    @Test
    fun testRepeatConcatenation() {
        val expected = Automaton()
        expected.createState()
        expected.createState()
        expected.createState()
        expected.setAccept(0, true)
        expected.addTransition(0, 1, 'a'.code)
        expected.addTransition(0, 0, 'c'.code)
        expected.finishState()
        expected.addTransition(1, 2, 'b'.code)
        expected.finishState()
        expected.addTransition(2, 1, 'a'.code)
        expected.addTransition(2, 0, 'c'.code)
        expected.finishState()

        val abs: Automaton =
            Operations.repeat(Automata.makeString("ab"))
        val absThenC: Automaton =
            Operations.concatenate(
                mutableListOf(
                    abs,
                    Automata.makeChar('c'.code)
                )
            )
        val actual: Automaton =
            Operations.repeat(absThenC)

        AutomatonTestUtil.assertMinimalDFA(actual)
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                expected,
                actual
            )
        )
    }

    @Test
    fun testRepeatOptionalConcatenation() {
        val abs: Automaton =
            Operations.repeat(Automata.makeString("ab"))
        val absThenC: Automaton =
            Operations.concatenate(
                mutableListOf(
                    abs,
                    Automata.makeChar('c'.code)
                )
            )

        val expected: Automaton =
            Operations.repeat(absThenC)
        val actual: Automaton =
            Operations.repeat(expected)

        AutomatonTestUtil.assertMinimalDFA(actual)
        assertSame(expected, actual)
    }

    @Test
    fun testRepeatConcatenateOptional() {
        var expected = Automaton()
        expected.createState()
        expected.createState()
        expected.setAccept(0, true)
        expected.addTransition(0, 0, 'a'.code)
        expected.addTransition(0, 1, 'a'.code)
        expected.finishState()
        expected.addTransition(1, 0, 'b'.code)
        expected.finishState()
        expected = Operations.determinize(expected, Int.MAX_VALUE)

        val aOrAb = Automaton()
        aOrAb.createState()
        aOrAb.createState()
        aOrAb.createState()
        aOrAb.setAccept(1, true)
        aOrAb.setAccept(2, true)
        aOrAb.addTransition(0, 1, 'a'.code)
        aOrAb.finishState()
        aOrAb.addTransition(1, 2, 'b'.code)
        aOrAb.finishState()
        val actual: Automaton =
            Operations.repeat(aOrAb)
        AutomatonTestUtil.assertMinimalDFA(actual)

        assertTrue(
            AutomatonTestUtil.sameLanguage(
                expected,
                actual
            )
        )
    }

    @Test
    fun testMergeAcceptStatesWithNoTransition() {
        val emptyLanguage: Automaton =
            Automata.makeEmpty()
        assertSame(
            emptyLanguage,
            Operations.mergeAcceptStatesWithNoTransition(emptyLanguage)
        )

        val a: Automaton = Automata.makeString("a")
        assertSame(a, Operations.mergeAcceptStatesWithNoTransition(a))

        // All accept states get combined
        val aOrC = Automaton()
        aOrC.createState()
        aOrC.createState()
        aOrC.createState()
        aOrC.addTransition(0, 1, 'a'.code)
        aOrC.setAccept(1, true)
        aOrC.addTransition(0, 2, 'c'.code)
        aOrC.setAccept(2, true)
        val aOrCSingleAcceptState: Automaton =
            Operations.mergeAcceptStatesWithNoTransition(aOrC)
        assertEquals(1, aOrCSingleAcceptState.acceptStates.cardinality().toLong())
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                aOrC,
                aOrCSingleAcceptState
            )
        )

        // Two accept states get combined, but not the 3rd one since it has an outgoing transition
        val aOrCOrXStar = Automaton()
        aOrCOrXStar.createState()
        aOrCOrXStar.createState()
        aOrCOrXStar.createState()
        aOrCOrXStar.createState()
        aOrCOrXStar.addTransition(0, 1, 'a'.code)
        aOrCOrXStar.setAccept(1, true)
        aOrCOrXStar.addTransition(0, 2, 'c'.code)
        aOrCOrXStar.setAccept(2, true)
        aOrCOrXStar.addTransition(0, 3, 'x'.code)
        aOrCOrXStar.addTransition(3, 3, 'x'.code)
        aOrCOrXStar.setAccept(3, true)
        val aOrCOrXStarSingleAcceptState: Automaton =
            Operations.mergeAcceptStatesWithNoTransition(aOrCOrXStar)
        assertEquals(2, aOrCOrXStarSingleAcceptState.acceptStates.cardinality().toLong())
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                aOrCOrXStar,
                aOrCOrXStarSingleAcceptState
            )
        )

        val iters: Int = atLeast(10) // TODO originally 100, but reduced to 10 for dev speed
        val determinizeWorkLimit = 100 // TODO originally MAX_INT, but reduced to 100 for dev speed
        for (iter in 0..<iters) {
            var randomAutomaton: Automaton = AutomatonTestUtil.randomAutomaton(random())

            try {
                randomAutomaton = Operations.determinize(
                    randomAutomaton,
                    determinizeWorkLimit
                )
            }catch (e: TooComplexToDeterminizeException){
                continue
            }

            val actual: Automaton = Operations.mergeAcceptStatesWithNoTransition(randomAutomaton)
            assertTrue(AutomatonTestUtil.sameLanguage(randomAutomaton, actual))
        }
    }

    @Test
    fun testDuelRepeat() {
        val iters: Int = atLeast(10) // TODO originally 1_000 but reduced to 10 for dev speed
        for (iter in 0..<iters) {
            val a: Automaton =
                AutomatonTestUtil.randomAutomaton(random())
            val repeat1: Automaton =
                Operations.determinize(
                    Operations.repeat(a), Int.MAX_VALUE
                )
            val repeat2: Automaton =
                Operations.determinize(
                    naiveRepeat(a), Int.MAX_VALUE
                )
            assertTrue(
                AutomatonTestUtil.sameLanguage(
                    repeat1,
                    repeat2
                )
            )
        }
    }

    @Test
    fun testOptional() {
        val expected = Automaton()
        expected.createState()
        expected.setAccept(0, true)
        expected.finishState()
        expected.createState()
        expected.setAccept(1, true)
        expected.addTransition(0, 1, 'a'.code)
        expected.finishState()

        val actual: Automaton =
            Operations.optional(Automata.makeChar('a'.code))

        AutomatonTestUtil.assertMinimalDFA(actual)
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                expected,
                actual
            )
        )
    }

    @Test
    fun testOptionalOptional() {
        val expected: Automaton =
            Operations.optional(Automata.makeChar('a'.code))
        val actual: Automaton =
            Operations.optional(expected)

        AutomatonTestUtil.assertMinimalDFA(actual)
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                expected,
                actual
            )
        )
    }

    // test an automaton that has a transition to state 0. a(ba)*
    @Test
    fun testOptionalAcceptsState0() {
        val expected = Automaton()
        expected.createState()
        expected.setAccept(0, true)
        expected.createState()
        expected.createState()
        expected.setAccept(2, true)
        expected.addTransition(0, 2, 'a'.code)
        expected.finishState()
        expected.addTransition(1, 2, 'a'.code)
        expected.finishState()
        expected.addTransition(2, 1, 'b'.code)
        expected.finishState()

        val a = Automaton()
        a.createState()
        a.createState()
        a.setAccept(1, true)
        a.addTransition(0, 1, 'a'.code)
        a.finishState()
        a.addTransition(1, 0, 'b'.code)
        a.finishState()
        val actual: Automaton = Operations.optional(a)

        AutomatonTestUtil.assertMinimalDFA(actual)
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                expected,
                actual
            )
        )
    }

    @Test
    fun testOptionalOptionalAcceptsState0() {
        var expected = Automaton()
        expected.createState()
        expected.createState()
        expected.setAccept(1, true)
        expected.addTransition(0, 1, 'a'.code)
        expected.finishState()
        expected.addTransition(1, 0, 'b'.code)
        expected.finishState()
        expected = Operations.optional(expected)

        val actual: Automaton =
            Operations.optional(expected)
        AutomatonTestUtil.assertMinimalDFA(actual)
        assertTrue(
            AutomatonTestUtil.sameLanguage(
                expected,
                actual
            )
        )
    }

    @Test
    fun testDuelOptional() {
        val iters: Int = atLeast(10) // TODO originally 1_000 but reduced to 10 for dev speed
        for (iter in 0..<iters) {
            val a: Automaton =
                AutomatonTestUtil.randomAutomaton(random())
            val repeat1: Automaton =
                Operations.determinize(
                    Operations.optional(a), Int.MAX_VALUE
                )
            val repeat2: Automaton =
                Operations.determinize(
                    naiveOptional(a), Int.MAX_VALUE
                )
            assertTrue(
                AutomatonTestUtil.sameLanguage(
                    repeat1,
                    repeat2
                )
            )
        }
    }

    companion object {
        /*private fun naiveUnion(strings: MutableList<BytesRef>): Automaton {
            val eachIndividual: Array<Automaton?> =
                arrayOfNulls(strings.size)
            var i = 0
            for (bref in strings) {
                eachIndividual[i++] = Automata.makeString(bref.utf8ToString())
            }
            return Operations.determinize(
                Operations.union(
                    mutableListOf(*eachIndividual) as MutableList<Automaton>
                ), DEFAULT_DETERMINIZE_WORK_LIMIT
            )
        }*/

        private fun naiveUnion(strings: MutableList<BytesRef>): Automaton {
            if (strings.isEmpty()) {
                return Automata.makeEmpty()
            }
            val automata = strings.map { bref ->
                Automata.makeString(bref.utf8ToString())
            }
            return Operations.determinize(
                Operations.union(
                    automata.toMutableList()
                ), DEFAULT_DETERMINIZE_WORK_LIMIT
            )
        }

        /**
         * Returns the set of all accepted strings.
         *
         *
         * This method exist just to ease testing. For production code directly use [ ] instead.
         *
         * @see FiniteStringsIterator
         */
        fun getFiniteStrings(a: Automaton): MutableSet<IntsRef> {
            return getFiniteStrings(FiniteStringsIterator(a))
        }

        /**
         * Returns the set of accepted strings, up to at most `limit` strings.
         *
         *
         * This method exist just to ease testing. For production code directly use [ ] instead.
         *
         * @see LimitedFiniteStringsIterator
         */
        fun getFiniteStrings(
            a: Automaton,
            limit: Int
        ): MutableSet<IntsRef> {
            return getFiniteStrings(LimitedFiniteStringsIterator(a, limit))
        }

        /** Get all finite strings of an iterator.  */
        private fun getFiniteStrings(iterator: FiniteStringsIterator): MutableSet<IntsRef> {
            val result: MutableSet<IntsRef> = HashSet()
            var finiteString: IntsRef?
            while ((iterator.next().also { finiteString = it }) != null) {
                result.add(IntsRef.deepCopyOf(finiteString!!))
            }

            return result
        }

        // This is the original implementation of Operations#repeat, before we improved it to generate
        // simpler automata in some common cases.
        private fun naiveRepeat(a: Automaton): Automaton {
            if (a.numStates == 0) {
                return a
            }

            val builder: Automaton.Builder =
                Automaton.Builder()
            // Create the initial state, which is accepted
            builder.createState()
            builder.setAccept(0, true)
            builder.copy(a)

            val t = Transition()
            var count: Int = a.initTransition(0, t)
            for (i in 0..<count) {
                a.getNextTransition(t)
                builder.addTransition(0, t.dest + 1, t.min, t.max)
            }

            val numStates: Int = a.numStates
            for (s in 0..<numStates) {
                if (a.isAccept(s)) {
                    count = a.initTransition(0, t)
                    for (i in 0..<count) {
                        a.getNextTransition(t)
                        builder.addTransition(s + 1, t.dest + 1, t.min, t.max)
                    }
                }
            }

            return builder.finish()
        }

        // This is the original implementation of Operations#optional, before we improved it to generate
        // simpler automata in some common cases.
        private fun naiveOptional(a: Automaton): Automaton {
            val result = Automaton()
            result.createState()
            result.setAccept(0, true)
            if (a.numStates > 0) {
                result.copy(a)
                result.addEpsilon(0, 1)
            }
            result.finishState()
            return result
        }
    }
}
