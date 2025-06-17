package org.gnit.lucenekmp.util.automaton

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefIterator

class TestStringsToAutomaton : LuceneTestCase() {
    @Test
    fun testBasic() {
        val terms = basicTerms()
        terms.sort()

        val a = build(terms, false)
        checkAutomaton(terms, a, false)
        checkMinimized(a)
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
        // TODO: MinimizationOperations not yet ported
        val minimized = Operations.removeDeadStates(a)
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

