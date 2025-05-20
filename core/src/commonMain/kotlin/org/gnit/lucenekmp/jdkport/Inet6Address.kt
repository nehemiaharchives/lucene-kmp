package org.gnit.lucenekmp.jdkport

import kotlin.experimental.or


/**
 * port of java.net.Inet6Address
 *
 * minimum implementation to support usage in lucene
 *
 * This class represents an Internet Protocol version 6 (IPv6) address.
 * Defined by <a href="http://www.ietf.org/rfc/rfc2373.txt">
 * <i>RFC&nbsp;2373: IP Version 6 Addressing Architecture</i></a>.
 *
 * <h2> <a id="format">Textual representation of IPv6 addresses</a> </h2>
 *
 * Textual representation of IPv6 address used as input to methods
 * takes one of the following forms:
 *
 * <ol>
 *   <li><p> <a id="lform">The preferred form</a> is x:x:x:x:x:x:x:x,
 *   where the 'x's are
 *   the hexadecimal values of the eight 16-bit pieces of the
 *   address. This is the full form.  For example,
 *
 *   <blockquote><ul style="list-style-type:none">
 *   <li>{@code 1080:0:0:0:8:800:200C:417A}</li>
 *   </ul></blockquote>
 *
 *   <p> Note that it is not necessary to write the leading zeros in
 *   an individual field. However, there must be at least one numeral
 *   in every field, except as described below.</li>
 *
 *   <li><p> Due to some methods of allocating certain styles of IPv6
 *   addresses, it will be common for addresses to contain long
 *   strings of zero bits. In order to make writing addresses
 *   containing zero bits easier, a special syntax is available to
 *   compress the zeros. The use of "::" indicates multiple groups
 *   of 16-bits of zeros. The "::" can only appear once in an address.
 *   The "::" can also be used to compress the leading and/or trailing
 *   zeros in an address. For example,
 *
 *   <blockquote><ul style="list-style-type:none">
 *   <li>{@code 1080::8:800:200C:417A}</li>
 *   </ul></blockquote>
 *
 *   <li><p> An alternative form that is sometimes more convenient
 *   when dealing with a mixed environment of IPv4 and IPv6 nodes is
 *   x:x:x:x:x:x:d.d.d.d, where the 'x's are the hexadecimal values
 *   of the six high-order 16-bit pieces of the address, and the 'd's
 *   are the decimal values of the four low-order 8-bit pieces of the
 *   standard IPv4 representation address, for example,
 *
 *   <blockquote><ul style="list-style-type:none">
 *   <li>{@code ::FFFF:129.144.52.38}</li>
 *   <li>{@code ::129.144.52.38}</li>
 *   </ul></blockquote>
 *
 *   <p> where "::FFFF:d.d.d.d" and "::d.d.d.d" are, respectively, the
 *   general forms of an IPv4-mapped IPv6 address and an
 *   IPv4-compatible IPv6 address. Note that the IPv4 portion must be
 *   in the "d.d.d.d" form. The following forms are invalid:
 *
 *   <blockquote><ul style="list-style-type:none">
 *   <li>{@code ::FFFF:d.d.d}</li>
 *   <li>{@code ::FFFF:d.d}</li>
 *   <li>{@code ::d.d.d}</li>
 *   <li>{@code ::d.d}</li>
 *   </ul></blockquote>
 *
 *   <p> The following form:
 *
 *   <blockquote><ul style="list-style-type:none">
 *   <li>{@code ::FFFF:d}</li>
 *   </ul></blockquote>
 *
 *   <p> is valid, however it is an unconventional representation of
 *   the IPv4-compatible IPv6 address,
 *
 *   <blockquote><ul style="list-style-type:none">
 *   <li>{@code ::255.255.0.d}</li>
 *   </ul></blockquote>
 *
 *   <p> while "::d" corresponds to the general IPv6 address
 *   "0:0:0:0:0:0:0:d".</li>
 * </ol>
 *
 * <p> For methods that return a textual representation as output
 * value, the full form is used. Inet6Address will return the full
 * form because it is unambiguous when used in combination with other
 * textual data.
 *
 * <h3><a id="special-ipv6-address">Special IPv6 address</a></h3>
 *
 * <blockquote>
 * <dl>
 *   <dt>IPv4-mapped address</dt>
 *         <dd>Of the form ::ffff:w.x.y.z, this IPv6 address is used to
 *         represent an IPv4 address. It allows the native program to
 *         use the same address data structure and also the same
 *         socket when communicating with both IPv4 and IPv6 nodes.
 *
 *         <p>In InetAddress and Inet6Address, it is used for internal
 *         representation; it has no functional role. Java will never
 *         return an IPv4-mapped address.  These classes can take an
 *         IPv4-mapped address as input, both in byte array and text
 *         representation. However, it will be converted into an IPv4
 *         address.</dd>
 * </dl>
 * </blockquote>
 *
 * <h3><a id="scoped">Textual representation of IPv6 scoped addresses</a></h3>
 *
 * <p>The textual representation of IPv6 addresses as described above can be
 * extended to specify IPv6 scoped addresses. This extension to the basic
 * addressing architecture is described in <a href="https://www.rfc-editor.org/info/rfc4007">
 * <i>RFC&nbsp;4007: IPv6 Scoped Address Architecture</i></a>.
 *
 * <p> Because link-local and site-local addresses are non-global, it is possible
 * that different hosts may have the same destination address and may be
 * reachable through different interfaces on the same originating system. In
 * this case, the originating system is said to be connected to multiple zones
 * of the same scope. In order to disambiguate which is the intended destination
 * zone, it is possible to append a zone identifier (or <i>scope_id</i>) to an
 * IPv6 address.
 *
 * <p> The general format for specifying the <i>scope_id</i> is the following:
 *
 * <blockquote><i>IPv6-address</i>%<i>scope_id</i></blockquote>
 * <p> The IPv6-address is a literal IPv6 address as described above.
 * The <i>scope_id</i> refers to an interface on the local system, and it can be
 * specified in two ways.
 * <ol><li><i>As a numeric identifier.</i> This must be a positive integer
 * that identifies the particular interface and scope as understood by the
 * system. Usually, the numeric values can be determined through administration
 * tools on the system. Each interface may have multiple values, one for each
 * scope. If the scope is unspecified, then the default value used is zero.</li>
 * <li><i>As a string.</i> This must be the exact string that is returned by
 * {@link NetworkInterface#getName()} for the particular interface in
 * question. When an Inet6Address is created in this way, the numeric scope-id
 * is determined at the time the object is created by querying the relevant
 * NetworkInterface.</li></ol>
 *
 * <p> Note also, that the numeric <i>scope_id</i> can be retrieved from
 * Inet6Address instances returned from the NetworkInterface class. This can be
 * used to find out the current scope ids configured on the system.
 *
 * <h3><a id="input">Textual representation of IPv6 addresses as method inputs</a></h3>
 *
 * <p> Methods of {@code InetAddress} and {@code Inet6Address} that accept a
 * textual representation of an IPv6 address allow for that representation
 * to be enclosed in square brackets. For example,
 * {@snippet :
 *  // The full IPv6 form
 *  InetAddress.getByName("1080:0:0:0:8:800:200C:417A");   // ==> /1080:0:0:0:8:800:200c:417a
 *  InetAddress.getByName("[1080:0:0:0:8:800:200C:417A]"); // ==> /1080:0:0:0:8:800:200c:417a
 *
 *  // IPv6 scoped address with scope-id as string
 *  Inet6Address.ofLiteral("fe80::1%en0");   // ==> /fe80:0:0:0:0:0:0:1%en0
 *  Inet6Address.ofLiteral("[fe80::1%en0]"); // ==> /fe80:0:0:0:0:0:0:1%en0
 * }
 * @spec https://www.rfc-editor.org/info/rfc2373
 *      RFC 2373: IP Version 6 Addressing Architecture
 * @spec https://www.rfc-editor.org/info/rfc4007
 *      RFC 4007: IPv6 Scoped Address Architecture
 * @since 1.4
 */
