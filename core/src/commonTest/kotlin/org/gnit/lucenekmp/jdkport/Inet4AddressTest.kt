package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith

class Inet4AddressTest {

    @Test
    fun testDefaultConstructor() {
        val addr = Inet4Address()
        assertContentEquals(byteArrayOf(0, 0, 0, 0), addr.getAddress())
        assertEquals("0.0.0.0", addr.getHostAddress())
    }

    @Test
    fun testConstructorWithHostAndBytes() {
        val bytes = byteArrayOf(127, 0, 0, 1)
        val addr = Inet4Address("localhost", bytes)
        assertContentEquals(bytes, addr.getAddress())
        assertEquals("127.0.0.1", addr.getHostAddress())
    }

    @Test
    fun testConstructorWithHostAndInt() {
        val ipInt = (127 shl 24) or (0 shl 16) or (0 shl 8) or 1
        val addr = Inet4Address("localhost", ipInt)
        assertContentEquals(byteArrayOf(127, 0, 0, 1), addr.getAddress())
        assertEquals("127.0.0.1", addr.getHostAddress())
    }

    @Test
    fun testEqualsAndHashCode() {
        val bytes = byteArrayOf(10, 0, 0, 1)
        val ipInt = (10 shl 24) or (0 shl 16) or (0 shl 8) or 1
        val a = Inet4Address("a", bytes)
        val b = Inet4Address("b", ipInt)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testNotEquals() {
        val a = Inet4Address("a", byteArrayOf(1, 2, 3, 4))
        val b = Inet4Address("b", byteArrayOf(4, 3, 2, 1))
        assertNotEquals(a, b)
    }

    @Test
    fun testNumericToTextFormat() {
        assertEquals("192.168.1.100", Inet4Address.numericToTextFormat(byteArrayOf(192.toByte(), 168.toByte(), 1, 100)))
        assertEquals("0.0.0.0", Inet4Address.numericToTextFormat(byteArrayOf(0, 0, 0, 0)))
        assertEquals("255.255.255.255", Inet4Address.numericToTextFormat(byteArrayOf(-1, -1, -1, -1)))
        assertEquals("127.0.0.1", Inet4Address.numericToTextFormat(byteArrayOf(127, 0, 0, 1)))
        assertEquals("10.0.0.1", Inet4Address.numericToTextFormat(byteArrayOf(10, 0, 0, 1)))
    }

    @Test
    fun testAddressLength() {
        assertFailsWith<IllegalArgumentException> {
            Inet4Address("host", byteArrayOf(127, 0, 0)) // Only 3 bytes
        }
        assertFailsWith<IllegalArgumentException> {
            Inet4Address("host", byteArrayOf(127, 0, 0, 1, 2)) // 5 bytes
        }
    }
}
