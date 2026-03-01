package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TestBytesRefAttImpl : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testCopyTo() {
        val t = BytesTermAttributeImpl()
        var copy = assertCopyIsEqual(t)

        // first do empty
        assertEquals(t.bytesRef, copy.bytesRef)
        assertEquals(BytesRef(), copy.bytesRef)
        // now after setting it
        t.setBytesRef(BytesRef("hello"))
        copy = assertCopyIsEqual(t)
        assertEquals(t.bytesRef, copy.bytesRef)
        assertNotSame(t.bytesRef, copy.bytesRef)
    }

    @Test
    fun testLucene9856() {
        assertTrue(
            BytesTermAttributeImpl() is TermToBytesRefAttribute,
            "BytesTermAttributeImpl must explicitly declare to implement TermToBytesRefAttribute"
        )
    }

    companion object {
        @Throws(Exception::class)
        fun <T : AttributeImpl> assertCopyIsEqual(att: T): T {
            @Suppress("UNCHECKED_CAST")
            val copy = att.clone() as T
            att.copyTo(copy)
            assertEquals(att, copy, "Copied instance must be equal")
            assertEquals(att.hashCode(), copy.hashCode(), "Copied instance's hashcode must be equal")
            return copy
        }
    }
}
