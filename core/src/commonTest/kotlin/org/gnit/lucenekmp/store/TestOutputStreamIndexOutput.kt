package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.ByteOrder
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestOutputStreamIndexOutput : LuceneTestCase() {
    @Test
    fun testDataTypes() {
        for (i in 0 until 12) {
            doTestDataTypes(i)
        }
    }

    private fun doTestDataTypes(offset: Int) {
        val bos = ByteArrayOutputStream()
        val out: IndexOutput = OutputStreamIndexOutput("test$offset", "test", bos, 12)
        for (i in 0 until offset) {
            out.writeByte(i.toByte())
        }
        out.writeShort(12345.toShort())
        out.writeInt(1234567890)
        out.writeLong(1234567890123456789L)
        assertEquals(offset + 14L, out.filePointer)
        out.close()

        val buf = ByteBuffer.wrap(bos.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until offset) {
            assertEquals(i.toByte(), buf.get())
        }
        assertEquals(12345.toShort(), buf.getShort())
        assertEquals(1234567890, buf.getInt())
        assertEquals(1234567890123456789L, buf.getLong())
        assertEquals(0, buf.remaining())
    }
}
