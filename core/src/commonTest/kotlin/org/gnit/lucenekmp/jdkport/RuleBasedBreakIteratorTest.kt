package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleBasedBreakIteratorTest {

    @Test
    fun testBasicIterationAndBoundaries() {
        val iterator = RuleBasedBreakIterator(ruleBasedBreakIteratorData)
        val text = "Hello world."
        iterator.setText(text)

        val boundaries = collectBoundaries(iterator)
        assertEquals(0, boundaries.first())
        assertEquals(text.length, boundaries.last())

        for (i in 1 until boundaries.size) {
            assertTrue(boundaries[i] > boundaries[i - 1])
        }

        for (i in 0..text.length) {
            val expectedFollowing = boundaries.firstOrNull { it > i } ?: BreakIterator.DONE
            val expectedPreceding = boundaries.lastOrNull { it < i } ?: BreakIterator.DONE
            assertEquals(expectedFollowing, iterator.following(i))
            assertEquals(expectedPreceding, iterator.preceding(i))
            val isBoundary = boundaries.contains(i)
            assertEquals(isBoundary, iterator.isBoundary(i))
        }
    }

    @Test
    fun testClonePreservesState() {
        val iterator = RuleBasedBreakIterator(ruleBasedBreakIteratorData)
        val text = "A simple test."
        iterator.setText(text)
        iterator.first()
        val pos1 = iterator.next()
        val pos2 = iterator.next()

        val cloned = iterator.clone() as BreakIterator
        assertEquals(pos2, cloned.current())
        assertEquals(iterator.next(), cloned.next())
        assertEquals(iterator.previous(), cloned.previous())
        assertEquals(pos1, iterator.previous())
    }

    @Test
    fun testSetTextResetsIteration() {
        val iterator = RuleBasedBreakIterator(ruleBasedBreakIteratorData)
        iterator.setText("one two")
        iterator.next()
        iterator.setText("three")
        assertEquals(0, iterator.first())
        assertEquals(0, iterator.current())
    }

    private fun collectBoundaries(iterator: BreakIterator): List<Int> {
        val boundaries = mutableListOf<Int>()
        boundaries.add(iterator.first())
        while (true) {
            val next = iterator.next()
            if (next == BreakIterator.DONE) {
                break
            }
            boundaries.add(next)
        }
        return boundaries
    }
}
