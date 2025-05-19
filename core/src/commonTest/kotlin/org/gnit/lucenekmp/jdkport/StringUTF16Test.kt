package org.gnit.lucenekmp.jdkport

import kotlin.test.*

class StringUTF16Test {

    @Test
    fun testToBytesAndGetChar() {
        val chars = charArrayOf('A', 'B', 'C', '\u0100', '\u00FF')
        val bytes = StringUTF16.toBytes(chars, 0, chars.size)
        assertEquals(chars.size, StringUTF16.length(bytes))
        for (i in chars.indices) {
            assertEquals(chars[i], StringUTF16.getChar(bytes, i))
        }
    }

    @Test
    fun testNewBytesLength() {
        assertEquals(0, StringUTF16.newBytesLength(0))
        assertEquals(2, StringUTF16.newBytesLength(1))
        assertEquals(10, StringUTF16.newBytesLength(5))
        assertFailsWith<Exception> { StringUTF16.newBytesLength(-1) }
    }

    @Test
    fun testPutCharAndCharAt() {
        val arr = ByteArray(4)
        StringUTF16.putChar(arr, 0, 'X'.code)
        StringUTF16.putChar(arr, 1, 'Y'.code)
        assertEquals('X', StringUTF16.charAt(arr, 0))
        assertEquals('Y', StringUTF16.charAt(arr, 1))
    }

    @Test
    fun testCompressLatin1() {
        val chars = charArrayOf('a', 'b', 'c', 'd')
        val compressed = StringUTF16.compress(chars, 0, chars.size)
        assertContentEquals(byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte()), compressed)
    }

    @Test
    fun testCompressNonLatin1() {
        val chars = charArrayOf('a', 'b', '\u0100', 'd')
        val compressed = StringUTF16.compress(chars, 0, chars.size)
        // Should be UTF16 encoded, so length is 8
        assertEquals(8, compressed.size)
        val decoded = CharArray(4) { StringUTF16.getChar(compressed, it) }
        assertContentEquals(chars, decoded)
    }

    @Test
    fun testCompressByteToByte() {
        val chars = charArrayOf('a', 'b', 'c', '\u0100')
        val bytes = StringUTF16.toBytes(chars, 0, chars.size)
        val latin1 = ByteArray(chars.size)
        val ndx = StringUTF16.compressByteToByte(bytes, 0, latin1, 0, chars.size)
        assertEquals(3, ndx)
        assertEquals('a'.code.toByte(), latin1[0])
        assertEquals('b'.code.toByte(), latin1[1])
        assertEquals('c'.code.toByte(), latin1[2])
    }

    @Test
    fun testInflate() {
        val latin1 = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        val inflated = ByteArray(6)
        StringUTF16.inflate(latin1, 0, inflated, 0, 3)
        assertEquals('a', StringUTF16.getChar(inflated, 0))
        assertEquals('b', StringUTF16.getChar(inflated, 1))
        assertEquals('c', StringUTF16.getChar(inflated, 2))
    }
}
