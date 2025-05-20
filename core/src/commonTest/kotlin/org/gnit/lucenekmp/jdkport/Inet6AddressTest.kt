package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertContentEquals

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class Inet6AddressTest {

    @Test
    fun testInet6AddressHolderSetAddrAndInit() {
        val addr = ByteArray(16) { it.toByte() }
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        assertContentEquals(addr, holder.ipaddress)
        holder.init(addr, 5)
        assertEquals(5, holder.scope_id)
        assertTrue(holder.scope_id_set)
    }

    @Test
    fun testInet6AddressHolderHostAddress() {
        val addr = ByteArray(16) { it.toByte() }
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        val expected = Inet6Address.numericToTextFormat(addr)
        assertEquals(expected, holder.hostAddress)
    }

    @Test
    fun testInet6AddressHolderEqualsAndHashCode() {
        val addr1 = ByteArray(16) { it.toByte() }
        val addr2 = ByteArray(16) { it.toByte() }
        val holder1 = Inet6Address.Companion.Inet6AddressHolder()
        val holder2 = Inet6Address.Companion.Inet6AddressHolder()
        holder1.setAddr(addr1)
        holder2.setAddr(addr2)
        assertTrue(holder1 == holder2)
        assertEquals(holder1.hashCode(), holder2.hashCode())
    }

    @Test
    fun testIsIPv4CompatibleAddress() {
        val addr = ByteArray(16) { 0 }
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        assertTrue(holder.isIPv4CompatibleAddress)
        addr[12] = 1
        holder.setAddr(addr)
        assertTrue(holder.isIPv4CompatibleAddress)
        addr[0] = 1
        holder.setAddr(addr)
        assertFalse(holder.isIPv4CompatibleAddress)
    }

    @Test
    fun testIsMulticastAddress() {
        val addr = ByteArray(16) { 0 }
        addr[0] = 0xff.toByte()
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        assertTrue(holder.isMulticastAddress)
        addr[0] = 0xfe.toByte()
        holder.setAddr(addr)
        assertFalse(holder.isMulticastAddress)
    }

    @Test
    fun testIsAnyLocalAddress() {
        val addr = ByteArray(16) { 0 }
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        assertTrue(holder.isAnyLocalAddress)
        addr[1] = 1
        holder.setAddr(addr)
        assertFalse(holder.isAnyLocalAddress)
    }

    @Test
    fun testIsLoopbackAddress() {
        val addr = ByteArray(16) { 0 }
        addr[15] = 1
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        assertTrue(holder.isLoopbackAddress)
        addr[0] = 1
        holder.setAddr(addr)
        assertFalse(holder.isLoopbackAddress)
    }

    @Test
    fun testIsLinkLocalAddress() {
        val addr = ByteArray(16) { 0 }
        addr[0] = 0xfe.toByte()
        addr[1] = 0x80.toByte()
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        assertTrue(holder.isLinkLocalAddress)
        addr[1] = 0x00.toByte()
        holder.setAddr(addr)
        assertFalse(holder.isLinkLocalAddress)
    }

    @Test
    fun testIsSiteLocalAddress() {
        val addr = ByteArray(16) { 0 }
        addr[0] = 0xfe.toByte()
        addr[1] = 0xc0.toByte()
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        assertTrue(holder.isSiteLocalAddress)
        addr[1] = 0x80.toByte()
        holder.setAddr(addr)
        assertFalse(holder.isSiteLocalAddress)
    }

    @Test
    fun testIsMCGlobal() {
        val addr = ByteArray(16) { 0 }
        addr[0] = 0xff.toByte()
        addr[1] = 0x0e.toByte()
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        assertTrue(holder.isMCGlobal)
        addr[1] = 0x01.toByte()
        holder.setAddr(addr)
        assertFalse(holder.isMCGlobal)
    }

    @Test
    fun testIsMCNodeLocal() {
        val addr = ByteArray(16) { 0 }
        addr[0] = 0xff.toByte()
        addr[1] = 0x01.toByte()
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        assertTrue(holder.isMCNodeLocal)
        addr[1] = 0x02.toByte()
        holder.setAddr(addr)
        assertFalse(holder.isMCNodeLocal)
    }

    @Test
    fun testIsMCLinkLocal() {
        val addr = ByteArray(16) { 0 }
        addr[0] = 0xff.toByte()
        addr[1] = 0x02.toByte()
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        assertTrue(holder.isMCLinkLocal)
        addr[1] = 0x01.toByte()
        holder.setAddr(addr)
        assertFalse(holder.isMCLinkLocal)
    }

    @Test
    fun testIsMCSiteLocal() {
        val addr = ByteArray(16) { 0 }
        addr[0] = 0xff.toByte()
        addr[1] = 0x05.toByte()
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        assertTrue(holder.isMCSiteLocal)
        addr[1] = 0x08.toByte()
        holder.setAddr(addr)
        assertFalse(holder.isMCSiteLocal)
    }

    @Test
    fun testIsMCOrgLocal() {
        val addr = ByteArray(16) { 0 }
        addr[0] = 0xff.toByte()
        addr[1] = 0x08.toByte()
        val holder = Inet6Address.Companion.Inet6AddressHolder()
        holder.setAddr(addr)
        assertTrue(holder.isMCOrgLocal)
        addr[1] = 0x05.toByte()
        holder.setAddr(addr)
        assertFalse(holder.isMCOrgLocal)
    }

    @Test
    fun testNumericToTextFormat() {
        val addr = byteArrayOf(
            0x20, 0x01, 0x0d, 0xb8.toByte(), 0x85.toByte(), 0xa3.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x8a.toByte(), 0x2e, 0x03, 0x70, 0x73, 0x34
        )
        val text = Inet6Address.numericToTextFormat(addr)
        assertEquals("2001:db8:85a3:0:0:8a2e:370:7334", text)
    }

    @Test
    fun testInet6AddressGetHostAddressAndGetAddress() {
        val addr = ByteArray(16) { it.toByte() }
        val inet6 = Inet6Address("host", addr, 2)
        assertEquals(Inet6Address.numericToTextFormat(addr) + "%2", inet6.getHostAddress())
        assertContentEquals(addr, inet6.getAddress())
    }
}
