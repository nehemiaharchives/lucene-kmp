package org.gnit.lucenekmp.jdkport


/**
 * port of java.net.Inet4Address
 *
 * This class represents an Internet Protocol version 4 (IPv4) address.
 * Defined by <a href="http://www.ietf.org/rfc/rfc790.txt">
 * <i>RFC&nbsp;790: Assigned Numbers</i></a>,
 * <a href="http://www.ietf.org/rfc/rfc1918.txt">
 * <i>RFC&nbsp;1918: Address Allocation for Private Internets</i></a>,
 * and <a href="http://www.ietf.org/rfc/rfc2365.txt"><i>RFC&nbsp;2365:
 * Administratively Scoped IP Multicast</i></a>
 *
 * <h2> <a id="format">Textual representation of IPv4 addresses</a> </h2>
 *
 * Textual representation of IPv4 address used as input to methods
 * takes one of the following forms:
 *
 * <blockquote><ul style="list-style-type:none">
 * <li>{@code d.d.d.d}</li>
 * <li>{@code d.d.d}</li>
 * <li>{@code d.d}</li>
 * <li>{@code d}</li>
 * </ul></blockquote>
 *
 * <p> When four parts are specified, each is interpreted as a byte of
 * data and assigned, from left to right, to the four bytes of an IPv4
 * address.
 *
 * <p> When a three part address is specified, the last part is
 * interpreted as a 16-bit quantity and placed in the right most two
 * bytes of the network address. This makes the three part address
 * format convenient for specifying Class B network addresses as
 * 128.net.host.
 *
 * <p> When a two part address is supplied, the last part is
 * interpreted as a 24-bit quantity and placed in the right most three
 * bytes of the network address. This makes the two part address
 * format convenient for specifying Class A network addresses as
 * net.host.
 *
 * <p> When only one part is given, the value is stored directly in
 * the network address without any byte rearrangement.
 *
 * <p> For example, the following (decimal) forms are supported by the methods
 * {@link Inet4Address#ofLiteral(String)} and {@link InetAddress#getByName(String)}
 * which are capable of parsing textual representations of IPv4 addresses:
 * {@snippet :
 *  // Dotted-decimal 'd.d.d.d' form with four part address literal
 *  InetAddress.getByName("007.008.009.010"); // ==> /7.8.9.10
 *  InetAddress.getByName("127.0.1.1");       // ==> /127.0.1.1
 *
 *  // Dotted-decimal 'd.d.d' form with three part address literal,
 *  // the last part is placed in the right most two bytes
 *  // of the constructed address
 *  InetAddress.getByName("127.0.257"); // ==> /127.0.1.1
 *
 *  // Dotted-decimal 'd.d' form with two part address literal,
 *  // the last part is placed in the right most three bytes
 *  // of the constructed address
 *  Inet4Address.ofLiteral("127.257"); // ==> /127.0.1.1
 *
 *  // 'd' form with one decimal value that is stored directly in
 *  // the constructed address bytes without any rearrangement
 *  Inet4Address.ofLiteral("02130706689"); // ==> /127.0.1.1
 * }
 *
 * <p> The above forms adhere to "strict" decimal-only syntax.
 * Additionally, the {@link Inet4Address#ofPosixLiteral(String)}
 * method implements a POSIX {@code inet_addr} compatible "loose"
 * parsing algorithm, allowing octal and hexadecimal address segments.
 * Please refer to <a href="https://www.ietf.org/rfc/rfc6943.html#section-3.1.1">
 * <i>RFC&nbsp;6943: Issues in Identifier Comparison for Security
 * Purposes</i></a>. Aside from {@code Inet4Address.ofPosixLiteral(String)}, all methods only
 * support strict decimal parsing.
 * <p> For methods that return a textual representation as output
 * value, the first form, i.e. a dotted-quad string in strict decimal notation, is used.
 *
 * <h3> The Scope of a Multicast Address </h3>
 *
 * Historically the IPv4 TTL field in the IP header has doubled as a
 * multicast scope field: a TTL of 0 means node-local, 1 means
 * link-local, up through 32 means site-local, up through 64 means
 * region-local, up through 128 means continent-local, and up through
 * 255 are global. However, the administrative scoping is preferred.
 * Please refer to <a href="http://www.ietf.org/rfc/rfc2365.txt">
 * <i>RFC&nbsp;2365: Administratively Scoped IP Multicast</i></a>
 *
 * @spec https://www.rfc-editor.org/info/rfc1918
 *      RFC 1918: Address Allocation for Private Internets
 * @spec https://www.rfc-editor.org/info/rfc2365
 *      RFC 2365: Administratively Scoped IP Multicast
 * @spec https://www.rfc-editor.org/info/rfc790
 *      RFC 790: Assigned numbers
 * @spec https://www.rfc-editor.org/rfc/rfc6943.html#section-3.1.1
 *      RFC 6943: Issues in Identifier Comparison for Security Purposes
 * @since 1.4
 */
