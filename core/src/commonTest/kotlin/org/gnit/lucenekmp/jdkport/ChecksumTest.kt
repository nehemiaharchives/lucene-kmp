package org.gnit.lucenekmp.jdkport

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class ChecksumTest {

    class MockChecksum : Checksum {
        private var sum: Long = 0

        override fun update(b: Int) {
            sum += b.toLong()
        }

        override fun update(b: ByteArray, off: Int, len: Int) {
            for (i in off until off + len) {
                sum += b[i].toLong()
            }
        }

        override fun getValue(): Long {
            return sum
        }

        override fun reset() {
            sum = 0
        }
    }

    @Test
    fun testUpdateWithByte() {
        val checksum = MockChecksum()
        checksum.update(1)
        checksum.update(2)
        checksum.update(3)
        assertEquals(6, checksum.getValue())
    }

    @Test
    fun testUpdateWithByteArray() {
        val checksum = MockChecksum()
        val data = byteArrayOf(1, 2, 3, 4, 5)
        checksum.update(data, 0, data.size)
        assertEquals(15, checksum.getValue())
    }

    @Test
    fun testUpdateWithByteBuffer() {
        val checksum = MockChecksum()
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val byteBuffer = ByteBuffer.wrap(data)
        checksum.update(byteBuffer)
        assertEquals(15, checksum.getValue())
    }

    @Test
    fun testGetValue() {
        val checksum = MockChecksum()
        checksum.update(1)
        checksum.update(2)
        checksum.update(3)
        assertEquals(6, checksum.getValue())
    }

    @Test
    fun testReset() {
        val checksum = MockChecksum()
        checksum.update(1)
        checksum.update(2)
        checksum.update(3)
        checksum.reset()
        assertEquals(0, checksum.getValue())
    }
}
