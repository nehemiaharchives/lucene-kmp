package org.gnit.lucenekmp.util.automaton

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.fail
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.Util

class TestLimitedFiniteStringsIterator : LuceneTestCase() {

    private fun getFiniteStrings(iterator: FiniteStringsIterator): MutableList<IntsRef> {
        val result = mutableListOf<IntsRef>()
        var s: IntsRef?
        while (iterator.next().also { s = it } != null) {
            result.add(IntsRef.deepCopyOf(s!!))
        }
        return result
    }
    @Test
    fun testRandomFiniteStrings() {
        val iters = atLeast(100)
        for (i in 0 until iters) {
            val a = AutomatonTestUtil.randomAutomaton(random())
            try {
                TestOperations.getFiniteStrings(a, TestUtil.nextInt(random(), 1, 1000))
            } catch (iae: IllegalArgumentException) {
                assertFalse(AutomatonTestUtil.isFinite(a))
            }
        }
    }

    @Test
    fun testInvalidLimitNegative() {
        val a = AutomatonTestUtil.randomAutomaton(random())
        assertFailsWith<IllegalArgumentException> {
            LimitedFiniteStringsIterator(a, -7)
            fail("did not hit exception")
        }
    }

    @Test
    fun testInvalidLimitNull() {
        val a = AutomatonTestUtil.randomAutomaton(random())
        assertFailsWith<IllegalArgumentException> {
            LimitedFiniteStringsIterator(a, 0)
        }
    }

    @Test
    fun testSingleton() {
        val a = Automata.makeString("foobar")
        val actual = TestOperations.getFiniteStrings(a, 1)
        assertEquals(1, actual.size)
        val scratch = IntsRefBuilder()
        Util.toUTF32("foobar".toCharArray(), 0, 6, scratch)
        assertTrue(actual.contains(scratch.get()))
    }

    @Test
    fun testLimit() {
        val a = Operations.union(mutableListOf(Automata.makeString("foo"), Automata.makeString("bar")))

        // Test without limit
        assertEquals(2, TestOperations.getFiniteStrings(a, -1).size)

        // Test with limit
        assertEquals(1, TestOperations.getFiniteStrings(a, 1).size)
    }

    @Test
    fun testSize() {
        val a = Operations.union(mutableListOf(Automata.makeString("foo"), Automata.makeString("bar")))
        val iterator = LimitedFiniteStringsIterator(a, -1)
        val actual = getFiniteStrings(iterator)
        assertEquals(2, actual.size)
        assertEquals(2, iterator.size())
    }
}
