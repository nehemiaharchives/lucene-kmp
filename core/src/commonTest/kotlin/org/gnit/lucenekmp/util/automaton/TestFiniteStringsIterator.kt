package org.gnit.lucenekmp.util.automaton

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.tests.util.automaton.MinimizationOperations
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.Util

class TestFiniteStringsIterator : LuceneTestCase() {

    @Test
    fun testRandomFiniteStrings1() {
        val numStrings = atLeast(100)
        val strings = mutableSetOf<IntsRef>()
        val automata = mutableListOf<Automaton>()
        val scratch = IntsRefBuilder()
        for (i in 0 until numStrings) {
            val s = TestUtil.randomSimpleString(random(), 1, 200)
            Util.toUTF32(s.toCharArray(), 0, s.length, scratch)
            if (strings.add(scratch.toIntsRef())) {
                automata.add(Automata.makeString(s))
            }
        }
        var a = Operations.union(automata)
        when {
            random().nextBoolean() -> {
                a = MinimizationOperations.minimize(a, 1_000_000)
            }
            random().nextBoolean() -> {
                a = Operations.determinize(a, 1_000_000)
            }
            random().nextBoolean() -> {
                a = Operations.removeDeadStates(a)
            }
        }

        val iterator = FiniteStringsIterator(a)
        val actual = getFiniteStrings(iterator)
        assertFiniteStringsRecursive(a, actual)
        if (strings != actual.toSet()) {
            fail("wrong strings found")
        }
    }

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
        val chars = CharArray(50_000)
        TestUtil.randomFixedLengthUnicodeString(random(), chars, 0, chars.size)
        val bigString1 = chars.concatToString()
        TestUtil.randomFixedLengthUnicodeString(random(), chars, 0, chars.size)
        val bigString2 = chars.concatToString()
        val a = Operations.union(mutableListOf(Automata.makeString(bigString1), Automata.makeString(bigString2)))
        val iterator = FiniteStringsIterator(a)
        val actual = getFiniteStrings(iterator)
        assertEquals(2, actual.size)
        val scratch = IntsRefBuilder()
        Util.toUTF32(bigString1.toCharArray(), 0, bigString1.length, scratch)
        assertTrue(actual.contains(scratch.get()))
        Util.toUTF32(bigString2.toCharArray(), 0, bigString2.length, scratch)
        assertTrue(actual.contains(scratch.get()))
    }

    @Test
    fun testWithCycle() {
        expectThrows(IllegalArgumentException::class) {
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
        Util.toUTF32("foobar".toCharArray(), 0, 6, scratch)
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

    companion object {
        fun getFiniteStrings(iterator: FiniteStringsIterator): MutableList<IntsRef> {
            val result = mutableListOf<IntsRef>()
            var finiteString: IntsRef?
            while (iterator.next().also { finiteString = it } != null) {
                result.add(IntsRef.deepCopyOf(finiteString!!))
            }
            return result
        }

        private fun assertFiniteStringsRecursive(a: Automaton, actual: List<IntsRef>) {
            val expected = AutomatonTestUtil.getFiniteStringsRecursive(a, -1)
            assertEquals(expected.size, actual.size)
            assertEquals(expected, actual.toSet())
        }
    }
}
