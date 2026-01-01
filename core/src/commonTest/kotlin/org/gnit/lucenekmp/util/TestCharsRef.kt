package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCharsRef : LuceneTestCase() {

    @Test
    fun testUTF16InUTF8Order() {
        val numStrings = atLeast(1000)
        val utf8 = Array(numStrings) { BytesRef() }
        val utf16 = Array(numStrings) { CharsRef() }

        fun randomAscii(max: Int): String {
            val len = TestUtil.nextInt(random(), 0, max)
            val sb = StringBuilder(len)
            repeat(len) { sb.append((random().nextInt(32, 127)).toChar()) }
            return sb.toString()
        }
        for (i in 0 until numStrings) {
            val s = randomAscii(20)
            utf8[i] = BytesRef(s)
            utf16[i] = CharsRef(s)
        }

        Arrays.sort(utf8)
        Arrays.sort(utf16, CharsRef.UTF16SortedAsUTF8Comparator)

        for (i in 0 until numStrings) {
            val s1 = utf8[i].utf8ToString()
            val s2 = utf16[i].toString()
            if (s1 != s2) {
                println("Mismatch index $i\nutf8: $s1\nutf16: $s2")
            }
            assertEquals(s1, s2, "mismatch at index $i: $s1 vs $s2")
        }
    }

    @Test
    fun testAppend() {
        val ref = CharsRefBuilder()
        val builder = StringBuilder()
        val numStrings = atLeast(10)
        for (i in 0 until numStrings) {
            val charArray = TestUtil.randomUnicodeString(random(), 100).ifEmpty { "a" }.toCharArray()
            val offset = random().nextInt(charArray.size)
            val length = charArray.size - offset
            builder.appendRange(charArray, offset, offset + length)
            ref.append(charArray, offset, length)
        }
        assertEquals(builder.toString(), ref.get().toString())
    }

    @Test
    fun testCopy() {
        val numIters = atLeast(10)
        for (i in 0 until numIters) {
            val ref = CharsRefBuilder()
            val charArray = TestUtil.randomUnicodeString(random(), 100).ifEmpty { "a" }.toCharArray()
            val offset = random().nextInt(charArray.size)
            val length = charArray.size - offset
            val str = charArray.concatToString(offset, offset + length)
            ref.copyChars(charArray, offset, length)
            assertEquals(str, ref.toString())
        }
    }

    @Test
    fun testCharSequenceCharAt() {
        val c = CharsRef("abc")
        assertEquals('b', c[1])
        expectThrows(IndexOutOfBoundsException::class) { c[-1] }
        expectThrows(IndexOutOfBoundsException::class) { c[3] }
    }

    @Test
    fun testCharSequenceSubSequence() {
        val sequences: Array<CharSequence> = arrayOf(
            CharsRef("abc"),
            CharsRef("0abc".toCharArray(), 1, 3),
            CharsRef("abc0".toCharArray(), 0, 3),
            CharsRef("0abc0".toCharArray(), 1, 3)
        )
        for (c in sequences) {
            doTestSequence(c)
        }
    }

    private fun doTestSequence(c: CharSequence) {
        assertEquals("a", c.subSequence(0, 1).toString())
        assertEquals("b", c.subSequence(1, 2).toString())
        assertEquals("bc", c.subSequence(1, 3).toString())
        assertEquals("", c.subSequence(0, 0).toString())
        expectThrows(IndexOutOfBoundsException::class) { c.subSequence(-1, 1) }
        expectThrows(IndexOutOfBoundsException::class) { c.subSequence(0, -1) }
        expectThrows(IndexOutOfBoundsException::class) { c.subSequence(0, 4) }
        expectThrows(IndexOutOfBoundsException::class) { c.subSequence(2, 1) }
    }

    @Test
    fun testInvalidDeepCopy() {
        val from = CharsRef(charArrayOf('a', 'b'), 0, 2)
        from.offset += 1
        expectThrows(IndexOutOfBoundsException::class) { CharsRef.deepCopyOf(from) }
    }
}
