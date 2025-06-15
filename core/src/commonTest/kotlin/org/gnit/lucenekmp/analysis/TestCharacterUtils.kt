package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.analysis.CharacterUtils.CharacterBuffer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestCharacterUtils : LuceneTestCase() {

    @Test
    fun testLowerUpper() {
        val reader = StringReader("ABc")
        val buffer = CharacterUtils.newCharacterBuffer(3)
        assertTrue(CharacterUtils.fill(buffer, reader))
        assertEquals(3, buffer.length)
        CharacterUtils.toLowerCase(buffer.buffer, 1, 3)
        assertEquals("Abc", buffer.buffer.concatToString())
        CharacterUtils.toUpperCase(buffer.buffer, 1, 3)
        assertEquals("ABC", buffer.buffer.concatToString())
    }

    @Test
    fun testConversions() {
        val orig = TestUtil.randomUnicodeString(random(), 100).toCharArray()
        val buf = IntArray(orig.size)
        val restored = CharArray(buf.size)
        val o1 = TestUtil.nextInt(random(), 0, kotlin.math.min(5, orig.size))
        val o2 = TestUtil.nextInt(random(), 0, o1)
        val o3 = TestUtil.nextInt(random(), 0, o1)
        val codePointCount = CharacterUtils.toCodePoints(orig, o1, orig.size - o1, buf, o2)
        val charCount = CharacterUtils.toChars(buf, o2, codePointCount, restored, o3)
        assertEquals(orig.size - o1, charCount)
        assertEquals(
            ArrayUtil.copyOfSubArray(orig, o1, o1 + charCount).concatToString(),
            ArrayUtil.copyOfSubArray(restored, o3, o3 + charCount).concatToString()
        )
    }

    @Test
    fun testNewCharacterBuffer() {
        var newCharacterBuffer = CharacterUtils.newCharacterBuffer(1024)
        assertEquals(1024, newCharacterBuffer.buffer.size)
        assertEquals(0, newCharacterBuffer.offset)
        assertEquals(0, newCharacterBuffer.length)

        newCharacterBuffer = CharacterUtils.newCharacterBuffer(2)
        assertEquals(2, newCharacterBuffer.buffer.size)
        assertEquals(0, newCharacterBuffer.offset)
        assertEquals(0, newCharacterBuffer.length)

        expectThrows(IllegalArgumentException::class) {
            CharacterUtils.newCharacterBuffer(1)
        }
    }

    @Test
    fun testFillNoHighSurrogate() {
        val reader = StringReader("helloworld")
        val buffer = CharacterUtils.newCharacterBuffer(6)
        assertTrue(CharacterUtils.fill(buffer, reader))
        assertEquals(0, buffer.offset)
        assertEquals(6, buffer.length)
        assertEquals("hellow", buffer.buffer.concatToString())
        assertFalse(CharacterUtils.fill(buffer, reader))
        assertEquals(4, buffer.length)
        assertEquals(0, buffer.offset)
        assertEquals("orld", buffer.buffer.concatToString(buffer.offset, buffer.offset + buffer.length))
        assertFalse(CharacterUtils.fill(buffer, reader))
    }

    @Test
    fun testFill() {
        val input = "1234\uD801\uDC1c789123\uD801\uD801\uDC1c\uD801"
        val reader = StringReader(input)
        val buffer = CharacterUtils.newCharacterBuffer(5)
        assertTrue(CharacterUtils.fill(buffer, reader))
        assertEquals(4, buffer.length)
        assertEquals("1234", buffer.buffer.concatToString(buffer.offset, buffer.offset + buffer.length))
        assertTrue(CharacterUtils.fill(buffer, reader))
        assertEquals(5, buffer.length)
        assertEquals("\uD801\uDC1c789", buffer.buffer.concatToString())
        assertTrue(CharacterUtils.fill(buffer, reader))
        assertEquals(4, buffer.length)
        assertEquals("123\uD801", buffer.buffer.concatToString(buffer.offset, buffer.offset + buffer.length))
        assertFalse(CharacterUtils.fill(buffer, reader))
        assertEquals(3, buffer.length)
        assertEquals("\uD801\uDC1c\uD801", buffer.buffer.concatToString(buffer.offset, buffer.offset + buffer.length))
        assertFalse(CharacterUtils.fill(buffer, reader))
        assertEquals(0, buffer.length)
    }
}

