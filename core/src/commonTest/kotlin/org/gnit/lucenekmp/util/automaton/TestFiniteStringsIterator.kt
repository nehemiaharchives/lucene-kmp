package org.gnit.lucenekmp.util.automaton

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.test.assertFailsWith
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.Util

/**
 * Port of Lucene's TestFiniteStringsIterator from commit ec75fca.
 */
class TestFiniteStringsIterator : LuceneTestCase() {

    @Test
    fun testRandomFiniteStrings1() {
        val numStrings = atLeast(100)
        if (VERBOSE) {
            println("TEST: numStrings=$numStrings")
        }

        val strings = HashSet<IntsRef>()
        val automata = mutableListOf<Automaton>()
        val scratch = IntsRefBuilder()
        for (i in 0 until numStrings) {
            val s = TestUtil.randomSimpleString(random(), 1, 200)
            Util.toUTF32(s, scratch)
            if (strings.add(scratch.toIntsRef())) {
                automata.add(Automata.makeString(s))
                if (VERBOSE) {
                    println("  add string=$s")
                }
            }
        }

        var a = Operations.union(automata)
        if (random().nextBoolean()) {
            a = MinimizationOperations.minimize(a, 1_000_000)
            if (VERBOSE) {
                println("TEST: a.minimize numStates=" + a.numStates)
            }
        } else if (random().nextBoolean()) {
            if (VERBOSE) {
                println("TEST: a.determinize")
            }
            a = Operations.determinize(a, 1_000_000)
        } else if (random().nextBoolean()) {
            if (VERBOSE) {
                println("TEST: a.removeDeadStates")
            }
            a = Operations.removeDeadStates(a)
        }

        val iterator = FiniteStringsIterator(a)
        val actual = getFiniteStrings(iterator)
        assertFiniteStringsRecursive(a, actual)

        if (strings != HashSet(actual)) {
            println("strings.size()=${strings.size} actual.size=${actual.size}")
            val x = ArrayList(strings)
            x.sort()
            val y = ArrayList(actual)
            y.sort()
            val end = minOf(x.size, y.size)
            for (i in 0 until end) {
                println("  i=$i string=${toString(x[i])} actual=${toString(y[i])}")
            }
            fail("wrong strings found")
        }
    }

    /** Basic test for getFiniteStrings */
    @Test
    fun testFiniteStringsBasic() {
        var a = Operations.union(mutableListOf(Automata.makeString("dog"), Automata.makeString("duck")))
        a = MinimizationOperations.minimize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        val iterator = FiniteStringsIterator(a)
        val actual = getFiniteStrings(iterator)
        assertFiniteStringsRecursive(a, actual)
        assertEquals(2, actual.size)
        val dog = IntsRefBuilder()
        Util.toIntsRef(BytesRef("dog"), dog)
        assertTrue(actual.contains(dog.get()))
        val duck = IntsRefBuilder()
        Util.toIntsRef(BytesRef("duck"), duck)
        assertTrue(actual.contains(duck.get()))
    }

    @Test
    fun testFiniteStringsEatsStack() {
        val chars = CharArray(50000)
        TestUtil.randomFixedLengthUnicodeString(random(), chars, 0, chars.size)
        val bigString1 = chars.concatToString()
        TestUtil.randomFixedLengthUnicodeString(random(), chars, 0, chars.size)
        val bigString2 = chars.concatToString()
        val a = Operations.union(mutableListOf(Automata.makeString(bigString1), Automata.makeString(bigString2)))
        val iterator = FiniteStringsIterator(a)
        val actual = getFiniteStrings(iterator)
        assertEquals(2, actual.size)
        val scratch = IntsRefBuilder()
        Util.toUTF32(bigString1, scratch)
        assertTrue(actual.contains(scratch.get()))
        Util.toUTF32(bigString2, scratch)
        assertTrue(actual.contains(scratch.get()))
    }

    @Test
    fun testWithCycle() {
        assertFailsWith<IllegalArgumentException> {
            val a = RegExp("abc.*", RegExp.NONE).toAutomaton()
            val iterator = FiniteStringsIterator(a)
            getFiniteStrings(iterator)
        }
    }

    @Test
    fun testSingletonNoLimit() {
        val a = Automata.makeString("foobar")
        val iterator = FiniteStringsIterator(a)
        val actual = getFiniteStrings(iterator)
        assertEquals(1, actual.size)
        val scratch = IntsRefBuilder()
        Util.toUTF32("foobar", scratch)
        assertTrue(actual.contains(scratch.get()))
    }

    @Test
    fun testShortAccept() {
        var a = Operations.union(mutableListOf(Automata.makeString("x"), Automata.makeString("xy")))
        a = MinimizationOperations.minimize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        val iterator = FiniteStringsIterator(a)
        val actual = getFiniteStrings(iterator)
        assertEquals(2, actual.size)
        val x = IntsRefBuilder()
        Util.toIntsRef(BytesRef("x"), x)
        assertTrue(actual.contains(x.get()))
        val xy = IntsRefBuilder()
        Util.toIntsRef(BytesRef("xy"), xy)
        assertTrue(actual.contains(xy.get()))
    }

    @Test
    fun testSingleString() {
        val a = Automaton()
        val start = a.createState()
        val end = a.createState()
        a.setAccept(end, true)
        a.addTransition(start, end, 'a'.code, 'a'.code)
        a.finishState()
        val accepted = TestOperations.getFiniteStrings(a)
        assertEquals(1, accepted.size)
        val intsRef = IntsRefBuilder()
        intsRef.append('a'.code)
        assertTrue(accepted.contains(intsRef.toIntsRef()))
    }

    /** All strings generated by the iterator. */
    private fun getFiniteStrings(iterator: FiniteStringsIterator): List<IntsRef> {
        val result = ArrayList<IntsRef>()
        var finiteString: IntsRef?
        while (iterator.next().also { finiteString = it } != null) {
            result.add(IntsRef.deepCopyOf(finiteString!!))
        }
        return result
    }

    /** Check that strings the automaton returns are as expected. */
    private fun assertFiniteStringsRecursive(automaton: Automaton, actual: List<IntsRef>) {
        val expected = AutomatonTestUtil.getFiniteStringsRecursive(automaton, -1)
        assertEquals(expected.size, actual.size)
        assertEquals(expected, HashSet(actual))
    }

    // ascii only!
    private fun toString(ints: IntsRef): String {
        val br = BytesRef(ints.length)
        for (i in 0 until ints.length) {
            br.bytes[i] = ints.ints[i + ints.offset].toByte()
        }
        br.length = ints.length
        return br.utf8ToString()
    }
}

