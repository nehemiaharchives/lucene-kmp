package org.gnit.lucenekmp.jdkport

import kotlin.test.*

class JavaLangAccessTest {

    @Test
    fun testDecodeASCII_basic() {
        val src = byteArrayOf(65, 66, 67) // 'A', 'B', 'C'
        val dst = CharArray(3)
        val count = JavaLangAccess.decodeASCII(src, 0, dst, 0, 3)
        assertEquals(3, count)
        assertContentEquals(charArrayOf('A', 'B', 'C'), dst)
    }

    @Test
    fun testDecodeASCII_partialDueToNonAscii() {
        val src = byteArrayOf(65, 66, -1, 67) // 'A', 'B', non-ASCII, 'C'
        val dst = CharArray(4)
        val count = JavaLangAccess.decodeASCII(src, 0, dst, 0, 4)
        assertEquals(2, count)
        assertEquals('A', dst[0])
        assertEquals('B', dst[1])
    }

    @Test
    fun testDecodeASCII_nullDst() {
        val src = byteArrayOf(65, 66, 67)
        val count = JavaLangAccess.decodeASCII(src, 0, null, 0, 3)
        assertEquals(0, count)
    }

    @Test
    fun testDecodeASCII_offsetAndLen() {
        val src = byteArrayOf(88, 65, 66, 67, 89)
        val dst = CharArray(2)
        val count = JavaLangAccess.decodeASCII(src, 1, dst, 0, 2)
        assertEquals(2, count)
        assertContentEquals(charArrayOf('A', 'B'), dst)
    }

    @Test
    fun testEncodeASCII_basic() {
        val src = charArrayOf('A', 'B', 'C')
        val dst = ByteArray(3)
        val count = JavaLangAccess.encodeASCII(src, 0, dst, 0, 3)
        assertEquals(3, count)
        assertContentEquals(byteArrayOf(65, 66, 67), dst)
    }

    @Test
    fun testEncodeASCII_partialDueToNonAscii() {
        val src = charArrayOf('A', 'B', 'Ç', 'C') // 'Ç' is non-ASCII
        val dst = ByteArray(4)
        val count = JavaLangAccess.encodeASCII(src, 0, dst, 0, 4)
        assertEquals(2, count)
        assertEquals(65, dst[0])
        assertEquals(66, dst[1])
    }

    @Test
    fun testEncodeASCII_nullDst() {
        val src = charArrayOf('A', 'B', 'C')
        val count = JavaLangAccess.encodeASCII(src, 0, null, 0, 3)
        assertEquals(0, count)
    }

    @Test
    fun testEncodeASCII_offsetAndLen() {
        val src = charArrayOf('X', 'A', 'B', 'C', 'Y')
        val dst = ByteArray(2)
        val count = JavaLangAccess.encodeASCII(src, 1, dst, 0, 2)
        assertEquals(2, count)
        assertContentEquals(byteArrayOf(65, 66), dst)
    }

    @Test
    fun testInflateBytesToChars_basic() {
        val src = byteArrayOf(65, 66, 67)
        val dst = CharArray(3)
        JavaLangAccess.inflateBytesToChars(src, 0, dst, 0, 3)
        assertContentEquals(charArrayOf('A', 'B', 'C'), dst)
    }

    @Test
    fun testInflateBytesToChars_nullSrcOrDst() {
        val dst = CharArray(3) { 'x' }
        JavaLangAccess.inflateBytesToChars(null, 0, dst, 0, 3)
        assertContentEquals(charArrayOf('x', 'x', 'x'), dst)
        JavaLangAccess.inflateBytesToChars(byteArrayOf(65, 66, 67), 0, null, 0, 3)
        // No exception should be thrown
    }

    @Test
    fun testInflateBytesToChars_offsetAndLen() {
        val src = byteArrayOf(88, 65, 66, 67, 89)
        val dst = CharArray(2)
        JavaLangAccess.inflateBytesToChars(src, 1, dst, 0, 2)
        assertContentEquals(charArrayOf('A', 'B'), dst)
    }
}
