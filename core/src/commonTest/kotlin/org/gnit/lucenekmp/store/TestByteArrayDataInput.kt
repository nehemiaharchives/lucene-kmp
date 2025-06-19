package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.ByteOrder
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestByteArrayDataInput : LuceneTestCase() {
    @Test
    fun testBasic() {
        var bytes = byteArrayOf(1, 65)
        val input = ByteArrayDataInput(bytes)
        assertEquals("A", input.readString())
        assertTrue(input.eof())

        bytes = byteArrayOf(1, 1, 65)
        input.reset(bytes, 1, 2)
        assertEquals("A", input.readString())
        assertTrue(input.eof())
    }

    @Test
    fun testDatatypes() {
        val bytes = ByteArray(32)
        val out = ByteArrayDataOutput(bytes)
        out.writeByte(43.toByte())
        out.writeShort(12345.toShort())
        out.writeInt(1234567890)
        out.writeLong(1234567890123456789L)
        val size = out.position
        assertEquals(15, size)

        val buf = ByteBuffer.wrap(bytes, 0, size)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(43.toByte(), buf.get())
        assertEquals(12345.toShort(), buf.getShort())
        assertEquals(1234567890, buf.getInt())
        assertEquals(1234567890123456789L, buf.getLong())
        assertEquals(0, buf.remaining())

        val input = ByteArrayDataInput(bytes, 0, size)
        assertEquals(43.toByte(), input.readByte())
        assertEquals(12345.toShort(), input.readShort())
        assertEquals(1234567890, input.readInt())
        assertEquals(1234567890123456789L, input.readLong())
        assertTrue(input.eof())
    }
}

