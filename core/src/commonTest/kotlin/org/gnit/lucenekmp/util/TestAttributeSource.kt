package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.FlagsAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.FlagsAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttributeImpl
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.test.fail

class TestAttributeSource : LuceneTestCase() {

    @Test
    fun testCaptureState() {
        // init a first instance
        val src = AttributeSource()
        var termAtt = src.addAttribute(CharTermAttribute::class)
        var typeAtt = src.addAttribute(TypeAttribute::class)
        termAtt.append("TestTerm")
        typeAtt.setType("TestType")
        val hashCode = src.hashCode()

        val state: AttributeSource.State? = src.captureState()

        // modify the attributes
        termAtt.setEmpty()!!.append("AnotherTestTerm")
        typeAtt.setType("AnotherTestType")
        assertTrue(hashCode != src.hashCode(), "Hash code should be different")

        src.restoreState(state)
        assertEquals("TestTerm", termAtt.toString())
        assertEquals("TestType", typeAtt.type())
        assertEquals(hashCode, src.hashCode(), "Hash code should be equal after restore")

        // restore into an exact configured copy
        val copy = AttributeSource()
        copy.addAttribute(CharTermAttribute::class)
        copy.addAttribute(TypeAttribute::class)
        copy.restoreState(state)
        assertEquals(
            src.hashCode(),
            copy.hashCode(),
            "Both AttributeSources should have same hashCode after restore"
        )
        assertEquals(src, copy, "Both AttributeSources should be equal after restore")

        // init a second instance (with attributes in different order and one additional attribute)
        val src2 = AttributeSource()
        typeAtt = src2.addAttribute(TypeAttribute::class)
        val flagsAtt = src2.addAttribute(FlagsAttribute::class)
        termAtt = src2.addAttribute(CharTermAttribute::class)
        flagsAtt.flags = 12345

        src2.restoreState(state)
        assertEquals("TestTerm", termAtt.toString())
        assertEquals("TestType", typeAtt.type())
        assertEquals(12345, flagsAtt.flags, "FlagsAttribute should not be touched")

        // init a third instance missing one Attribute
        val src3 = AttributeSource()
        termAtt = src3.addAttribute(CharTermAttribute::class)
        // The third instance is missing the TypeAttribute, so restoreState() should throw
        // IllegalArgumentException
        expectThrows(IllegalArgumentException::class) {
            src3.restoreState(state)
        }
    }

    @Test
    fun testCloneAttributes() {
        val src = AttributeSource()
        val flagsAtt = src.addAttribute(FlagsAttribute::class)
        val typeAtt = src.addAttribute(TypeAttribute::class)
        flagsAtt.flags = 1234
        typeAtt.setType("TestType")

        val clone = src.cloneAttributes()
        val it = clone.attributeClassesIterator
        assertEquals(FlagsAttribute::class, it.next(), "FlagsAttribute must be the first attribute")
        assertEquals(TypeAttribute::class, it.next(), "TypeAttribute must be the second attribute")
        assertFalse(it.hasNext(), "No more attributes")

        val flagsAtt2 = clone.getAttribute(FlagsAttribute::class)
        assertNotNull(flagsAtt2)
        val typeAtt2 = clone.getAttribute(TypeAttribute::class)
        assertNotNull(typeAtt2)
        assertNotSame(flagsAtt2, flagsAtt, "FlagsAttribute of original and clone must be different instances")
        assertNotSame(typeAtt2, typeAtt, "TypeAttribute of original and clone must be different instances")
        assertEquals(flagsAtt2, flagsAtt, "FlagsAttribute of original and clone must be equal")
        assertEquals(typeAtt2, typeAtt, "TypeAttribute of original and clone must be equal")

        // test copy back
        flagsAtt2.flags = 4711
        typeAtt2.setType("OtherType")
        clone.copyTo(src)
        assertEquals(4711, flagsAtt.flags, "FlagsAttribute of original must now contain updated term")
        assertEquals("OtherType", typeAtt.type(), "TypeAttribute of original must now contain updated type")
        // verify again:
        assertNotSame(flagsAtt2, flagsAtt, "FlagsAttribute of original and clone must be different instances")
        assertNotSame(typeAtt2, typeAtt, "TypeAttribute of original and clone must be different instances")
        assertEquals(flagsAtt2, flagsAtt, "FlagsAttribute of original and clone must be equal")
        assertEquals(typeAtt2, typeAtt, "TypeAttribute of original and clone must be equal")
    }

