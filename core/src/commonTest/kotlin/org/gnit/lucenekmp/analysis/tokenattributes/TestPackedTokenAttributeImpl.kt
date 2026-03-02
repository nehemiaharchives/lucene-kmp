package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TestPackedTokenAttributeImpl : LuceneTestCase() {

    /* the CharTermAttributeStuff is tested by TestCharTermAttributeImpl */

    @Test
    @Throws(Exception::class)
    fun testClone() {
        val t = PackedTokenAttributeImpl()
        t.setOffset(0, 5)
        val content = "hello".toCharArray()
        t.copyBuffer(content, 0, 5)
        val buf = t.buffer()
        val copy = TestCharTermAttributeImpl.assertCloneIsEqual(t)
        assertEquals(t.toString(), copy.toString())
        assertNotSame(buf, copy.buffer())
    }

    @Test
    @Throws(Exception::class)
    fun testCopyTo() {
        var t = PackedTokenAttributeImpl()
        var copy = assertCopyIsEqual(t)
        assertEquals("", t.toString())
        assertEquals("", copy.toString())

        t = PackedTokenAttributeImpl()
        t.setOffset(0, 5)
        val content = "hello".toCharArray()
        t.copyBuffer(content, 0, 5)
        val buf = t.buffer()
        copy = assertCopyIsEqual(t)
        assertEquals(t.toString(), copy.toString())
        assertNotSame(buf, copy.buffer())
    }

    @Test
    @Throws(Exception::class)
    fun testPackedTokenAttributeFactory() {
        val ts: TokenStream = MockTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            MockTokenizer.WHITESPACE,
            false,
            MockTokenizer.DEFAULT_MAX_TOKEN_LENGTH
        )
        (ts as Tokenizer).setReader(StringReader("foo bar"))

        assertTrue(
            ts.addAttribute(CharTermAttribute::class) is PackedTokenAttributeImpl,
            "CharTermAttribute is not implemented by Token"
        )
        assertTrue(
            ts.addAttribute(OffsetAttribute::class) is PackedTokenAttributeImpl,
            "OffsetAttribute is not implemented by Token"
        )
        assertTrue(
            ts.addAttribute(PositionIncrementAttribute::class) is PackedTokenAttributeImpl,
            "PositionIncrementAttribute is not implemented by Token"
        )
        assertTrue(
            ts.addAttribute(TypeAttribute::class) is PackedTokenAttributeImpl,
            "TypeAttribute is not implemented by Token"
        )

        assertTrue(
            ts.addAttribute(FlagsAttribute::class) is FlagsAttributeImpl,
            "FlagsAttribute is not implemented by FlagsAttributeImpl"
        )
    }

    @Test
    @Throws(Exception::class)
    fun testAttributeReflection() {
        val t = PackedTokenAttributeImpl()
        t.append("foobar")
        t.setOffset(6, 22)
        t.setPositionIncrement(3)
        t.positionLength = 11
        t.setType("foobar")
        t.termFrequency = 42
        TestUtil.assertAttributeReflection(
            t,
            HashMap<String, Any>().apply {
                put(CharTermAttribute::class.simpleName + "#term", "foobar")
                put(TermToBytesRefAttribute::class.simpleName + "#bytes", BytesRef("foobar"))
                put(OffsetAttribute::class.simpleName + "#startOffset", 6)
                put(OffsetAttribute::class.simpleName + "#endOffset", 22)
                put(PositionIncrementAttribute::class.simpleName + "#positionIncrement", 3)
                put(PositionLengthAttribute::class.simpleName + "#positionLength", 11)
                put(TypeAttribute::class.simpleName + "#type", "foobar")
                put(TermFrequencyAttribute::class.simpleName + "#termFrequency", 42)
            }
        )
    }

    companion object {
        @Throws(Exception::class)
        fun <T : AttributeImpl> assertCopyIsEqual(att: T): T {
            @Suppress("UNCHECKED_CAST")
            val copy = when (att) {
                is PackedTokenAttributeImpl -> PackedTokenAttributeImpl() as T
                else -> throw UnsupportedOperationException("No copy strategy for ${att::class.simpleName}")
            }
            att.copyTo(copy)
            assertEquals(att, copy, "Copied instance must be equal")
            assertEquals(att.hashCode(), copy.hashCode(), "Copied instance's hashcode must be equal")
            return copy
        }
    }
}
