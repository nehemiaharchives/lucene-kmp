package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestIntSet : LuceneTestCase() {

    @Test
    fun testFreezeEqualitySmallSet() {
        testFreezeEquality(10)
    }

    @Test
    fun testFreezeEqualityLargeSet() {
        testFreezeEquality(100)
    }

    private fun testFreezeEquality(size: Int) {
        val stateSet = StateSet(0)
        for (i in 0 until size) {
            // Some duplicates is nice but not critical
            stateSet.incr(random().nextInt(i + 1))
        }

        val frozen0: IntSet = stateSet.freeze(0)
        assertEquals(stateSet, frozen0, "Frozen set not equal to origin sorted set.")
        assertEquals(frozen0, stateSet, "Symmetry: Sorted set not equal to frozen set.")

        val frozen1: IntSet = stateSet.freeze(random().nextInt())
        assertEquals(stateSet, frozen1, "Sorted set modified while freezing?")
        assertEquals(frozen0, frozen1, "Frozen sets were not equal")
    }

    @Test
    fun testMapCutover() {
        val set = StateSet(10)
        for (i in 0 until 35) {
            // No duplicates so there are enough elements to trigger impl cutover
            set.incr(i)
        }

        assertTrue(set.size() > 32)

        for (i in 0 until 35) {
            // This is pretty much the worst case, perf wise
            set.decr(i)
        }

        assertTrue(set.size() == 0)
    }

    @Test
    fun testModify() {
        val set = StateSet(2)
        set.incr(1)
        set.incr(2)

        val set2: FrozenIntSet = set.freeze(0)
        assertEquals(set as IntSet, set2 as IntSet)

        set.incr(1)
        assertEquals(set as IntSet, set2 as IntSet)

        set.decr(1)
        assertEquals(set as IntSet, set2 as IntSet)

        set.decr(1)
        assertNotEquals(set as IntSet, set2 as IntSet)
    }

    @Test
    fun testHashCode() {
        val set = StateSet(1000)
        val set2 = StateSet(100)
        for (i in 0 until 100) {
            set.incr(i)
            set2.incr(99 - i)
        }
        assertEquals(set.hashCode(), set2.hashCode())
    }
}

