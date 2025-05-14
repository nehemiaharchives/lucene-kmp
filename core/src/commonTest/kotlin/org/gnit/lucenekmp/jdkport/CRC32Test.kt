package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals

class CRC32Test {

    @Test
    fun testUpdate() {
        val crc32 = CRC32()
        val data = "Hello, World!".encodeToByteArray()
        crc32.update(data, 0, data.size)
        assertEquals(0xEC4AC3D0, crc32.getValue())
    }

    @Test
    fun testGetValue() {
        val crc32 = CRC32()
        val data = "Hello, World!".encodeToByteArray()
        crc32.update(data, 0, data.size)
        assertEquals(0xEC4AC3D0, crc32.getValue())
    }

    @Test
    fun testReset() {
        val crc32 = CRC32()
        val data = "Hello, World!".encodeToByteArray()
        crc32.update(data, 0, data.size)
        crc32.reset()
        assertEquals(0, crc32.getValue())
    }
}
