package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class TestDocIdSetIterator : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testRangeBasic() {
        val disi = DocIdSetIterator.range(5, 8)
        assertEquals(-1, disi.docID())
        assertEquals(5, disi.nextDoc())
        assertEquals(6, disi.nextDoc())
        assertEquals(7, disi.nextDoc())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, disi.nextDoc())
    }

    @Test
    @Throws(Exception::class)
    fun testInvalidRange() {
        assertFailsWith<IllegalArgumentException> {
            DocIdSetIterator.range(5, 4)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testInvalidMin() {
        assertFailsWith<IllegalArgumentException> {
            DocIdSetIterator.range(-1, 4)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testEmpty() {
        assertFailsWith<IllegalArgumentException> {
            DocIdSetIterator.range(7, 7)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testAdvance() {
        val disi = DocIdSetIterator.range(5, 20)
        assertEquals(-1, disi.docID())
        assertEquals(5, disi.nextDoc())
        assertEquals(17, disi.advance(17))
        assertEquals(18, disi.nextDoc())
        assertEquals(19, disi.nextDoc())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, disi.nextDoc())
    }

    @Test
    @Throws(Exception::class)
    fun testIntoBitset() {
        for (i in 0..<10) {
            val max = 1 + random().nextInt(500)
            val expectedDisi: DocIdSetIterator
            val actualDisi: DocIdSetIterator
            if ((i and 1) == 0) {
                val min = random().nextInt(max)
                expectedDisi = DocIdSetIterator.range(min, max)
                actualDisi = DocIdSetIterator.range(min, max)
            } else {
                expectedDisi = DocIdSetIterator.all(max)
                actualDisi = DocIdSetIterator.all(max)
            }
            val expected = FixedBitSet(max * 2)
            val actual = FixedBitSet(max * 2)
            var doc = -1
            expectedDisi.nextDoc()
            actualDisi.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                val r = random().nextInt(3)
                when (r) {
                    0 -> {
                        expectedDisi.nextDoc()
                        actualDisi.nextDoc()
                    }

                    1 -> {
                        val jump = expectedDisi.docID() + random().nextInt(5)
                        expectedDisi.advance(jump)
                        actualDisi.advance(jump)
                    }

                    2 -> {
                        expected.clear()
                        actual.clear()
                        val upTo =
                            if (random().nextBoolean()) {
                                expectedDisi.docID() - 1
                            } else {
                                expectedDisi.docID() + random().nextInt(5)
                            }
                        val offset = expectedDisi.docID() - random().nextInt(max)
                        // use the default impl of intoBitSet
                        FilterDocIdSetIterator(expectedDisi).intoBitSet(upTo, expected, offset)
                        actualDisi.intoBitSet(upTo, actual, offset)
                        assertContentEquals(expected.bits, actual.bits)
                    }
                }
                assertEquals(expectedDisi.docID(), actualDisi.docID())
                doc = expectedDisi.docID()
            }
        }
    }
}
