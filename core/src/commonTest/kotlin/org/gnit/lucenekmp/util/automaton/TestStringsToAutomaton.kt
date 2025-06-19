package org.gnit.lucenekmp.util.automaton

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.tests.util.automaton.MinimizationOperations
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefIterator
import org.gnit.lucenekmp.util.automaton.Automata

class TestStringsToAutomaton : LuceneTestCase() {
    @Test
    fun testBasic() {
        val terms = basicTerms()
        terms.sort()

        val a = build(terms, false)
        checkAutomaton(terms, a, false)
        checkMinimized(a)
    }

    @Test
    fun testBasicBinary() {
        val terms = basicTerms()
        terms.sort()

        val a = build(terms, true)
        checkAutomaton(terms, a, true)
        checkMinimized(a)
    }

    @Test
    fun testRandomMinimized() {
        val iters = if (LuceneTestCase.TEST_NIGHTLY) 20 else 5
        repeat(iters) {
            val buildBinary = random().nextBoolean()
            val size = random().nextInt(2, 50)
            val terms = mutableSetOf<BytesRef>()
            val automata = mutableListOf<Automaton>()
            for (j in 0 until size) {
                if (buildBinary) {
                    val bytes = ByteArray(random().nextInt(1, 9)) { random().nextInt(0, 256).toByte() }
                    val t = BytesRef(bytes)
                    terms.add(t)
                    automata.add(Automata.makeBinary(t))
                } else {
                    val s = LuceneTestCase.randomUnicodeString(random(), 8)
                    terms.add(newBytesRef(s))
                    automata.add(Automata.makeString(s))
                }
            }
            val sortedTerms = terms.toList().sorted()
            val expected =
                MinimizationOperations.minimize(Operations.union(automata), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
            val actual = build(sortedTerms, buildBinary)
            assertSameAutomaton(expected, actual)
        }
    }

    private fun checkAutomaton(expected: List<BytesRef>, a: Automaton, isBinary: Boolean) {
        val c = CompiledAutomaton(a, true, false, isBinary)
        val runAutomaton = c.runAutomaton!!
        for (t in expected) {
            val readable = if (isBinary) t.toString() else t.utf8ToString()
            assertTrue(runAutomaton.run(t.bytes, t.offset, t.length), "$readable should be found but wasn't")
        }
        // TODO: verify produced terms once FiniteStringsIterator is ported
    }

    private fun checkMinimized(a: Automaton) {
        val minimized =
            MinimizationOperations.minimize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        assertSameAutomaton(minimized, a)
    }

    private fun assertSameAutomaton(a: Automaton, b: Automaton) {
        assertEquals(a.numStates, b.numStates)
        assertEquals(a.numTransitions, b.numTransitions)
        assertTrue(AutomatonTestUtil.sameLanguage(a, b))
    }

    private fun basicTerms(): MutableList<BytesRef> {
        val terms = mutableListOf<BytesRef>()
        terms.add(newBytesRef("dog"))
        terms.add(newBytesRef("day"))
        terms.add(newBytesRef("dad"))
        terms.add(newBytesRef("cats"))
        terms.add(newBytesRef("cat"))
        return terms
    }

    private fun build(terms: Collection<BytesRef>, asBinary: Boolean): Automaton {
        return if (random().nextBoolean()) {
            StringsToAutomaton.build(terms, asBinary)
        } else {
            StringsToAutomaton.build(TermIterator(terms), asBinary)
        }
    }

    private class TermIterator(terms: Collection<BytesRef>) : BytesRefIterator {
        private val it = terms.iterator()
        override fun next(): BytesRef? {
            return if (it.hasNext()) it.next() else null
        }
    }
}

