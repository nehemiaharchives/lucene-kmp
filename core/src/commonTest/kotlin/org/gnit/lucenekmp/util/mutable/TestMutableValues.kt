package org.gnit.lucenekmp.util.mutable

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Simple test of the basic contract of the various [MutableValue] implementaitons. */
class TestMutableValues : LuceneTestCase() {

    @Test
    fun testStr() {
        val xxx = MutableValueStr()
        assert(xxx.value.get() == BytesRef()) { "defaults have changed, test utility may not longer be as high" }
        assert(xxx.exists) { "defaults have changed, test utility may not longer be as high" }
        assertSanity(xxx)
        val yyy = MutableValueStr()
        assertSanity(yyy)

        assertEquality(xxx, yyy)

        xxx.exists = false
        assertSanity(xxx)

        assertInEquality(xxx, yyy)

        yyy.exists = false
        assertEquality(xxx, yyy)

        xxx.value.clear()
        xxx.value.copyChars("zzz")
        xxx.exists = true
        assertSanity(xxx)

        assertInEquality(xxx, yyy)

        yyy.value.clear()
        yyy.value.copyChars("aaa")
        yyy.exists = true
        assertSanity(yyy)

        assertInEquality(xxx, yyy)
        assertTrue(xxx > yyy)
        assertTrue(yyy < xxx)

        xxx.copy(yyy)
        assertSanity(xxx)
        assertEquality(xxx, yyy)

        // special BytesRef considerations...

        xxx.exists = false
        xxx.value.clear() // but leave bytes alone
        assertInEquality(xxx, yyy)

        yyy.exists = false
        yyy.value.clear() // but leave bytes alone
        assertEquality(xxx, yyy)
    }

    @Test
    fun testDouble() {
        val xxx = MutableValueDouble()
        assert(xxx.value == 0.0) { "defaults have changed, test utility may not longer be as high" }
        assert(xxx.exists) { "defaults have changed, test utility may not longer be as high" }
        assertSanity(xxx)
        val yyy = MutableValueDouble()
        assertSanity(yyy)

        assertEquality(xxx, yyy)

        xxx.exists = false
        assertSanity(xxx)

        assertInEquality(xxx, yyy)

        yyy.exists = false
        assertEquality(xxx, yyy)

        xxx.value = 42.0
        xxx.exists = true
        assertSanity(xxx)

        assertInEquality(xxx, yyy)

        yyy.value = -99.0
        yyy.exists = true
        assertSanity(yyy)

        assertInEquality(xxx, yyy)
        assertTrue(xxx > yyy)
        assertTrue(yyy < xxx)

        xxx.copy(yyy)
        assertSanity(xxx)
        assertEquality(xxx, yyy)
    }

    @Test
    fun testInt() {
        val xxx = MutableValueInt()
        assert(xxx.value == 0) { "defaults have changed, test utility may not longer be as high" }
        assert(xxx.exists) { "defaults have changed, test utility may not longer be as high" }
        assertSanity(xxx)
        val yyy = MutableValueInt()
        assertSanity(yyy)

        assertEquality(xxx, yyy)

        xxx.exists = false
        assertSanity(xxx)

        assertInEquality(xxx, yyy)

        yyy.exists = false
        assertEquality(xxx, yyy)

        xxx.value = 42
        xxx.exists = true
        assertSanity(xxx)

        assertInEquality(xxx, yyy)

        yyy.value = -99
        yyy.exists = true
        assertSanity(yyy)

        assertInEquality(xxx, yyy)
        assertTrue(xxx > yyy)
        assertTrue(yyy < xxx)

        xxx.copy(yyy)
        assertSanity(xxx)
        assertEquality(xxx, yyy)
    }

    @Test
    fun testFloat() {
        val xxx = MutableValueFloat()
        assert(xxx.value == 0.0f) { "defaults have changed, test utility may not longer be as high" }
        assert(xxx.exists) { "defaults have changed, test utility may not longer be as high" }
        assertSanity(xxx)
        val yyy = MutableValueFloat()
        assertSanity(yyy)

        assertEquality(xxx, yyy)

        xxx.exists = false
        assertSanity(xxx)

        assertInEquality(xxx, yyy)

        yyy.exists = false
        assertEquality(xxx, yyy)

        xxx.value = 42.0f
        xxx.exists = true
        assertSanity(xxx)

        assertInEquality(xxx, yyy)

        yyy.value = -99.0f
        yyy.exists = true
        assertSanity(yyy)

        assertInEquality(xxx, yyy)
        assertTrue(xxx > yyy)
        assertTrue(yyy < xxx)

        xxx.copy(yyy)
        assertSanity(xxx)
        assertEquality(xxx, yyy)
    }

    @Test
    fun testLong() {
        val xxx = MutableValueLong()
        assert(xxx.value == 0L) { "defaults have changed, test utility may not longer be as high" }
        assert(xxx.exists) { "defaults have changed, test utility may not longer be as high" }
        assertSanity(xxx)
        val yyy = MutableValueLong()
        assertSanity(yyy)

        assertEquality(xxx, yyy)

        xxx.exists = false
        assertSanity(xxx)

        assertInEquality(xxx, yyy)

        yyy.exists = false
        assertEquality(xxx, yyy)

        xxx.value = 42L
        xxx.exists = true
        assertSanity(xxx)

        assertInEquality(xxx, yyy)

        yyy.value = -99L
        yyy.exists = true
        assertSanity(yyy)

        assertInEquality(xxx, yyy)
        assertTrue(xxx > yyy)
        assertTrue(yyy < xxx)

        xxx.copy(yyy)
        assertSanity(xxx)
        assertEquality(xxx, yyy)
    }

    @Test
    fun testBool() {
        val xxx = MutableValueBool()
        assert(!xxx.value) { "defaults have changed, test utility may not longer be as high" }
        assert(xxx.exists) { "defaults have changed, test utility may not longer be as high" }
        assertSanity(xxx)
        val yyy = MutableValueBool()
        assertSanity(yyy)

        assertEquality(xxx, yyy)

        xxx.exists = false
        assertSanity(xxx)

        assertInEquality(xxx, yyy)

        yyy.exists = false
        assertEquality(xxx, yyy)

        xxx.value = true
        xxx.exists = true
        assertSanity(xxx)

        assertInEquality(xxx, yyy)

        yyy.value = false
        yyy.exists = true
        assertSanity(yyy)

        assertInEquality(xxx, yyy)
        assertTrue(xxx > yyy)
        assertTrue(yyy < xxx)

        xxx.copy(yyy)
        assertSanity(xxx)
        assertEquality(xxx, yyy)
    }

    private fun assertSanity(x: MutableValue) {
        assertEquality(x, x)
        val y = x.duplicate()
        assertEquality(x, y)
    }

    private fun assertEquality(x: MutableValue, y: MutableValue) {
        assertEquals(x.hashCode(), y.hashCode())

        assertEquals(x, y)
        assertEquals(y, x)

        assertTrue(x.equalsSameType(y))
        assertTrue(y.equalsSameType(x))

        assertEquals(0, x.compareTo(y))
        assertEquals(0, y.compareTo(x))

        assertEquals(0, x.compareSameType(y))
        assertEquals(0, y.compareSameType(x))
    }

    private fun assertInEquality(x: MutableValue, y: MutableValue) {
        assertFalse(x == y)
        assertFalse(y == x)

        assertFalse(x.equalsSameType(y))
        assertFalse(y.equalsSameType(x))

        assertFalse(0 == x.compareTo(y))
        assertFalse(0 == y.compareTo(x))
    }
}
