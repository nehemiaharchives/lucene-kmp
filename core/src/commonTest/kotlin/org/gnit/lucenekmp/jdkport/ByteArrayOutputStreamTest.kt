package org.gnit.lucenekmp.jdkport

import kotlin.test.*

class ByteArrayOutputStreamTest {

    @Test
    fun testWriteSingleByte() {
        val baos = ByteArrayOutputStream()
        baos.write(42)
        assertContentEquals(byteArrayOf(42), baos.toByteArray())
        assertEquals(1, baos.size())
    }

    @Test
    fun testWriteByteArray() {
        val baos = ByteArrayOutputStream()
        val data = byteArrayOf(1, 2, 3, 4, 5)
        baos.write(data, 0, data.size)
        assertContentEquals(data, baos.toByteArray())
        assertEquals(5, baos.size())
    }

    @Test
    fun testWriteBytesConvenience() {
        val baos = ByteArrayOutputStream()
        val data = byteArrayOf(10, 20, 30)
        baos.writeBytes(data)
        assertContentEquals(data, baos.toByteArray())
    }

    @Test
    fun testEnsureCapacityAndResize() {
        val baos = ByteArrayOutputStream(2)
        val data = byteArrayOf(1, 2, 3, 4)
        baos.write(data, 0, data.size)
        assertContentEquals(data, baos.toByteArray())
        assertEquals(4, baos.size())
    }

    @Test
    fun testReset() {
        val baos = ByteArrayOutputStream()
        baos.write(1)
        baos.write(2)
        baos.reset()
        assertEquals(0, baos.size())
        assertContentEquals(byteArrayOf(), baos.toByteArray())
        baos.write(3)
        assertContentEquals(byteArrayOf(3), baos.toByteArray())
    }

    @Test
    fun testToStringDefaultCharset() {
        val baos = ByteArrayOutputStream()
        val str = "hello"
        baos.writeBytes(str.encodeToByteArray())
        assertTrue(baos.toString().contains("hello"))
    }

    @Test
    fun testToStringWithCharsetName() {
        val baos = ByteArrayOutputStream()
        val str = "world"
        baos.writeBytes(str.encodeToByteArray())
        assertTrue(baos.toString("UTF-8").contains("world"))
    }

    @Test
    fun testToStringWithCharset() {
        val baos = ByteArrayOutputStream()
        val str = "kotlin"
        baos.writeBytes(str.encodeToByteArray())
        assertTrue(baos.toString(StandardCharsets.UTF_8).contains("kotlin"))
    }

    @Test
    fun testWriteTo() {
        val baos = ByteArrayOutputStream()
        val data = byteArrayOf(7, 8, 9)
        baos.writeBytes(data)
        val out = ByteArrayOutputStream()
        baos.writeTo(out)
        assertContentEquals(data, out.toByteArray())
    }

    @Test
    fun testWriteWithOffsetAndLength() {
        val baos = ByteArrayOutputStream()
        val data = byteArrayOf(1, 2, 3, 4, 5)
        baos.write(data, 1, 3)
        assertContentEquals(byteArrayOf(2, 3, 4), baos.toByteArray())
    }

    @Test
    fun testWriteNullThrows() {
        val baos = ByteArrayOutputStream()
        assertFailsWith<NullPointerException> {
            baos.write(null as ByteArray, 0, 1)
        }
    }

    @Test
    fun testWriteOutOfBoundsThrows() {
        val baos = ByteArrayOutputStream()
        val data = byteArrayOf(1, 2, 3)
        assertFailsWith<IndexOutOfBoundsException> {
            baos.write(data, -1, 2)
        }
        assertFailsWith<IndexOutOfBoundsException> {
            baos.write(data, 0, 4)
        }
        assertFailsWith<IndexOutOfBoundsException> {
            baos.write(data, 2, 2)
        }
    }

    @Test
    fun testCloseHasNoEffect() {
        val baos = ByteArrayOutputStream()
        baos.write(1)
        baos.close()
        baos.write(2)
        assertContentEquals(byteArrayOf(1, 2), baos.toByteArray())
    }
}