    @Test
    fun testDefaultAttributeFactory() {
        val src = AttributeSource()

        assertTrue(
            src.addAttribute(CharTermAttribute::class) is CharTermAttributeImpl,
            "CharTermAttribute is not implemented by CharTermAttributeImpl"
        )
        assertTrue(
            src.addAttribute(OffsetAttribute::class) is OffsetAttributeImpl,
            "OffsetAttribute is not implemented by OffsetAttributeImpl"
        )
        assertTrue(
            src.addAttribute(FlagsAttribute::class) is FlagsAttributeImpl,
            "FlagsAttribute is not implemented by FlagsAttributeImpl"
        )
        assertTrue(
            src.addAttribute(PayloadAttribute::class) is PayloadAttributeImpl,
            "PayloadAttribute is not implemented by PayloadAttributeImpl"
        )
        assertTrue(
            src.addAttribute(PositionIncrementAttribute::class) is PositionIncrementAttributeImpl,
            "PositionIncrementAttribute is not implemented by PositionIncrementAttributeImpl"
        )
        assertTrue(
            src.addAttribute(TypeAttribute::class) is TypeAttributeImpl,
            "TypeAttribute is not implemented by TypeAttributeImpl"
        )
    }

    @Test
    fun testInvalidArguments() {
        expectThrows(IllegalArgumentException::class) {
            val src = AttributeSource()
            src.addAttribute(Token::class)
            fail("Should throw IllegalArgumentException")
        }

        expectThrows(IllegalArgumentException::class) {
            val src = AttributeSource(Token.TOKEN_ATTRIBUTE_FACTORY)
            src.addAttribute(Token::class)
        }

        expectThrows(IllegalArgumentException::class) {
            val src = AttributeSource()
            @Suppress("UNCHECKED_CAST")
            src.addAttribute(Iterator::class as kotlin.reflect.KClass<Attribute>)
        }
    }

    @Test
    fun testLUCENE_3042() {
        val src1 = AttributeSource()
        src1.addAttribute(CharTermAttribute::class).append("foo")
        val hash1 = src1.hashCode() // this triggers a cached state
        val src2 = AttributeSource(src1)
        src2.addAttribute(TypeAttribute::class).setType("bar")
        assertTrue(hash1 != src1.hashCode(), "The hashCode is identical, so the captured state was preserved.")
        assertEquals(src2.hashCode(), src1.hashCode())
    }

    @Test
    fun testClonePayloadAttribute() {
        // LUCENE-6055: verify that PayloadAttribute.clone() does deep cloning.
        val src = PayloadAttributeImpl(BytesRef(byteArrayOf(1, 2, 3)))

        // test clone()
        var clone = src.clone()
        clone.payload!!.bytes[0] = 10 // modify one byte, srcBytes shouldn't change
        assertEquals(1, src.payload!!.bytes[0].toInt(), "clone() wasn't deep")

        // test copyTo()
        clone = PayloadAttributeImpl()
        src.copyTo(clone)
        clone.payload!!.bytes[0] = 10 // modify one byte, srcBytes shouldn't change
        assertEquals(1, src.payload!!.bytes[0].toInt(), "clone() wasn't deep")
    }

    @Test
    fun testRemoveAllAttributes() {
        val attrClasses = mutableListOf(
            CharTermAttribute::class,
            OffsetAttribute::class,
            FlagsAttribute::class,
            PayloadAttribute::class,
            PositionIncrementAttribute::class,
            TypeAttribute::class
        )

        // Add attributes with the default factory, then try to remove all of them
        val defaultFactoryAttributeSource = AttributeSource()

        assertFalse(defaultFactoryAttributeSource.hasAttributes())

        for (attrClass in attrClasses) {
            defaultFactoryAttributeSource.addAttribute(attrClass)
            assertTrue(
                defaultFactoryAttributeSource.hasAttribute(attrClass),
                "Missing added attribute ${attrClass.simpleName}"
            )
        }

        defaultFactoryAttributeSource.removeAllAttributes()

        for (attrClass in attrClasses) {
            assertFalse(
                defaultFactoryAttributeSource.hasAttribute(attrClass),
                "Didn't remove attribute ${attrClass.simpleName}"
            )
        }
        assertFalse(defaultFactoryAttributeSource.hasAttributes())

        // Add attributes with the packed implementations factory, then try to remove all of them
        val packedImplsAttributeSource = AttributeSource(TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY)
        assertFalse(packedImplsAttributeSource.hasAttributes())

        for (attrClass in attrClasses) {
            packedImplsAttributeSource.addAttribute(attrClass)
            assertTrue(
                packedImplsAttributeSource.hasAttribute(attrClass),
                "Missing added attribute ${attrClass.simpleName}"
            )
        }

        packedImplsAttributeSource.removeAllAttributes()

        for (attrClass in attrClasses) {
            assertFalse(
                packedImplsAttributeSource.hasAttribute(attrClass),
                "Didn't remove attribute ${attrClass.simpleName}"
            )
        }
        assertFalse(packedImplsAttributeSource.hasAttributes())
    }
}
