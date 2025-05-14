package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShortExtTest {

    @Test
    fun testCompare() {
        assertEquals(0, Short.compare(123.toShort(), 123.toShort()))
        assertTrue(Short.compare(123.toShort(), 321.toShort()) < 0)
        assertTrue(Short.compare(321.toShort(), 123.toShort()) > 0)
    }

    @Test
    fun testToUnsignedInt() {
        val value: Short = -1
        val unsignedInt = Short.toUnsignedInt(value)
        assertEquals(65535, unsignedInt)
    }

    @Test
    fun testToUnsignedLong() {
        val value: Short = -1
        val unsignedLong = Short.toUnsignedLong(value)
        assertEquals(65535L, unsignedLong)
    }
}
