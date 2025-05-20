package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class IPAddressUtilTest {

    @Test
    fun testConvertFromIPv4MappedAddress_valid() {
        // 0:0:0:0:0:ffff:192.168.1.1 -> [0,0,0,0,0,0,0,0,0,0,255,255,192,168,1,1]
        val ipv4Mapped = byteArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff.toByte(), 0xff.toByte(),
            192.toByte(), 168.toByte(), 1, 1
        )
        val expected = byteArrayOf(192.toByte(), 168.toByte(), 1, 1)
        val result = IPAddressUtil.convertFromIPv4MappedAddress(ipv4Mapped)
        logger.debug { "Result: ${result?.joinToString()}" }
        assertContentEquals(expected, result)
    }

    @Test
    fun testConvertFromIPv4MappedAddress_invalid_notMapped() {
        // Not a mapped address (wrong prefix)
        val notMapped = byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
        )
        val result = IPAddressUtil.convertFromIPv4MappedAddress(notMapped)
        logger.debug { "Result for not mapped: $result" }
        assertNull(result)
    }

    @Test
    fun testConvertFromIPv4MappedAddress_invalid_tooShort() {
        // Too short to be a mapped address
        val tooShort = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff.toByte(), 0xff.toByte())
        val result = IPAddressUtil.convertFromIPv4MappedAddress(tooShort)
        logger.debug { "Result for too short: $result" }
        assertNull(result)
    }
}
