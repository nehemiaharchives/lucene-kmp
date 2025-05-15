package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StringBufferTest {
    @Test
    fun testEmptyConstructor() {
        val sb = StringBuffer()
        assertEquals(0, sb.length)
        assertEquals("", sb.toString())
    }

    @Test
    fun testStringConstructor() {
        val sb = StringBuffer("abc")
        assertEquals(3, sb.length)
        assertEquals("abc", sb.toString())
    }

    @Test
    fun testAppendCharAndString() {
        val sb = StringBuffer()
        sb.append('a').append("bc")
        assertEquals("abc", sb.toString())
    }

    @Test
    fun testAppendAny() {
        val sb = StringBuffer()
        sb.append(123)
        sb.append(null)
        assertEquals("123null", sb.toString())
    }

    @Test
    fun testInsert() {
        val sb = StringBuffer("ac")
        sb.insert(1, "b")
        assertEquals("abc", sb.toString())
    }

    @Test
    fun testSetCharAt() {
        val sb = StringBuffer("abc")
        sb.setCharAt(1, 'x')
        assertEquals("axc", sb.toString())
        assertFailsWith<IndexOutOfBoundsException> { sb.setCharAt(3, 'z') }
    }

    @Test
    fun testGet() {
        val sb = StringBuffer("abc")
        assertEquals('a', sb[0])
        assertEquals('b', sb[1])
        assertEquals('c', sb[2])
        assertFailsWith<IndexOutOfBoundsException> { sb[-1] }
        assertFailsWith<IndexOutOfBoundsException> { sb[3] }
    }

    @Test
    fun testSubSequence() {
        val sb = StringBuffer("abcdef")
        assertEquals("bcd", sb.subSequence(1, 4).toString())
        assertFailsWith<IndexOutOfBoundsException> { sb.subSequence(-1, 2) }
        assertFailsWith<IndexOutOfBoundsException> { sb.subSequence(2, 7) }
        assertFailsWith<IndexOutOfBoundsException> { sb.subSequence(4, 2) }
    }

    @Test
    fun testGetChars() {
        val sb = StringBuffer("abcdef")
        val dst = CharArray(3)
        sb.getChars(1, 4, dst, 0)
        assertEquals(charArrayOf('b', 'c', 'd').toList(), dst.toList())
        assertFailsWith<IndexOutOfBoundsException> { sb.getChars(-1, 2, dst, 0) }
        assertFailsWith<IndexOutOfBoundsException> { sb.getChars(1, 7, dst, 0) }
        assertFailsWith<IndexOutOfBoundsException> { sb.getChars(3, 2, dst, 0) }
        assertFailsWith<IndexOutOfBoundsException> { sb.getChars(1, 4, dst, 1) }
    }

    @Test
    fun testCompareTo() {
        val sb1 = StringBuffer("abc")
        val sb2 = StringBuffer("abd")
        val sb3 = StringBuffer("abc")
        assertEquals(true, sb1 < sb2)
        assertEquals(true, sb2 > sb1)
        assertEquals(0, sb1.compareTo(sb3))
    }
}
