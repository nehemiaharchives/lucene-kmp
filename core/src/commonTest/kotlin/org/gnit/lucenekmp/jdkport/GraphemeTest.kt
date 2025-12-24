package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GraphemeTest {

    @Test
    fun testNextBoundaryAscii() {
        val text = "abc"
        assertEquals(1, Grapheme.nextBoundary(text, 0, text.length))
        assertEquals(2, Grapheme.nextBoundary(text, 1, text.length))
        assertEquals(3, Grapheme.nextBoundary(text, 2, text.length))
    }

    @Test
    fun testNextBoundaryCombiningMark() {
        val text = "a\u0308" // 'a' + COMBINING DIAERESIS
        assertEquals(2, Grapheme.nextBoundary(text, 0, text.length))
    }

    @Test
    fun testNextBoundaryRegionalIndicatorPair() {
        val text = "\uD83C\uDDFA\uD83C\uDDF8" // 🇺🇸
        assertEquals(text.length, Grapheme.nextBoundary(text, 0, text.length))
    }

    @Test
    fun testNextBoundaryHangulLVT() {
        val text = "\u1100\u1161" // L + V (Hangul)
        assertEquals(2, Grapheme.nextBoundary(text, 0, text.length))
    }

    @Test
    fun testNextBoundaryCrLf() {
        val text = "\r\n"
        assertEquals(2, Grapheme.nextBoundary(text, 0, text.length))
    }

    @Test
    fun testExcludedSpacingMark() {
        assertTrue(Grapheme.isExcludedSpacingMark(0x102B))
    }

    @Test
    fun testNextBoundaryInvalidRangeThrows() {
        assertFailsWith<IndexOutOfBoundsException> {
            Grapheme.nextBoundary("abc", -1, 2)
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Grapheme.nextBoundary("abc", 2, 1)
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Grapheme.nextBoundary("abc", 0, 4)
        }
    }
}