class Inet6Address: InetAddress {

    val holder6: Inet6AddressHolder

    constructor() :super() {
        holder.init(null, IPv6)
        holder6 = Inet6AddressHolder()
    }

    /* checking of value for scope_id should be done by caller
     * scope_id must be >= 0, or -1 to indicate not being set
     */
    constructor(hostName: String?, addr: ByteArray, scope_id: Int) {
        holder.init(hostName, IPv6)
        holder6 = Inet6AddressHolder()
        holder6.init(addr, scope_id)
    }


    constructor(hostName: String?, addr: ByteArray) {
        holder6 = Inet6AddressHolder()
        try {
            initif(hostName, addr, null)
        } catch (e: UnknownHostException) {
        } /* can't happen if ifname is null */
    }

    @Throws(UnknownHostException::class)
    private fun initif(hostName: String?, addr: ByteArray, nif: NetworkInterface?) {
        var family = -1
        holder6.init(addr, nif)

        if (addr.size == INADDRSZ) { // normal IPv6 address
            family = IPv6
        }
        holder.init(hostName, family)
    }

    override fun getHostAddress(): String {
        return holder6.hostAddress
    }

    override fun getAddress(): ByteArray {
        return holder6.ipaddress.copyOf()
    }

    companion object {
        const val INADDRSZ: Int = 16

        /**
         * empty port of java.net.NetworkInterface
         *
         * this is empty implementation and always null, I think this is not needed to run lucene, implement if needed
         */
        class NetworkInterface

        class Inet6AddressHolder {
            constructor() {
                ipaddress = ByteArray(INADDRSZ)
            }

            constructor(
                ipaddress: ByteArray, scope_id: Int, scope_id_set: Boolean,
                ifname: NetworkInterface?, scope_ifname_set: Boolean
            ) {
                this.ipaddress = ipaddress
                this.scope_id = scope_id
                this.scope_id_set = scope_id_set
                this.scope_ifname_set = scope_ifname_set
                this.scope_ifname = ifname
            }

            /**
             * Holds a 128-bit (16 bytes) IPv6 address.
             */
            var ipaddress: ByteArray

            /**
             * scope_id. The scope specified when the object is created. If the object
             * is created with an interface name, then the scope_id is not determined
             * until the time it is needed.
             */
            var scope_id: Int = 0 // 0

            /**
             * This will be set to true when the scope_id field contains a valid
             * integer scope_id.
             */
            var scope_id_set: Boolean = false // false

            /**
             * scoped interface. scope_id is derived from this as the scope_id of the first
             * address whose scope is the same as this address for the named interface.
             */
            var scope_ifname: NetworkInterface? = null // null

            /**
             * set if the object is constructed with a scoped
             * interface instead of a numeric scope id.
             */
            var scope_ifname_set: Boolean = false // false;

            fun setAddr(addr: ByteArray) {
                if (addr.size == INADDRSZ) { // normal IPv6 address
                    System.arraycopy(addr, 0, ipaddress, 0, INADDRSZ)
                }
            }

            fun init(addr: ByteArray, scope_id: Int) {
                setAddr(addr)

                if (scope_id >= 0) {
                    this.scope_id = scope_id
                    this.scope_id_set = true
                }
            }

            @Throws(UnknownHostException::class)
            fun init(addr: ByteArray, nif: NetworkInterface?) {
                setAddr(addr)

                if (nif != null) {
                    this.scope_id = 0 /*Inet6Address.deriveNumericScope(ipaddress, nif)*/ // not implementing
                    this.scope_id_set = true
                    this.scope_ifname = nif
                    this.scope_ifname_set = true
                }
            }

            val hostAddress: String
                get() {
                    var s: String = Inet6Address.numericToTextFormat(ipaddress)
                    if (scope_ifname != null) { /* must check this first */
                        s = "$s%" // not implementing
                    } else if (scope_id_set) {
                        s = "$s%$scope_id"
                    }
                    return s
                }

            override fun equals(o: Any?): Boolean {
                if (o !is Inet6AddressHolder) {
                    return false
                }

                return this.ipaddress.contentEquals(o.ipaddress)
            }

            override fun hashCode(): Int {
                if (ipaddress != null) {
                    var hash = 0
                    var i = 0
                    while (i < INADDRSZ) {
                        var j = 0
                        var component = 0
                        while (j < 4 && i < INADDRSZ) {
                            component = (component shl 8) + ipaddress[i]
                            j++
                            i++
                        }
                        hash += component
                    }
                    return hash
                } else {
                    return 0
                }
            }

            val isIPv4CompatibleAddress: Boolean
                get() {
                    if ((ipaddress[0].toInt() == 0x00) && (ipaddress[1].toInt() == 0x00) &&
                        (ipaddress[2].toInt() == 0x00) && (ipaddress[3].toInt() == 0x00) &&
                        (ipaddress[4].toInt() == 0x00) && (ipaddress[5].toInt() == 0x00) &&
                        (ipaddress[6].toInt() == 0x00) && (ipaddress[7].toInt() == 0x00) &&
                        (ipaddress[8].toInt() == 0x00) && (ipaddress[9].toInt() == 0x00) &&
                        (ipaddress[10].toInt() == 0x00) && (ipaddress[11].toInt() == 0x00)
                    ) {
                        return true
                    }
                    return false
                }

            val isMulticastAddress: Boolean
                get() = ((ipaddress[0].toInt() and 0xff) == 0xff)

            val isAnyLocalAddress: Boolean
                get() {
                    var test: Byte = 0x00
                    for (i in 0..<INADDRSZ) {
                        test = test or ipaddress[i]
                    }
                    return (test.toInt() == 0x00)
                }

            val isLoopbackAddress: Boolean
                get() {
                    var test: Byte = 0x00
                    for (i in 0..14) {
                        test = test or ipaddress[i]
                    }
                    return (test.toInt() == 0x00) && (ipaddress[15].toInt() == 0x01)
                }

            val isLinkLocalAddress: Boolean
                get() = ((ipaddress[0].toInt() and 0xff) == 0xfe
                        && (ipaddress[1].toInt() and 0xc0) == 0x80)


            val isSiteLocalAddress: Boolean
                get() = ((ipaddress[0].toInt() and 0xff) == 0xfe
                        && (ipaddress[1].toInt() and 0xc0) == 0xc0)

            val isMCGlobal: Boolean
                get() = ((ipaddress[0].toInt() and 0xff) == 0xff
                        && (ipaddress[1].toInt() and 0x0f) == 0x0e)

            val isMCNodeLocal: Boolean
                get() = ((ipaddress[0].toInt() and 0xff) == 0xff
                        && (ipaddress[1].toInt() and 0x0f) == 0x01)

            val isMCLinkLocal: Boolean
                get() = ((ipaddress[0].toInt() and 0xff) == 0xff
                        && (ipaddress[1].toInt() and 0x0f) == 0x02)

            val isMCSiteLocal: Boolean
                get() = ((ipaddress[0].toInt() and 0xff) == 0xff
                        && (ipaddress[1].toInt() and 0x0f) == 0x05)

            val isMCOrgLocal: Boolean
                get() = ((ipaddress[0].toInt() and 0xff) == 0xff
                        && (ipaddress[1].toInt() and 0x0f) == 0x08)
        }


        // Utilities
        const val INT16SZ: Int = 2

        /**
         * Convert IPv6 binary address into presentation (printable) format.
         *
         * @param src a byte array representing the IPv6 numeric address
         * @return a String representing an IPv6 address in
         * textual representation format
         */
        fun numericToTextFormat(src: ByteArray): String {
            val sb = StringBuilder(39)
            for (i in 0..<(INADDRSZ / INT16SZ)) {
                sb.append(
                    Int.toHexString(
                        ((src[i shl 1].toInt() shl 8) and 0xff00)
                                or (src[(i shl 1) + 1].toInt() and 0xff)
                    )
                )
                if (i < (INADDRSZ / INT16SZ) - 1) {
                    sb.append(":")
                }
            }
            return sb.toString()
        }

        // end of companion object
    }
}