class Inet4Address : InetAddress {

    constructor() : super() {
        holder().hostName = null
        holder().address = 0
        holder().family = IPv4
    }

    constructor(hostName: String?, addr: ByteArray?) {
        holder().hostName = hostName
        holder().family = IPv4
        if (addr != null) {
            if (addr.size == INADDRSZ) {
                var address = addr[3].toInt() and 0xFF
                address = address or ((addr[2].toInt() shl 8) and 0xFF00)
                address = address or ((addr[1].toInt() shl 16) and 0xFF0000)
                address = address or ((addr[0].toInt() shl 24) and -0x1000000)
                holder().address = address
            }else {
                // this is not implemented in jdk but I think it's good to have
                throw IllegalArgumentException("Invalid address length: ${addr.size}")
            }
        }
        holder().originalHostName = hostName
    }

    constructor(hostName: String?, address: Int) {
        holder().hostName = hostName
        holder().family = IPv4
        holder().address = address
        holder().originalHostName = hostName
    }

    /**
     * Returns the IP address string in textual presentation form.
     *
     * @return  the raw IP address in a string format.
     */
    override fun getHostAddress(): String {
        return numericToTextFormat(getAddress())
    }

    /**
     * Returns the raw IP address of this `InetAddress`
     * object. The result is in network byte order: the highest order
     * byte of the address is in `getAddress()[0]`.
     *
     * @return  the raw IP address of this object.
     */
    override fun getAddress(): ByteArray {
        val address: Int = holder().address
        val addr = ByteArray(INADDRSZ)

        addr[0] = ((address ushr 24) and 0xFF).toByte()
        addr[1] = ((address ushr 16) and 0xFF).toByte()
        addr[2] = ((address ushr 8) and 0xFF).toByte()
        addr[3] = (address and 0xFF).toByte()
        return addr
    }

    /**
     * Returns a hashcode for this IP address.
     *
     * @return  a hash code value for this IP address.
     */
    override fun hashCode(): Int {
        return holder().address
    }

    /**
     * Compares this object against the specified object.
     * The result is `true` if and only if the argument is
     * not `null` and it represents the same IP address as
     * this object.
     *
     *
     * Two instances of `InetAddress` represent the same IP
     * address if the length of the byte arrays returned by
     * `getAddress` is the same for both, and each of the
     * array components is the same for the byte arrays.
     *
     * @param   obj   the object to compare against.
     * @return  `true` if the objects are the same;
     * `false` otherwise.
     * @see java.net.InetAddress.getAddress
     */
    override fun equals(obj: Any?): Boolean {
        return (obj is Inet4Address) &&
                obj.holder().address == holder().address
    }

    // Utilities
    companion object {
        const val INADDRSZ: Int = 4

        /**
         * Converts IPv4 binary address into a string suitable for presentation.
         *
         * @param src a byte array representing an IPv4 numeric address
         * @return a String representing the IPv4 address in
         * textual representation format
         */
        fun numericToTextFormat(src: ByteArray): String {
            return (src[0].toInt() and 0xff).toString() + "." + (src[1].toInt() and 0xff) + "." + (src[2].toInt() and 0xff) + "." + (src[3].toInt() and 0xff)
        }
    }
}