package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteOrderTest {

    @Test
    fun testToString() {
        assertEquals("BIG_ENDIAN", ByteOrder.BIG_ENDIAN.toString())
        assertEquals("LITTLE_ENDIAN", ByteOrder.LITTLE_ENDIAN.toString())
    }

    @Test
    fun testNativeOrder() {
        // Since we hardcoded the native order to LITTLE_ENDIAN in ByteOrder.kt,
        // we expect the native order to be LITTLE_ENDIAN.
        assertEquals(ByteOrder.LITTLE_ENDIAN, ByteOrder.nativeOrder())
    }
}
