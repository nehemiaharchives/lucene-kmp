package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


/**
 * tests functions of [CharBuffer] if it behaves like [java.nio.CharBuffer](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/nio/CharBuffer.html)
 */
class CharBufferTest {

    @Test
    fun testAllocate() {
        val buffer = CharBuffer.allocate(10)
        assertEquals(10, buffer.capacity)
        assertEquals(0, buffer.position)
        assertEquals(10, buffer.limit)
    }

    @Test
    fun testWrap() {
        val array = charArrayOf('a', 'b', 'c', 'd', 'e')
        val buffer = CharBuffer.wrap(array)
        assertEquals(5, buffer.capacity)
        assertEquals(0, buffer.position)
        assertEquals(5, buffer.limit)
        assertEquals('a', buffer.get())
    }

    @Test
    fun testGetPut() {
        val buffer = CharBuffer.allocate(10)
        buffer.put('a')
        buffer.put('b')
        buffer.put('c')
        assertEquals('a', buffer.getAbsolute(0))
        assertEquals('b', buffer.getAbsolute(1))
        assertEquals('c', buffer.getAbsolute(2))
    }

    @Test
    fun testFlip() {
        val buffer = CharBuffer.allocate(10)
        buffer.put('a')
        buffer.put('b')
        buffer.put('c')
        buffer.flip()
        assertEquals(0, buffer.position)
        assertEquals(3, buffer.limit)
        assertEquals('a', buffer.get())
    }

    @Test
    fun testClear() {
        val buffer = CharBuffer.allocate(10)
        buffer.put('a')
        buffer.put('b')
        buffer.put('c')
        buffer.clear()
        assertEquals(0, buffer.position)
        assertEquals(10, buffer.limit)
    }

    @Test
    fun testCompact() {
        val buffer = CharBuffer.allocate(10)
        buffer.put('a')
        buffer.put('b')
        buffer.put('c')
        buffer.flip()
        buffer.get()
        buffer.compact()
        assertEquals(2, buffer.position)
        assertEquals(10, buffer.limit)
        assertEquals('b', buffer.getAbsolute(0))
    }

    @Test
    fun testMarkReset() {
        val buffer = CharBuffer.allocate(10)
        buffer.put('a')        // position = 1
        buffer.put('b')        // position = 2
        buffer.mark()          // mark set at position 2
        buffer.put('c')        // position = 3
        buffer.reset()         // position restored to 2
        assertEquals(2, buffer.position)
    }

    @Test
    fun testPositionLimit() {
        val buffer = CharBuffer.allocate(10)
        buffer.position = 5
        assertEquals(5, buffer.position)
        buffer.limit = 7
        assertEquals(7, buffer.limit)
    }

    @Test
    fun testRemaining() {
        val buffer = CharBuffer.allocate(10)
        buffer.position = 5
        assertEquals(5, buffer.remaining())
    }

    @Test
    fun testHasRemaining() {
        val buffer = CharBuffer.allocate(10)
        buffer.position = 5
        assertTrue(buffer.hasRemaining())
    }

    @Test
    fun testIsReadOnly() {
        val buffer = CharBuffer.allocate(10)
        val readOnlyBuffer = buffer.asReadOnlyBuffer()
        assertFailsWith<ReadOnlyBufferException> {
            readOnlyBuffer.put('a')
        }
    }

    @Test
    fun testSlice() {
        val buffer = CharBuffer.allocate(10)
        buffer.position = 5
        val slice = buffer.slice()
        assertEquals(5, slice.capacity)
        assertEquals(0, slice.position)
        assertEquals(5, slice.limit)
    }

    @Test
    fun testDuplicate() {
        val buffer = CharBuffer.allocate(10)
        buffer.put('a')
        buffer.put('b')
        val duplicate = buffer.duplicate()
        assertEquals(10, duplicate.capacity)
        assertEquals(2, duplicate.position)
        assertEquals(10, duplicate.limit)
        assertEquals('a', duplicate.getAbsolute(0))
    }

    @Test
    fun testArray() {
        val array = charArrayOf('a', 'b', 'c', 'd', 'e')
        val buffer = CharBuffer.wrap(array)
        val arrayCopy = buffer.array()
        assertTrue(array contentEquals arrayCopy)
    }

    @Test
    fun testArrayOffset() {
        val array = charArrayOf('a', 'b', 'c', 'd', 'e')
        val buffer = CharBuffer.wrap(array)
        assertEquals(0, buffer.arrayOffset())
    }

    @Test
    fun testCharBufferCompareTo() {
        val charsABC = charArrayOf('a', 'b', 'c')
        val charsABD = charArrayOf('a', 'b', 'd')
        val buffer1 = CharBuffer.wrap(charsABC)
        val buffer2 = CharBuffer.wrap(charsABD)
        assertTrue(buffer1 < buffer2)
    }

    @Test
    fun testEquals() {
        val buffer1 = CharBuffer.wrap(charArrayOf('a', 'b', 'c'))
        val buffer2 = CharBuffer.wrap(charArrayOf('a', 'b', 'c'))
        assertEquals(buffer1, buffer2)
    }

    @Test
    fun testHashCode() {
        val buffer = CharBuffer.wrap(charArrayOf('a', 'b', 'c'))
        assertEquals(buffer.hashCode(), buffer.hashCode())
    }

    @Test
    fun testToString() {
        val buffer = CharBuffer.wrap(charArrayOf('a', 'b', 'c'))
        assertEquals("abc", buffer.toString())
    }
}
