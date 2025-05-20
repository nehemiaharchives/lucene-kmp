package org.gnit.lucenekmp.jdkport

/**
 * port of sun.net.util.IPAddressUtil
 *
 * minimum implementation to support usage in lucene
 */
object IPAddressUtil {
    private const val INADDR4SZ: Int = 4
    private const val INADDR16SZ: Int = 16
    private const val INT16SZ: Int = 2

    /**
     * Convert IPv4-Mapped address to IPv4 address. Both input and
     * returned value are in network order binary form.
     *
     * @param src a String representing an IPv4-Mapped address in textual format
     * @return a byte array representing the IPv4 numeric address
     */
    fun convertFromIPv4MappedAddress(addr: ByteArray): ByteArray? {
        if (isIPv4MappedAddress(addr)) {
            val newAddr = ByteArray(INADDR4SZ)
            System.arraycopy(addr, 12, newAddr, 0, INADDR4SZ)
            return newAddr
        }
        return null
    }

    /**
     * Utility routine to check if the InetAddress is an
     * IPv4 mapped IPv6 address.
     *
     * @return a `boolean` indicating if the InetAddress is
     * an IPv4 mapped IPv6 address; or false if address is IPv4 address.
     */
    private fun isIPv4MappedAddress(addr: ByteArray): Boolean {
        if (addr.size < INADDR16SZ) {
            return false
        }
        if ((addr[0].toInt() == 0x00) && (addr[1].toInt() == 0x00) &&
            (addr[2].toInt() == 0x00) && (addr[3].toInt() == 0x00) &&
            (addr[4].toInt() == 0x00) && (addr[5].toInt() == 0x00) &&
            (addr[6].toInt() == 0x00) && (addr[7].toInt() == 0x00) &&
            (addr[8].toInt() == 0x00) && (addr[9].toInt() == 0x00) &&
            (addr[10] == 0xff.toByte()) &&
            (addr[11] == 0xff.toByte())
        ) {
            return true
        }
        return false
    }
}
