package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCharFilter : LuceneTestCase() {
    private class CharFilter1(input: Reader) : CharFilter(input) {
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            return input.read(cbuf, off, len)
        }

        override fun correct(currentOff: Int): Int {
            return currentOff + 1
        }
    }

    private class CharFilter2(input: Reader) : CharFilter(input) {
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            return input.read(cbuf, off, len)
        }

        override fun correct(currentOff: Int): Int {
            return currentOff + 2
        }
    }

    @Test
    fun testCharFilter1() {
        val cs: CharFilter = CharFilter1(StringReader(""))
        assertEquals(1, cs.correctOffset(0), "corrected offset is invalid")
    }

    @Test
    fun testCharFilter2() {
        val cs: CharFilter = CharFilter2(StringReader(""))
        assertEquals(2, cs.correctOffset(0), "corrected offset is invalid")
    }

    @Test
    fun testCharFilter12() {
        val cs: CharFilter = CharFilter2(CharFilter1(StringReader("")))
        assertEquals(3, cs.correctOffset(0), "corrected offset is invalid")
    }

    @Test
    fun testCharFilter11() {
        val cs: CharFilter = CharFilter1(CharFilter1(StringReader("")))
        assertEquals(2, cs.correctOffset(0), "corrected offset is invalid")
    }
}
