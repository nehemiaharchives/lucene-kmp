package org.gnit.lucenekmp.jdkport

/**
 * minimum implementation to support usage in lucene
 */
@Ported(from = "java.net.InetAddress")
abstract class InetAddress {

    /* Used to store the serializable fields of InetAddress */
    val holder: InetAddressHolder = InetAddressHolder()

    /**
     * Constructor for the Socket.accept() method.
     * This creates an empty InetAddress, which is filled in by
     * the accept() method.  This InetAddress, however, is not
     * put in the address cache, since it is not created by name.
     */
    constructor()

    /**
     * Returns the IP address string in textual presentation.
     *
     * @return  the raw IP address in a string format.
     * @since   1.0.2
     */
    abstract fun getHostAddress(): String

    /**
     * Returns the raw IP address of this `InetAddress`
     * object. The result is in network byte order: the highest order
     * byte of the address is in `getAddress()[0]`.
     *
     * @return  the raw IP address of this object.
     */
    abstract fun getAddress(): ByteArray

    /**
     * Returns a hashcode for this IP address.
     *
     * @return  a hash code value for this IP address.
     */
    override fun hashCode(): Int {
        return -1
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
        return false
    }

    /**
     * Converts this IP address to a `String`. The
     * string returned is of the form: hostname / literal IP
     * address.
     *
     * If the host name is unresolved, no reverse lookup
     * is performed. The hostname part will be represented
     * by an empty string.
     *
     * @return  a string representation of this IP address.
     */
    override fun toString(): String {
        return "${holder().hostName.orEmpty()}/${getHostAddress()}"
    }

    fun holder(): InetAddressHolder {
        return holder!!
    }

    companion object {

        /**
         * Specify the address family: Internet Protocol, Version 4
         * @since 1.4
         */
        const val IPv4: Int = 1

        /**
         * Specify the address family: Internet Protocol, Version 6
         * @since 1.4
         */
        const val IPv6: Int = 2

        class InetAddressHolder {
            /**
             * Reserve the original application specified hostname.
             *
             * The original hostname is useful for domain-based endpoint
             * identification (see RFC 2818 and RFC 6125).  If an address
             * was created with a raw IP address, a reverse name lookup
             * may introduce endpoint identification security issue via
             * DNS forging.
             *
             * Oracle JSSE provider is using this original hostname, via
             * jdk.internal.misc.JavaNetAccess, for SSL/TLS endpoint identification.
             *
             * Note: May define a new public method in the future if necessary.
             */
            var originalHostName: String? = null

            internal constructor()

            internal constructor(hostName: String?, address: Int, family: Int) {
                this.originalHostName = hostName
                this.hostName = hostName
                this.address = address
                this.family = family
            }

            fun init(hostName: String?, family: Int) {
                this.originalHostName = hostName
                this.hostName = hostName
                if (family != -1) {
                    this.family = family
                }
            }

            var hostName: String? = null

            /**
             * Holds a 32-bit IPv4 address.
             */
            var address: Int = 0

            /**
             * Specifies the address family type, for instance, '1' for IPv4
             * addresses, and '2' for IPv6 addresses.
             */
            var family: Int = 0
        }


        /**
         * Returns an `InetAddress` object given the raw IP address .
         * The argument is in network byte order: the highest order
         * byte of the address is in `getAddress()[0]`.
         *
         *
         *  This method doesn't block, i.e. no reverse lookup is performed.
         *
         *
         *  IPv4 address byte array must be 4 bytes long and IPv6 byte array
         * must be 16 bytes long
         *
         * @param addr the raw IP address in network byte order
         * @return  an InetAddress object created from the raw IP address.
         * @throws     UnknownHostException  if IP address is of illegal length
         * @since 1.4
         */
        @Throws(UnknownHostException::class)
        fun getByAddress(addr: ByteArray?): InetAddress {
            return getByAddress(null, addr)
        }

        /**
         * Creates an InetAddress based on the provided host name and IP address.
         * The system-wide [resolver][InetAddressResolver] is not used to check
         * the validity of the address.
         *
         *
         *  The host name can either be a machine name, such as
         * "`www.example.com`", or a textual representation of its IP
         * address.
         *
         *  No validity checking is done on the host name either.
         *
         *
         *  If addr specifies an IPv4 address an instance of Inet4Address
         * will be returned; otherwise, an instance of Inet6Address
         * will be returned.
         *
         *
         *  IPv4 address byte array must be 4 bytes long and IPv6 byte array
         * must be 16 bytes long
         *
         * @param host the specified host
         * @param addr the raw IP address in network byte order
         * @return  an InetAddress object created from the raw IP address.
         * @throws     UnknownHostException  if IP address is of illegal length
         * @since 1.4
         */
        @Throws(UnknownHostException::class)
        fun getByAddress(host: String?, addr: ByteArray?): InetAddress {
            var host = host
            if (host != null && !host.isEmpty() && host[0] == '[') {
                if (host[host.length - 1] == ']') {
                    host = host.substring(1, host.length - 1)
                }
            }
            if (addr != null) {
                if (addr.size == Inet4Address.INADDRSZ) {
                    return Inet4Address(host, addr)
                } else if (addr.size == Inet6Address.INADDRSZ) {
                    val newAddr
                            : ByteArray? = IPAddressUtil.convertFromIPv4MappedAddress(addr)
                    if (newAddr != null) {
                        return Inet4Address(host, newAddr)
                    } else {
                        return Inet6Address(host, addr)
                    }
                }
            }
            throw UnknownHostException("addr is of illegal length")
        }

        // end of companion object
    }
}