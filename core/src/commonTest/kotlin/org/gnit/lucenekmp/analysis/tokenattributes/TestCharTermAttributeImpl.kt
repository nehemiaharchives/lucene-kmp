package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.jdkport.CharBuffer
import org.gnit.lucenekmp.jdkport.StringBuffer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TestCharTermAttributeImpl : LuceneTestCase() {

    @Test
    fun testResize() {
        val t = CharTermAttributeImpl()
        val content = "hello".toCharArray()
        t.copyBuffer(content, 0, content.size)
        for (i in 0..<2000) {
            t.resizeBuffer(i)
            assertTrue(i <= t.buffer().size)
            assertEquals("hello", t.toString())
        }
    }

    @Test
    fun testSetLength() {
        val t = CharTermAttributeImpl()
        val content = "hello".toCharArray()
        t.copyBuffer(content, 0, content.size)
        expectThrows(IndexOutOfBoundsException::class) {
            t.setLength(-1)
        }
    }

    @Test
    fun testGrow() {
        var t = CharTermAttributeImpl()
        var buf = StringBuilder("ab")
        for (i in 0..<20) {
            val content = buf.toString().toCharArray()
            t.copyBuffer(content, 0, content.size)
            assertEquals(buf.length, t.length)
            assertEquals(buf.toString(), t.toString())
            buf.append(buf.toString())
        }
        assertEquals(1048576, t.length)

        // now as a StringBuilder, first variant
        t = CharTermAttributeImpl()
        buf = StringBuilder("ab")
        for (i in 0..<20) {
            t.setEmpty()!!.append(buf)
            assertEquals(buf.length, t.length)
            assertEquals(buf.toString(), t.toString())
            buf.append(t)
        }
        assertEquals(1048576, t.length)

        // Test for slow growth to a long term
        t = CharTermAttributeImpl()
        buf = StringBuilder("a")
        for (i in 0..<20000) {
            t.setEmpty()!!.append(buf)
            assertEquals(buf.length, t.length)
            assertEquals(buf.toString(), t.toString())
            buf.append("a")
        }
        assertEquals(20000, t.length)
    }

    @Test
    @Throws(Exception::class)
    fun testToString() {
        val b = charArrayOf('a', 'l', 'o', 'h', 'a')
        val t = CharTermAttributeImpl()
        t.copyBuffer(b, 0, 5)
        assertEquals("aloha", t.toString())

        t.setEmpty()!!.append("hi there")
        assertEquals("hi there", t.toString())
    }

    @Test
    @Throws(Exception::class)
    fun testClone() {
        val t = CharTermAttributeImpl()
        val content = "hello".toCharArray()
        t.copyBuffer(content, 0, 5)
        val buf = t.buffer()
        val copy = assertCloneIsEqual(t)
        assertEquals(t.toString(), copy.toString())
        assertNotSame(buf, copy.buffer())
    }

    @Test
    @Throws(Exception::class)
    fun testEquals() {
        val t1a = CharTermAttributeImpl()
        val content1a = "hello".toCharArray()
        t1a.copyBuffer(content1a, 0, 5)
        val t1b = CharTermAttributeImpl()
        val content1b = "hello".toCharArray()
        t1b.copyBuffer(content1b, 0, 5)
        val t2 = CharTermAttributeImpl()
        val content2 = "hello2".toCharArray()
        t2.copyBuffer(content2, 0, 6)
        assertTrue(t1a.equals(t1b))
        assertFalse(t1a.equals(t2))
        assertFalse(t2.equals(t1b))
    }

    @Test
    @Throws(Exception::class)
    fun testCopyTo() {
        var t = CharTermAttributeImpl()
        var copy = assertCopyIsEqual(t)
        assertEquals("", t.toString())
        assertEquals("", copy.toString())

        t = CharTermAttributeImpl()
        val content = "hello".toCharArray()
        t.copyBuffer(content, 0, 5)
        val buf = t.buffer()
        copy = assertCopyIsEqual(t)
        assertEquals(t.toString(), copy.toString())
        assertNotSame(buf, copy.buffer())
    }

    @Test
    @Throws(Exception::class)
    fun testAttributeReflection() {
        val t = CharTermAttributeImpl()
        t.append("foobar")
        TestUtil.assertAttributeReflection(
            t,
            HashMap<String, Any>().apply {
                put(CharTermAttribute::class.simpleName + "#term", "foobar")
                put(TermToBytesRefAttribute::class.simpleName + "#bytes", BytesRef("foobar"))
            }
        )
    }

    @Test
    fun testCharSequenceInterface() {
        val s = "0123456789"
        val t = CharTermAttributeImpl()
        t.append(s)

        assertEquals(s.length, t.length)
        assertEquals("12", t.subSequence(1, 3).toString())
        assertEquals(s, t.subSequence(0, s.length).toString())

        assertTrue(Regex("01\\d+").matches(t.toString()))
        assertTrue(Regex("34").matches(t.subSequence(3, 5).toString()))

        assertEquals(s.subSequence(3, 7).toString(), t.subSequence(3, 7).toString())

        for (i in 0..<s.length) {
            assertTrue(t[i] == s[i])
        }
    }

    @Test
    fun testAppendableInterface() {
        val t = CharTermAttributeImpl()
        t.append("1234")
        assertEquals("1234", t.toString())
        t.append("5678")
        assertEquals("12345678", t.toString())
        t.append('9')
        assertEquals("123456789", t.toString())
        t.append("0" as CharSequence)
        assertEquals("1234567890", t.toString())
        t.append("0123456789" as CharSequence, 1, 3)
        assertEquals("123456789012", t.toString())
        t.append(CharBuffer.wrap("0123456789".toCharArray()) as CharSequence, 3, 5)
        assertEquals("12345678901234", t.toString())
        t.append(t as CharSequence)
        assertEquals("1234567890123412345678901234", t.toString())
        t.append(StringBuilder("0123456789") as CharSequence, 5, 7)
        assertEquals("123456789012341234567890123456", t.toString())
        t.append(StringBuffer(t) as CharSequence)
        assertEquals("123456789012341234567890123456123456789012341234567890123456", t.toString())
        // very wierd, to test if a subSlice is wrapped correct :)
        val buf = CharBuffer.wrap("0123456789".toCharArray(), 3, 5)
        assertEquals("34567", buf.toString())
        t.setEmpty()!!.append(buf as CharSequence, 1, 2)
        assertEquals("4", t.toString())
        val t2: CharTermAttribute = CharTermAttributeImpl()
        t2.append("test")
        t.append(t2 as CharSequence)
        assertEquals("4test", t.toString())
        t.append(t2 as CharSequence, 1, 2)
        assertEquals("4teste", t.toString())

        expectThrows(IndexOutOfBoundsException::class) {
            t.append(t2 as CharSequence, 1, 5)
        }

        expectThrows(IndexOutOfBoundsException::class) {
            t.append(t2 as CharSequence, 1, 0)
        }

        t.append(null as CharSequence?)
        assertEquals("4testenull", t.toString())
    }

    @Test
    fun testAppendableInterfaceWithLongSequences() {
        val t = CharTermAttributeImpl()
        t.append("01234567890123456789012345678901234567890123456789" as CharSequence)
        t.append(
            CharBuffer.wrap("01234567890123456789012345678901234567890123456789".toCharArray()) as CharSequence,
            3,
            50
        )
        assertEquals(
            "0123456789012345678901234567890123456789012345678934567890123456789012345678901234567890123456789",
            t.toString()
        )
        t.setEmpty()!!.append(StringBuilder("01234567890123456789") as CharSequence, 5, 17)
        assertEquals("567890123456", t.toString())
        t.append(StringBuffer(t))
        assertEquals("567890123456567890123456", t.toString())
        // very wierd, to test if a subSlice is wrapped correct :)
        val buf = CharBuffer.wrap("012345678901234567890123456789".toCharArray(), 3, 15)
        assertEquals("345678901234567", buf.toString())
        t.setEmpty()!!.append(buf, 1, 14)
        assertEquals("4567890123456", t.toString())

        // finally use a completely custom CharSequence that is not catched by instanceof checks
        val longTestString = "012345678901234567890123456789"
        t.append(
            object : CharSequence {
                override val length: Int
                    get() = longTestString.length

                override fun get(index: Int): Char {
                    return longTestString[index]
                }

                override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
                    return longTestString.subSequence(startIndex, endIndex)
                }

                override fun toString(): String {
                    return longTestString
                }
            }
        )
        assertEquals("4567890123456$longTestString", t.toString())
    }

    @Test
    fun testNonCharSequenceAppend() {
        val t = CharTermAttributeImpl()
        t.append("0123456789")
        t.append("0123456789")
        assertEquals("01234567890123456789", t.toString())
        t.append(StringBuilder("0123456789"))
        assertEquals("012345678901234567890123456789", t.toString())
        val t2: CharTermAttribute = CharTermAttributeImpl()
        t2.append("test")
        t.append(t2)
        assertEquals("012345678901234567890123456789test", t.toString())
        t.append(null as CharSequence?)
        t.append(null as CharSequence?)
        t.append(null as CharSequence?)
        assertEquals("012345678901234567890123456789testnullnullnull", t.toString())
    }

    @Test
    fun testExceptions() {
        val t = CharTermAttributeImpl()
        t.append("test")
        assertEquals("test", t.toString())

        expectThrows(IndexOutOfBoundsException::class) {
            t[-1]
        }

        expectThrows(IndexOutOfBoundsException::class) {
            t[4]
        }

        expectThrows(IndexOutOfBoundsException::class) {
            t.subSequence(0, 5)
        }

        expectThrows(IndexOutOfBoundsException::class) {
            t.subSequence(5, 0)
        }
    }

    companion object {
        fun <T : AttributeImpl> assertCloneIsEqual(att: T): T {
            @Suppress("UNCHECKED_CAST")
            val clone = att.clone() as T
            assertEquals(att, clone, "Clone must be equal")
            assertEquals(att.hashCode(), clone.hashCode(), "Clone's hashcode must be equal")
            return clone
        }

        @Throws(Exception::class)
        fun <T : AttributeImpl> assertCopyIsEqual(att: T): T {
            @Suppress("UNCHECKED_CAST")
            val copy = when (att) {
                is CharTermAttributeImpl -> CharTermAttributeImpl() as T
                else -> throw UnsupportedOperationException("No no-arg constructor strategy for ${att::class.simpleName}")
            }
            att.copyTo(copy)
            assertEquals(att, copy, "Copied instance must be equal")
            assertEquals(att.hashCode(), copy.hashCode(), "Copied instance's hashcode must be equal")
            return copy
        }
    }
}
