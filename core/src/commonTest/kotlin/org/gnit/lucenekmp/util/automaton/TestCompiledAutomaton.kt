package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class TestCompiledAutomaton : LuceneTestCase() {

    private fun build(determinizeWorkLimit: Int, vararg strings: String): CompiledAutomaton {
        val terms = ArrayList<BytesRef>()
        for (s in strings) {
            terms.add(BytesRef(s))
        }
        terms.sort()
        val a = Automata.makeStringUnion(terms)
        return CompiledAutomaton(a, true, false, false)
    }

    private fun testFloor(c: CompiledAutomaton, input: String, expected: String?) {
        val b = BytesRef(input)
        val result = c.floor(b, BytesRefBuilder())
        if (expected == null) {
            assertNull(result)
        } else {
            assertNotNull(result)
            assertEquals(
                BytesRef(expected),
                result,
                "actual=" + result.utf8ToString() + " vs expected=" + expected + " (input=" + input + ")"
            )
        }
    }

    private fun testTerms(determinizeWorkLimit: Int, terms: Array<String>) {
        val c = build(determinizeWorkLimit, *terms)
        val termBytes = Array(terms.size) { BytesRef(terms[it]) }
        termBytes.sort()

        fun binarySearch(a: Array<BytesRef>, key: BytesRef): Int {
            var low = 0
            var high = a.size - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val cmp = a[mid].compareTo(key)
                if (cmp < 0) {
                    low = mid + 1
                } else if (cmp > 0) {
                    high = mid - 1
                } else {
                    return mid
                }
            }
            return -(low + 1)
        }

        if (VERBOSE) {
            println("\nTEST: terms in unicode order")
            for (t in termBytes) {
                println("  " + t.utf8ToString())
            }
            // println(c.utf8.toDot())
        }

        for (iter in 0 until 100 * RANDOM_MULTIPLIER) {
            val s = if (random().nextInt(10) == 1) terms[random().nextInt(terms.size)] else randomString()
            if (VERBOSE) {
                println("\nTEST: floor(" + s + ")")
            }
            var loc = binarySearch(termBytes, BytesRef(s))
            val expected: String?
            if (loc >= 0) {
                expected = s
            } else {
                loc = -(loc + 1)
                expected = if (loc == 0) null else termBytes[loc - 1].utf8ToString()
            }
            if (VERBOSE) {
                println("  expected=" + expected)
            }
            testFloor(c, s, expected)
        }
    }

    @Test
    fun testRandom() {
        val numTerms = atLeast(400)
        val terms = HashSet<String>()
        while (terms.size != numTerms) {
            terms.add(randomString())
        }
        testTerms(numTerms * 100, terms.toTypedArray())
    }

    private fun randomString(): String {
        return TestUtil.randomRealisticUnicodeString(random())
    }

    @Test
    fun testBasic() {
        val c = build(Operations.DEFAULT_DETERMINIZE_WORK_LIMIT, "fob", "foo", "goo")
        testFloor(c, "goo", "goo")
        testFloor(c, "ga", "foo")
        testFloor(c, "g", "foo")
        testFloor(c, "foc", "fob")
        testFloor(c, "foz", "foo")
        testFloor(c, "f", null)
        testFloor(c, "", null)
        testFloor(c, "aa", null)
        testFloor(c, "zzz", "goo")
    }

    // LUCENE-6367
    @Test
    fun testBinaryAll() {
        val a = Automaton()
        val state = a.createState()
        a.setAccept(state, true)
        a.addTransition(state, state, 0, 0xff)
        a.finishState()

        val ca = CompiledAutomaton(a, false, true, true)
        assertEquals(CompiledAutomaton.AUTOMATON_TYPE.ALL, ca.type)
    }

    // LUCENE-6367
    @Test
    fun testUnicodeAll() {
        val a = Automaton()
        val state = a.createState()
        a.setAccept(state, true)
        a.addTransition(state, state, 0, Character.MAX_CODE_POINT)
        a.finishState()

        val ca = CompiledAutomaton(a, false, true, false)
        assertEquals(CompiledAutomaton.AUTOMATON_TYPE.ALL, ca.type)
    }

    // LUCENE-6367
    @Test
    fun testBinarySingleton() {
        val a = Automata.makeString("foobar")
        val ca = CompiledAutomaton(a, true, true, true)
        assertEquals(CompiledAutomaton.AUTOMATON_TYPE.SINGLE, ca.type)
    }

    // LUCENE-6367
    @Test
    fun testUnicodeSingleton() {
        val a = Automata.makeString(TestUtil.randomRealisticUnicodeString(random()))
        val ca = CompiledAutomaton(a, true, true, false)
        assertEquals(CompiledAutomaton.AUTOMATON_TYPE.SINGLE, ca.type)
    }
}

