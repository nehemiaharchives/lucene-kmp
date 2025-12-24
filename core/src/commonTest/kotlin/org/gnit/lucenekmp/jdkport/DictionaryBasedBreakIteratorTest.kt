package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DictionaryBasedBreakIteratorTest {

    @Test
    fun testThaiWordIteratorBoundaryConsistency() {
        val provider = BreakIteratorProviderImpl(
            LocaleProviderAdapter.Type.JRE,
            setOf("en")
        )
        val thai = Locale("th", "TH", "TH")
        val iterator = provider.getWordInstance(thai)

        assertTrue(iterator is DictionaryBasedBreakIterator)

        val text = "ภาษาไทยภาษาไทย"
        iterator.setText(text)

        val boundaries = collectBoundaries(iterator, text.length)
        assertEquals(0, boundaries.first())
        assertEquals(text.length, boundaries.last())
        for (i in 1 until boundaries.size) {
            assertTrue(boundaries[i] >= boundaries[i - 1])
        }

        for (i in 0..text.length) {
            val expectedFollowing = boundaries.firstOrNull { it > i } ?: BreakIterator.DONE
            val expectedPreceding = boundaries.lastOrNull { it < i } ?: BreakIterator.DONE
            assertEquals(expectedFollowing, iterator.following(i))
            assertEquals(expectedPreceding, iterator.preceding(i))
        }
    }

    @Test
    fun testClonePreservesState() {
        val provider = BreakIteratorProviderImpl(
            LocaleProviderAdapter.Type.JRE,
            setOf("en")
        )
        val thai = Locale("th", "TH", "TH")
        val iterator = provider.getWordInstance(thai)

        assertTrue(iterator is DictionaryBasedBreakIterator)

        val text = "ภาษาไทยภาษาไทย"
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

    private fun collectBoundaries(iterator: BreakIterator, length: Int): List<Int> {
        val boundaries = mutableListOf<Int>()
        boundaries.add(iterator.first())
        while (true) {
            val next = iterator.next()
            if (next == BreakIterator.DONE) {
                break
            }
            boundaries.add(next)
        }
        if (boundaries.last() != length) {
            boundaries.add(length)
        }
        return boundaries
    }
}
