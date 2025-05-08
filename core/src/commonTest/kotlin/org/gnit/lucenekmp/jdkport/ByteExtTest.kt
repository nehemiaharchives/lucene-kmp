package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteExtTest {

    @Test
    fun testCompareUnsigned() {
        assertEquals(0, Byte.compareUnsigned(1, 1))
        assertEquals(-1, Byte.compareUnsigned(1, 2))
        assertEquals(1, Byte.compareUnsigned(2, 1))
    }

    @Test
    fun testToUnsignedInt() {
        assertEquals(255, Byte.toUnsignedInt(-1))
        assertEquals(0, Byte.toUnsignedInt(0))
        assertEquals(127, Byte.toUnsignedInt(127))
    }

    @Test
    fun testToUnsignedLong() {
        assertEquals(255L, Byte.toUnsignedLong(-1))
        assertEquals(0L, Byte.toUnsignedLong(0))
        assertEquals(127L, Byte.toUnsignedLong(127))
    }
}
